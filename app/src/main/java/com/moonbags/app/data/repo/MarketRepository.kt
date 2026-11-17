package com.moonbags.app.data.repo

import com.moonbags.app.data.local.GlobalEntity
import com.moonbags.app.data.local.HoldingEntity
import com.moonbags.app.data.local.MoonDatabase
import com.moonbags.app.data.local.QuoteEntity
import com.moonbags.app.data.remote.AccountClients
import com.moonbags.app.data.remote.HttpClients
import com.moonbags.app.data.remote.RemoteBalance
import com.moonbags.app.data.remote.SearchCoinDto
import com.moonbags.app.data.security.ExchangeCredentials
import com.moonbags.app.data.security.SecureCredentialStore
import com.moonbags.app.domain.AssetIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarketRepository(
    private val db: MoonDatabase,
    private val secrets: SecureCredentialStore,
    private val accounts: AccountClients = AccountClients(),
) {
    suspend fun search(query: String): List<SearchCoinDto> = withContext(Dispatchers.IO) {
        HttpClients.gecko.search(query).coins.take(8)
    }

    suspend fun refresh(currency: String, watchIds: List<String>) = withContext(Dispatchers.IO) {
        val fiat = currency.lowercase()
        val ids = (watchIds + AssetIds.bySymbol.values).distinct().joinToString(",")
        val global = HttpClients.gecko.global().data
        db.market().upsertGlobal(
            GlobalEntity(
                currency = fiat,
                marketCap = global.totalMarketCap[fiat] ?: global.totalMarketCap["usd"] ?: 0.0,
                volume = global.totalVolume[fiat] ?: global.totalVolume["usd"] ?: 0.0,
                change24h = global.change24h,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val markets = HttpClients.gecko.markets(fiat, ids)
        val overlays = coinbaseOverlays(markets.map { it.symbol.uppercase() }, fiat)
        val now = System.currentTimeMillis()
        db.market().upsertQuotes(
            markets.map { coin ->
                val symbol = coin.symbol.uppercase()
                val overlay = overlays[symbol]
                QuoteEntity(
                    coinId = coin.id,
                    symbol = symbol,
                    name = coin.name,
                    price = overlay?.price ?: coin.currentPrice ?: 0.0,
                    marketCap = coin.marketCap ?: 0.0,
                    change24h = overlay?.change ?: coin.change24h ?: 0.0,
                    sparkline = coin.sparkline?.price?.joinToString(",") { it.toString() }.orEmpty(),
                    imageUrl = coin.image,
                    currency = fiat,
                    updatedAt = now,
                )
            },
        )
    }

    suspend fun syncExchanges(): String = withContext(Dispatchers.IO) {
        val creds = secrets.load()
        val notes = mutableListOf<String>()
        if (creds.hasCoinbase) {
            val rows = accounts.coinbaseBalances(creds)
            replace("COINBASE", rows)
            notes += "Coinbase ${rows.size}"
        }
        if (creds.hasBinance) {
            val rows = accounts.binanceBalances(creds)
            replace("BINANCE", rows)
            notes += "Binance ${rows.size}"
        }
        if (notes.isEmpty()) "No exchange keys saved" else notes.joinToString()
    }

    suspend fun trackWallet(chain: String, address: String) = withContext(Dispatchers.IO) {
        val trimmed = address.trim()
        val rows = accounts.publicWallet(chain, trimmed)
        if (rows.isEmpty()) error("No supported balance on that address")
        val source = if (chain == "sol") "SOL" else "ETH"
        val keep = db.holdings().all().filter { it.source == source && it.address != trimmed }
        db.holdings().deleteSource(source)
        db.holdings().upsert(keep + rows.toHoldings(trimmed))
    }

    suspend fun addManual(symbol: String, amount: Double) = withContext(Dispatchers.IO) {
        val ticker = symbol.trim().uppercase()
        db.holdings().upsert(
            listOf(
                HoldingEntity(
                    id = "manual:$ticker",
                    source = "MANUAL",
                    symbol = ticker,
                    coinId = AssetIds.idFor(ticker),
                    amount = amount,
                    label = "Manual",
                    address = null,
                ),
            ),
        )
    }

    suspend fun removeHolding(id: String) = withContext(Dispatchers.IO) {
        db.holdings().delete(id)
    }

    private suspend fun replace(source: String, rows: List<RemoteBalance>) {
        db.holdings().deleteSource(source)
        if (rows.isNotEmpty()) db.holdings().upsert(rows.toHoldings(null))
    }

    private fun List<RemoteBalance>.toHoldings(address: String?) = map { row ->
        HoldingEntity(
            id = "${row.source}:${address ?: "exchange"}:${row.symbol}",
            source = row.source,
            symbol = row.symbol,
            coinId = row.coinId,
            amount = row.amount,
            label = row.label,
            address = address,
        )
    }

    private suspend fun coinbaseOverlays(symbols: List<String>, fiat: String): Map<String, Overlay> {
        val code = fiat.uppercase()
        val out = mutableMapOf<String, Overlay>()
        for (symbol in symbols.distinct()) {
            if (symbol !in AssetIds.coinbaseProducts) continue
            try {
                val spot = HttpClients.coinbase.spot("$symbol-$code").data.amount.toDouble()
                val stats = runCatching { HttpClients.exchange.stats("$symbol-USD") }.getOrNull()
                val open = stats?.open?.toDoubleOrNull()
                val last = stats?.last?.toDoubleOrNull() ?: spot
                val change = if (open != null && open > 0) ((last - open) / open) * 100 else null
                out[symbol] = Overlay(spot, change)
            } catch (_: Exception) {
                // CoinGecko price remains the fallback.
            }
        }
        return out
    }

    private data class Overlay(val price: Double, val change: Double?)
}

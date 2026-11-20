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
import com.moonbags.app.data.remote.MoonJson
import com.moonbags.app.domain.AssetIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.json.JSONObject

class MarketRepository(
    private val db: MoonDatabase,
    private val secrets: SecureCredentialStore,
    private val accounts: AccountClients = AccountClients(),
) {
    suspend fun remember(hit: SearchCoinDto, currency: String) = withContext(Dispatchers.IO) {
        if (hit.id.startsWith("cmc-") || hit.id.startsWith("dex")) {
            pinMeme(hit, currency)
            return@withContext
        }
        db.market().upsertQuotes(
            listOf(
                QuoteEntity(
                    coinId = hit.id,
                    symbol = hit.symbol.uppercase(),
                    name = hit.name,
                    price = hit.price,
                    marketCap = hit.marketCap,
                    change24h = hit.change24h,
                    sparkline = "",
                    imageUrl = hit.image.ifBlank { null },
                    currency = currency.lowercase(),
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )
    }

    suspend fun searchTape(query: String): List<SearchCoinDto> = withContext(Dispatchers.IO) {
        val memes = runCatching { searchMemes(query, "all") }.getOrDefault(emptyList())
        val found = runCatching { HttpClients.gecko.search(query).coins.take(8) }.getOrDefault(emptyList())
        val ids = found.map { it.id }.filter { it.isNotBlank() }
        val markets = if (ids.isEmpty()) emptyList() else runCatching { HttpClients.gecko.markets("usd", ids.joinToString(",")) }.getOrDefault(emptyList())
        val priced = found.map { hit ->
            val row = markets.find { it.id == hit.id }
            hit.copy(
                symbol = hit.symbol.uppercase(),
                price = row?.currentPrice ?: hit.price,
                marketCap = row?.marketCap ?: hit.marketCap,
                image = row?.image ?: hit.image,
                change24h = row?.change24h ?: hit.change24h,
            )
        }
        (memes + priced).distinctBy { it.id }.take(12)
    }

    suspend fun searchMemes(query: String, chain: String): List<SearchCoinDto> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        val found = runCatching { searchDex(needle) }.getOrDefault(emptyList())
        val filtered = found.filter { row -> chain == "all" || row.chain.equals(chain, true) }
        if (isContract(needle) || filtered.isNotEmpty()) return@withContext filtered.take(12)
        memeCatalog().filter { row ->
            (chain == "all" || row.chain.equals(chain, true)) &&
                (needle.isEmpty() || row.name.contains(needle, true) || row.symbol.contains(needle, true))
        }.take(12)
    }

    suspend fun pinMeme(hit: SearchCoinDto, currency: String) = withContext(Dispatchers.IO) {
        val spark = memeSpark(hit.id)
        val row = memeCatalog().find { it.id == hit.id } ?: hit
        db.market().upsertQuotes(
            listOf(
                QuoteEntity(
                    coinId = row.id,
                    symbol = row.symbol.uppercase(),
                    name = row.name,
                    price = hit.price,
                    marketCap = row.marketCap,
                    change24h = 0.0,
                    sparkline = spark,
                    imageUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/${row.id.removePrefix("cmc-")}.png",
                    currency = currency.lowercase(),
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )
    }

    suspend fun refresh(currency: String, watchIds: List<String>) = withContext(Dispatchers.IO) {
        val fiat = currency.lowercase()
        val geckoIds = (watchIds.filter { !it.startsWith("cmc-") && !it.startsWith("dex") } + AssetIds.bySymbol.values).distinct()
        val ids = geckoIds.joinToString(",")
        val gecko = runCatching { HttpClients.gecko.global().data }.getOrNull()
        val cmc = cmcGlobal()
        db.market().upsertGlobal(
            GlobalEntity(
                currency = fiat,
                marketCap = cmc?.first ?: gecko?.totalMarketCap?.get(fiat) ?: gecko?.totalMarketCap?.get("usd") ?: 0.0,
                volume = cmc?.second ?: gecko?.totalVolume?.get(fiat) ?: gecko?.totalVolume?.get("usd") ?: 0.0,
                change24h = cmc?.third ?: gecko?.change24h ?: 0.0,
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

    private var memeCache: Pair<Long, List<SearchCoinDto>>? = null

    private fun memeCatalog(): List<SearchCoinDto> {
        val cached = memeCache
        if (cached != null && System.currentTimeMillis() - cached.first < 90_000) return cached.second
        val body = cmc(
            "https://api.coinmarketcap.com/data-api/v3/cryptocurrency/listing?start=1&limit=200&sortBy=market_cap&sortType=desc&convert=USD&cryptoType=tokens&tagSlugs=memes&aux=platform",
        )
        val rows = MoonJson.json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("cryptoCurrencyList")?.jsonArray.orEmpty().mapNotNull { element ->
            val coin = element.jsonObject
            val platform = coin["platform"]?.jsonObject?.get("slug")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (platform != "solana" && platform != "base") return@mapNotNull null
            val id = coin["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val quote = coin["quotes"]?.jsonArray?.firstOrNull()?.jsonObject
            SearchCoinDto(
                id = "cmc-$id",
                symbol = coin["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase().orEmpty(),
                name = coin["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                chain = if (platform == "solana") "Solana" else "Base",
                marketCap = quote?.get("marketCap")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }
        memeCache = System.currentTimeMillis() to rows
        return rows
    }

    private fun isContract(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.matches(Regex("^0x[a-fA-F0-9]{40}$")) || trimmed.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))
    }

    private fun searchDex(query: String): List<SearchCoinDto> {
        if (query.length < 2) return emptyList()
        val body = cmc("https://pro-api.coinmarketcap.com/public-api/v1/dex/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        val tokens = MoonJson.json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("tks")?.jsonArray.orEmpty()
        return tokens.mapNotNull { element ->
            val token = element.jsonObject
            val platform = token["plt"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (platform != "Solana" && platform != "Base") return@mapNotNull null
            val address = token["addr"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cid = token["cid"]?.jsonPrimitive?.contentOrNull
            val id = if (!cid.isNullOrBlank() && cid != "0") "cmc-$cid" else if (platform == "Solana") "dexsol-$address" else "dexbase-$address"
            SearchCoinDto(
                id = id,
                symbol = token["s"]?.jsonPrimitive?.contentOrNull?.uppercase().orEmpty(),
                name = token["n"]?.jsonPrimitive?.contentOrNull ?: "Token",
                chain = platform,
                marketCap = token["mc"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }
    }

    private fun memeSpark(id: String): String {
        val numeric = id.removePrefix("cmc-")
        if (!numeric.all { it.isDigit() }) return ""
        val body = cmc("https://api.coinmarketcap.com/data-api/v3/cryptocurrency/detail/chart?id=$numeric&range=1D")
        val points = MoonJson.json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("points")?.jsonObject ?: return ""
        val prices = points.entries.sortedBy { it.key.toLongOrNull() ?: 0L }.mapNotNull { entry ->
            entry.value.jsonObject["v"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull
        }
        if (prices.size < 2) return ""
        val step = (prices.size / 36).coerceAtLeast(1)
        return prices.filterIndexed { index, _ -> index % step == 0 }.joinToString(",")
    }

    private fun cmcGlobal(): Triple<Double, Double, Double>? = runCatching {
        val body = cmc("https://api.coinmarketcap.com/data-api/v3/global-metrics/quotes/latest")
        val quotes = JSONObject(body).getJSONObject("data").getJSONArray("quotes")
        var usd: JSONObject? = null
        for (index in 0 until quotes.length()) {
            val row = quotes.getJSONObject(index)
            if (row.optString("name") == "USD") usd = row
        }
        val row = usd ?: return null
        val cap = row.optDouble("totalMarketCap")
        if (cap <= 0.0) return null
        Triple(cap, row.optDouble("totalVolume24H"), row.optDouble("totalMarketCapYesterdayPercentageChange"))
    }.getOrNull()

    private fun cmc(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").header("Accept", "application/json").get().build()
        HttpClients.okHttp.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(text.ifBlank { "CoinMarketCap ${response.code}" })
            return text
        }
    }
}

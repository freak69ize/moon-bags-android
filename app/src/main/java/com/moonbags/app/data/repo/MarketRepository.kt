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
import com.moonbags.app.domain.CompactMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
                image = logoFor(hit.id, row?.image ?: hit.large.ifBlank { hit.thumb }.ifBlank { hit.image }),
                change24h = row?.changeInCurrency ?: row?.change24h ?: hit.change24h,
                spark = row?.sparkline?.price?.joinToString(",") { it.toString() }.orEmpty(),
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
                    price = if (hit.price > 0) hit.price else row.price,
                    marketCap = row.marketCap,
                    change24h = if (hit.change24h != 0.0) hit.change24h else row.change24h,
                    sparkline = spark,
                    imageUrl = logoFor(row.id, hit.image.ifBlank { row.image }),
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
        coroutineScope {
            val gecko = async { runCatching { HttpClients.gecko.global().data }.getOrNull() }
            val cmc = async { cmcGlobal() }
            val markets = async { runCatching { HttpClients.gecko.markets(fiat, ids) }.getOrDefault(emptyList()) }
            val spots = async { cmcSpots(fiat) }
            val memes = async { refreshMemes(watchIds, fiat, System.currentTimeMillis()) }
            val geckoData = gecko.await()
            val cmcData = cmc.await()
            db.market().upsertGlobal(
                GlobalEntity(
                    currency = fiat,
                    marketCap = cmcData?.first ?: geckoData?.totalMarketCap?.get(fiat) ?: geckoData?.totalMarketCap?.get("usd") ?: 0.0,
                    volume = cmcData?.second ?: geckoData?.totalVolume?.get(fiat) ?: geckoData?.totalVolume?.get("usd") ?: 0.0,
                    change24h = cmcData?.third ?: geckoData?.change24h ?: 0.0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val now = System.currentTimeMillis()
            val spotMap = spots.await()
            val marketRows = markets.await()
            if (marketRows.isNotEmpty() || spotMap.isNotEmpty()) db.market().upsertQuotes(
                marketRows.map { coin ->
                    val symbol = coin.symbol.uppercase()
                    val spot = spotMap[symbol]
                    QuoteEntity(
                        coinId = coin.id,
                        symbol = symbol,
                        name = coin.name,
                        price = spot?.first?.takeIf { it > 0 } ?: coin.currentPrice ?: 0.0,
                        marketCap = spot?.third?.takeIf { it > 0 } ?: coin.marketCap ?: 0.0,
                        change24h = spot?.second ?: coin.changeInCurrency ?: coin.change24h ?: 0.0,
                        sparkline = coin.sparkline?.price?.joinToString(",") { it.toString() }.orEmpty(),
                        imageUrl = coin.image,
                        currency = fiat,
                        updatedAt = now,
                    )
                },
            )
            memes.await()
        }
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

    private suspend fun coinbaseOverlays(symbols: List<String>, fiat: String): Map<String, Overlay> = coroutineScope {
        val code = fiat.uppercase()
        symbols.distinct().filter { it in AssetIds.coinbaseProducts }.map { symbol ->
            async {
                try {
                    val spot = HttpClients.coinbase.spot("$symbol-$code").data.amount.toDouble()
                    val stats = runCatching { HttpClients.exchange.stats("$symbol-USD") }.getOrNull()
                    val open = stats?.open?.toDoubleOrNull()
                    val last = stats?.last?.toDoubleOrNull() ?: spot
                    val change = if (open != null && open > 0) ((last - open) / open) * 100 else null
                    symbol to Overlay(spot, change)
                } catch (_: Exception) {
                    null
                }
            }
        }.mapNotNull { it.await() }.toMap()
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
                price = quote?.get("price")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                change24h = quote?.get("percentChange24h")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                image = "https://s2.coinmarketcap.com/static/img/coins/64x64/$id.png",
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
        val fromCmc = runCatching { searchCmcDex(query) }.getOrDefault(emptyList())
        if (fromCmc.isNotEmpty()) return fromCmc
        return runCatching { searchDexScreener(query) }.getOrDefault(emptyList())
    }

    private fun searchCmcDex(query: String): List<SearchCoinDto> {
        val body = cmc("https://pro-api.coinmarketcap.com/public-api/v1/dex/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        val tokens = MoonJson.json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("tks")?.jsonArray.orEmpty()
        return tokens.mapNotNull { element ->
            val token = element.jsonObject
            val platform = token["plt"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val address = token["addr"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val price = jsonDouble(token["pu"]) ?: jsonDouble(token["p"]) ?: 0.0
            SearchCoinDto(
                id = dexId(platform, address),
                symbol = token["s"]?.jsonPrimitive?.contentOrNull?.uppercase().orEmpty(),
                name = token["n"]?.jsonPrimitive?.contentOrNull ?: "Token",
                chain = platform,
                marketCap = jsonDouble(token["mc"]) ?: 0.0,
                price = price,
                change24h = dexPercent(jsonDouble(token["pc24h"]) ?: jsonDouble(token["p24h"]) ?: 0.0),
                image = logoFor(dexId(platform, address), token["l"]?.jsonPrimitive?.contentOrNull ?: token["lg"]?.jsonPrimitive?.contentOrNull ?: ""),
            )
        }.distinctBy { it.id }.take(12)
    }

    private fun searchDexScreener(query: String): List<SearchCoinDto> {
        val endpoint = if (isContract(query)) {
            "https://api.dexscreener.com/latest/dex/tokens/${java.net.URLEncoder.encode(query, "UTF-8")}"
        } else {
            "https://api.dexscreener.com/latest/dex/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        }
        val body = cmc(endpoint)
        val pairs = MoonJson.json.parseToJsonElement(body).jsonObject["pairs"]?.jsonArray.orEmpty()
        return pairs.mapNotNull { element ->
            val pair = element.jsonObject
            val chainId = pair["chainId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val base = pair["baseToken"]?.jsonObject ?: return@mapNotNull null
            val address = base["address"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val chain = chainId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            SearchCoinDto(
                id = dexId(chainId, address),
                symbol = base["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase().orEmpty(),
                name = base["name"]?.jsonPrimitive?.contentOrNull ?: "Token",
                chain = chain,
                marketCap = jsonDouble(pair["marketCap"]) ?: jsonDouble(pair["fdv"]) ?: 0.0,
                price = pair["priceUsd"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0,
                change24h = pair["priceChange"]?.jsonObject?.get("h24")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                image = pair["info"]?.jsonObject?.get("imageUrl")?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.distinctBy { it.id }.take(12)
    }

    private fun dexId(platform: String, address: String): String = when (platform.lowercase()) {
        "solana" -> "dexsol-$address"
        "base" -> "dexbase-$address"
        else -> "dex-${platform.lowercase()}-$address"
    }

    private fun dexAddress(id: String): String = when {
        id.startsWith("dexsol-") -> id.removePrefix("dexsol-")
        id.startsWith("dexbase-") -> id.removePrefix("dexbase-")
        id.startsWith("dex-") -> id.substringAfterLast("-")
        else -> id
    }

    private fun jsonDouble(element: kotlinx.serialization.json.JsonElement?): Double? {
        val primitive = element?.jsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
    }

    private fun dexPercent(raw: Double): Double = if (kotlin.math.abs(raw) <= 1.0) raw * 100 else raw

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

    suspend fun liveGlobal(): Triple<Double, Double, Double>? = withContext(Dispatchers.IO) { cmcGlobal() }

    private fun cmcGlobal(): Triple<Double, Double, Double>? = runCatching {
        val body = cmc("https://api.coinmarketcap.com/data-api/v3/global-metrics/quotes/latest")
        val quotes = JSONObject(body).getJSONObject("data").getJSONArray("quotes")
        var usd: JSONObject? = null
        for (index in 0 until quotes.length()) {
            val row = quotes.getJSONObject(index)
            val name = row.optString("name")
            val id = row.optInt("cryptoId")
            if (name.equals("USD", true) || id == 2781) usd = row
        }
        val row = usd ?: if (quotes.length() > 0) quotes.getJSONObject(0) else return null
        val cap = row.optDouble("totalMarketCap")
        if (!cap.isFinite() || cap < 100_000_000_000.0) return null
        Triple(cap, row.optDouble("totalVolume24H"), row.optDouble("totalMarketCapYesterdayPercentageChange"))
    }.getOrNull()

    suspend fun freshCmc(currency: String, coins: List<Pair<String, String>>): Map<String, Triple<Double, Double, Double>> = withContext(Dispatchers.IO) {
        val listed = cmcSpots(currency.lowercase())
        if (coins.isEmpty()) return@withContext listed
        val missing = coins.filter { (symbol, _) -> symbol.uppercase() !in listed }
        if (missing.isEmpty()) return@withContext listed
        val extra = coroutineScope {
            missing.take(12).map { (symbol, id) ->
                async {
                    val spot = cmcByIdentity(id, currency.lowercase()) ?: return@async null
                    symbol.uppercase() to spot
                }
            }.mapNotNull { it.await() }
        }
        listed + extra
    }

    private fun cmcSpots(currency: String): Map<String, Triple<Double, Double, Double>> = runCatching {
        val convert = currency.uppercase()
        val body = cmc("https://api.coinmarketcap.com/data-api/v3/cryptocurrency/listing?start=1&limit=200&sortBy=market_cap&sortType=desc&convert=$convert&cryptoType=all&tagType=all&audited=false")
        val list = JSONObject(body).getJSONObject("data").getJSONArray("cryptoCurrencyList")
        buildMap {
            for (index in 0 until list.length()) {
                val coin = list.getJSONObject(index)
                val symbol = coin.optString("symbol").uppercase()
                val quote = cmcQuote(coin.optJSONArray("quotes"), convert) ?: continue
                if (symbol.isNotBlank() && quote.first > 0) put(symbol, quote)
            }
        }
    }.getOrDefault(emptyMap())

    private fun cmcByIdentity(id: String, currency: String): Triple<Double, Double, Double>? {
        val convert = currency.uppercase()
        val numeric = id.removePrefix("cmc-")
        val url = if (id.startsWith("cmc-") && numeric.all { it.isDigit() }) {
            "https://api.coinmarketcap.com/data-api/v3/cryptocurrency/detail/lite?id=$numeric"
        } else if (id.isNotBlank() && !id.startsWith("dex")) {
            "https://api.coinmarketcap.com/data-api/v3/cryptocurrency/detail?slug=$id"
        } else {
            return null
        }
        return runCatching {
            val data = JSONObject(cmc(url)).optJSONObject("data") ?: return@runCatching null
            val stats = data.optJSONObject("statistics")
            val quote = cmcQuote(data.optJSONArray("quotes"), convert)
            val price = quote?.first ?: stats?.optDouble("price") ?: return@runCatching null
            if (!price.isFinite() || price <= 0) return@runCatching null
            val change = quote?.second ?: stats?.optDouble("priceChangePercentage24h") ?: 0.0
            val cap = quote?.third ?: stats?.optDouble("marketCap") ?: 0.0
            Triple(price, if (change.isFinite()) change else 0.0, if (cap.isFinite()) cap else 0.0)
        }.getOrNull()
    }

    private fun cmcQuote(quotes: org.json.JSONArray?, convert: String): Triple<Double, Double, Double>? {
        if (quotes == null || quotes.length() == 0) return null
        var chosen: org.json.JSONObject? = null
        for (index in 0 until quotes.length()) {
            val row = quotes.optJSONObject(index) ?: continue
            val name = row.optString("name")
            if (name.equals(convert, true) || name.equals("USD", true) && chosen == null) chosen = row
            if (name.equals(convert, true)) {
                chosen = row
                break
            }
        }
        val row = chosen ?: quotes.optJSONObject(0) ?: return null
        val price = row.optDouble("price")
        if (!price.isFinite() || price <= 0) return null
        val change = row.optDouble("percentChange24h")
        val cap = row.optDouble("marketCap")
        return Triple(price, if (change.isFinite()) change else 0.0, if (cap.isFinite()) cap else 0.0)
    }

    private fun logoFor(id: String, image: String): String {
        if (image.startsWith("http")) return image
        val numeric = id.removePrefix("cmc-")
        if (id.startsWith("cmc-") && numeric.all { it.isDigit() }) {
            return "https://s2.coinmarketcap.com/static/img/coins/64x64/$numeric.png"
        }
        return image
    }

    private suspend fun refreshMemes(watchIds: List<String>, fiat: String, now: Long) {
        val rows = db.market().quotes().filter { it.coinId in watchIds && (it.coinId.startsWith("cmc-") || it.coinId.startsWith("dex")) }
        if (rows.isEmpty()) return
        val next = coroutineScope {
            rows.map { row ->
                async {
                    val fresh = memeLive(row.coinId)
                    row.copy(
                        price = fresh?.price?.takeIf { it > 0 } ?: row.price,
                        change24h = fresh?.change24h ?: row.change24h,
                        imageUrl = logoFor(row.coinId, fresh?.image?.ifBlank { row.imageUrl.orEmpty() } ?: row.imageUrl.orEmpty()).ifBlank { row.imageUrl },
                        currency = fiat,
                        updatedAt = now,
                    )
                }
            }.map { it.await() }
        }
        db.market().upsertQuotes(next)
    }

    private fun memeLive(id: String): SearchCoinDto? = runCatching {
        if (id.startsWith("cmc-")) {
            val numeric = id.removePrefix("cmc-")
            val body = cmc("https://api.coinmarketcap.com/data-api/v3/cryptocurrency/detail/lite?id=$numeric")
            val stats = MoonJson.json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("statistics")?.jsonObject
            SearchCoinDto(
                id = id,
                price = stats?.get("price")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                change24h = stats?.get("priceChangePercentage24h")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                image = logoFor(id, ""),
            )
        } else {
            val address = dexAddress(id)
            val body = cmc("https://api.dexscreener.com/latest/dex/tokens/$address")
            val pair = MoonJson.json.parseToJsonElement(body).jsonObject["pairs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
            SearchCoinDto(
                id = id,
                price = pair["priceUsd"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0,
                change24h = pair["priceChange"]?.jsonObject?.get("h24")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                image = pair["info"]?.jsonObject?.get("imageUrl")?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
    }.getOrNull()

    suspend fun coinStats(hit: SearchCoinDto): CoinStats = withContext(Dispatchers.IO) {
        val prices = hit.spark.split(",").mapNotNull { it.toDoubleOrNull() }
        val base = CoinStats(
            price = hit.price,
            marketCap = hit.marketCap,
            network = hit.chain,
            prices = prices,
            caps = if (hit.price > 0 && hit.marketCap > 0) prices.map { hit.marketCap * (it / hit.price) } else emptyList(),
        )
        val numeric = hit.id.removePrefix("cmc-")
        val loaded = when {
            hit.id.startsWith("cmc-") && numeric.all { it.isDigit() } -> cmcStats(numeric, base)
            hit.id.startsWith("dex") -> dexStats(hit, base)
            else -> geckoStats(hit.id, base)
        }
        loaded ?: base
    }

    private fun cmcStats(id: String, base: CoinStats): CoinStats? = runCatching {
        val data = JSONObject(cmc("https://api.coinmarketcap.com/data-api/v3/cryptocurrency/detail?id=$id")).optJSONObject("data") ?: return@runCatching null
        val stats = data.optJSONObject("statistics")
        val platforms = data.optJSONArray("platforms")
        val platform = if (platforms != null && platforms.length() > 0) platforms.optJSONObject(0) else null
        val chart = chartSeries(id)
        val maxSupply = stats?.optDouble("maxSupply") ?: Double.NaN
        val circulating = stats?.optDouble("circulatingSupply") ?: base.circulating
        base.copy(
            price = stats?.optDouble("price")?.takeIf { it > 0 } ?: base.price,
            marketCap = stats?.optDouble("marketCap")?.takeIf { it > 0 } ?: base.marketCap,
            volume = stats?.optDouble("volume24h")?.takeIf { it > 0 } ?: base.volume,
            circulating = circulating.takeIf { it > 0 } ?: 0.0,
            ath = listOf("highAllTime", "ath", "allTimeHigh").firstNotNullOfOrNull { key -> stats?.optDouble(key)?.takeIf { it > 0 } } ?: base.ath,
            rank = data.optInt("rank", stats?.optInt("rank") ?: base.rank),
            mintable = when {
                maxSupply.isNaN() || maxSupply <= 0 -> "Yes"
                circulating > 0 && circulating >= maxSupply * 0.995 -> "No"
                else -> "Yes"
            },
            holders = stats?.optDouble("holders")?.takeIf { it > 0 }?.let { CompactMoney.format(it, "usd").removePrefix("$") } ?: "—",
            holdTime = "—",
            activity = volumeActivity(stats?.optDouble("volume24h") ?: 0.0, stats?.optDouble("volumeYesterday") ?: 0.0),
            network = platform?.optString("contractPlatform").orEmpty().ifBlank { base.network },
            contract = platform?.optString("contractAddress").orEmpty().ifBlank { base.contract },
            prices = chart.first.ifEmpty { base.prices },
            caps = chart.second.ifEmpty { base.caps },
        )
    }.getOrNull()

    private fun geckoStats(id: String, base: CoinStats): CoinStats? = runCatching {
        val body = cmc("https://api.coingecko.com/api/v3/coins/$id?localization=false&tickers=false&market_data=true&community_data=false&developer_data=false&sparkline=true")
        val root = JSONObject(body)
        val market = root.optJSONObject("market_data") ?: return@runCatching null
        val price = market.optJSONObject("current_price")?.optDouble("usd") ?: base.price
        val cap = market.optJSONObject("market_cap")?.optDouble("usd") ?: base.marketCap
        val spark = market.optJSONObject("sparkline_7d")?.optJSONArray("price")
        val prices = spark?.let { array -> List(array.length()) { index -> array.optDouble(index) }.filter { it > 0 } } ?: base.prices
        val maxSupply = market.optDouble("max_supply")
        val circulating = market.optDouble("circulating_supply")
        base.copy(
            price = price.takeIf { it > 0 } ?: base.price,
            marketCap = cap.takeIf { it > 0 } ?: base.marketCap,
            volume = market.optJSONObject("total_volume")?.optDouble("usd")?.takeIf { it > 0 } ?: base.volume,
            circulating = circulating.takeIf { it > 0 } ?: 0.0,
            ath = market.optJSONObject("ath")?.optDouble("usd")?.takeIf { it > 0 } ?: base.ath,
            rank = root.optInt("market_cap_rank", base.rank),
            mintable = when {
                maxSupply.isNaN() || maxSupply <= 0 -> "Yes"
                circulating > 0 && circulating >= maxSupply * 0.995 -> "No"
                else -> "Yes"
            },
            prices = prices,
            caps = if (price > 0 && cap > 0) prices.map { cap * (it / price) } else base.caps,
        )
    }.getOrNull()

    private fun dexStats(hit: SearchCoinDto, base: CoinStats): CoinStats? = runCatching {
        val address = hit.id.substringAfterLast("-")
        val body = cmc("https://api.dexscreener.com/latest/dex/tokens/$address")
        val pair = MoonJson.json.parseToJsonElement(body).jsonObject["pairs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
        base.copy(
            price = pair["priceUsd"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: base.price,
            marketCap = pair["marketCap"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: base.marketCap,
            volume = pair["volume"]?.jsonObject?.get("h24")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: base.volume,
            network = pair["chainId"]?.jsonPrimitive?.contentOrNull ?: hit.chain,
            contract = pair["baseToken"]?.jsonObject?.get("address")?.jsonPrimitive?.contentOrNull ?: address,
            mintable = "—",
        )
    }.getOrNull()

    private fun chartSeries(id: String): Pair<List<Double>, List<Double>> = runCatching {
        val body = cmc("https://api.coinmarketcap.com/data-api/v3/cryptocurrency/detail/chart?id=$id&range=1D")
        val points = MoonJson.json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("points")?.jsonObject ?: return@runCatching emptyList<Double>() to emptyList()
        val rows = points.entries.sortedBy { it.key.toLongOrNull() ?: 0L }.mapNotNull { entry ->
            entry.value.jsonObject["v"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull?.toDoubleOrNull() ?: 0.0 }
        }
        val prices = rows.mapNotNull { it.getOrNull(0)?.takeIf { price -> price > 0 } }
        val caps = rows.mapNotNull { it.getOrNull(2)?.takeIf { cap -> cap > 0 } }
        prices to caps
    }.getOrDefault(emptyList<Double>() to emptyList())

    private fun volumeActivity(today: Double, yesterday: Double): String {
        if (today <= 0.0) return "—"
        if (yesterday <= 0.0) return "Active"
        val change = (today - yesterday) / yesterday * 100.0
        val tone = if (change >= 0) "Up" else "Down"
        return tone + " " + String.format(java.util.Locale.US, "%.1f%%", kotlin.math.abs(change))
    }

    private fun cmc(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept", "application/json")
            .header("Referer", "https://coinmarketcap.com/")
            .header("Origin", "https://coinmarketcap.com")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()
            .build()
        HttpClients.okHttp.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(text.ifBlank { "CoinMarketCap ${response.code}" })
            return text
        }
    }
}

data class CoinStats(
    val price: Double = 0.0,
    val marketCap: Double = 0.0,
    val volume: Double = 0.0,
    val circulating: Double = 0.0,
    val ath: Double = 0.0,
    val rank: Int = 0,
    val mintable: String = "—",
    val holders: String = "—",
    val holdTime: String = "—",
    val activity: String = "—",
    val network: String = "",
    val contract: String = "",
    val prices: List<Double> = emptyList(),
    val caps: List<Double> = emptyList(),
)

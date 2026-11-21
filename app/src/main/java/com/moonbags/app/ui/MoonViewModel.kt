package com.moonbags.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moonbags.app.MoonBagsApp
import com.moonbags.app.data.local.GlobalEntity
import com.moonbags.app.data.local.HoldingEntity
import com.moonbags.app.data.local.QuoteEntity
import com.moonbags.app.data.local.WidgetConfigEntity
import com.moonbags.app.data.remote.SearchCoinDto
import com.moonbags.app.data.settings.UserSettings
import com.moonbags.app.data.remote.LiveTicker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.moonbags.app.widget.updateAllWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DeskState(
    val refreshing: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val settings: UserSettings = UserSettings(),
    val global: GlobalEntity? = null,
    val quotes: List<QuoteEntity> = emptyList(),
    val holdings: List<HoldingEntity> = emptyList(),
    val widgets: List<WidgetConfigEntity> = emptyList(),
)

private data class LiveTick(val price: Double, val open: Double, val change: Double, val spark: List<Double>)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MoonViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as MoonBagsApp).graph
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val notice = MutableStateFlow<String?>(null)
    private val ticks = MutableStateFlow<Map<String, LiveTick>>(emptyMap())
    private val ticker = LiveTicker()
    private var flushJob: Job? = null

    val state = combine(graph.settings.flow, refreshing, error, notice) { settings, busy, err, note ->
        Quad(settings, busy, err, note)
    }.flatMapLatest { quad ->
        combine(
            graph.db.market().observeGlobal(quad.settings.currency),
            combine(graph.db.market().observeQuotes(), ticks) { quotes, live ->
                quotes.map { quote ->
                    val tick = live[quote.symbol.uppercase()] ?: return@map quote
                    val moved = tick.open > 0 && kotlin.math.abs(tick.price - tick.open) > tick.open * 0.0000005
                    quote.copy(
                        price = if (tick.price > 0) tick.price else quote.price,
                        change24h = if (moved) tick.change else quote.change24h,
                        sparkline = if (tick.spark.size > 1) tick.spark.joinToString(",") else quote.sparkline,
                    )
                }
            },
            graph.db.holdings().observe(),
            graph.db.widgets().observe(),
        ) { global, quotes, holdings, widgets ->
            DeskState(
                refreshing = quad.refreshing,
                error = quad.error,
                notice = quad.notice,
                settings = quad.settings,
                global = global,
                quotes = quotes.filter { it.currency == quad.settings.currency },
                holdings = holdings,
                widgets = widgets,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeskState())

    init {
        viewModelScope.launch {
            graph.db.market().observeQuotes().collect { rows ->
                ticker.symbols = rows.map { it.symbol.uppercase() }.toSet()
            }
        }
    }

    fun onForeground() {
        startTicks()
        refresh()
    }

    fun onBackground() {
        viewModelScope.launch {
            flushTicks()
            updateAllWidgets(getApplication())
            ticker.stop()
            flushJob?.cancel()
        }
    }

    private fun startTicks() {
        ticker.start { symbol, price, open ->
            val current = ticks.value[symbol]
            val spark = ((current?.spark ?: emptyList()) + price).takeLast(48)
            val change = if (open > 0) ((price - open) / open) * 100 else current?.change ?: 0.0
            ticks.value = ticks.value + (symbol to LiveTick(price, open, change, spark))
        }
        if (flushJob?.isActive == true) return
        flushJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                flushTicks()
            }
        }
    }

    private suspend fun flushTicks() {
        val live = ticks.value
        if (live.isEmpty()) return
        val rows = graph.db.market().observeQuotes().first()
        val next = rows.mapNotNull { quote ->
            val tick = live[quote.symbol.uppercase()] ?: return@mapNotNull null
            quote.copy(
                price = tick.price,
                change24h = tick.change,
                sparkline = if (tick.spark.size > 1) tick.spark.joinToString(",") else quote.sparkline,
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (next.isNotEmpty()) graph.db.market().upsertQuotes(next)
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                val fresh = graph.settings.flow.first()
                graph.market.refresh(fresh.currency, fresh.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                updateAllWidgets(getApplication())
            } catch (exception: Exception) {
                error.value = exception.message ?: "Refresh failed"
            } finally {
                refreshing.value = false
            }
        }
    }

    fun patch(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            graph.settings.update(transform)
            val interval = graph.settings.flow.first().intervalMin
            (getApplication<MoonBagsApp>()).scheduleSync(interval.toLong())
            updateAllWidgets(getApplication())
        }
    }

    fun addManual(symbol: String, amount: Double) = launchIo("Added $symbol") {
        graph.market.addManual(symbol, amount)
        refreshQuiet()
    }

    fun removeHolding(id: String) = launchIo("Removed") { graph.market.removeHolding(id) }

    fun trackWallet(chain: String, address: String) = launchIo("Wallet read") {
        graph.market.trackWallet(chain, address)
        refreshQuiet()
    }

    fun syncExchanges() = launchIo("Exchanges synced") {
        notice.value = graph.market.syncExchanges()
        refreshQuiet()
    }

    fun saveCoinbase(name: String, pem: String) {
        graph.secrets.saveCoinbase(name, pem)
        notice.value = "Coinbase key saved on this device"
    }

    fun saveBinance(key: String, secret: String) {
        graph.secrets.saveBinance(key, secret)
        notice.value = "Binance key saved on this device"
    }

    fun clearSecrets() {
        graph.secrets.clear()
        notice.value = "Exchange keys cleared"
    }

    suspend fun search(query: String): List<SearchCoinDto> = graph.market.searchTape(query)

    suspend fun searchMemes(query: String, chain: String): List<SearchCoinDto> = graph.market.searchMemes(query, chain)

    fun addWatched(hit: SearchCoinDto) = launchIo("Added ${hit.symbol}") {
        val currency = graph.settings.flow.first().currency
        graph.market.remember(hit, currency)
        graph.settings.update { settings ->
            val ids = settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (hit.id !in ids) ids += hit.id
            settings.copy(watchIds = ids.joinToString(","))
        }
        refreshQuiet()
    }

    fun pinMeme(config: WidgetConfigEntity, hit: SearchCoinDto) = launchIo("Added ${hit.symbol}") {
        val currency = graph.settings.flow.first().currency
        graph.market.pinMeme(hit, currency)
        updateWidgetConfig(config.copy(coinId = hit.id))
    }

    suspend fun splashMillis(): Long {
        val seconds = graph.settings.flow.first().splashSeconds
        return seconds.coerceIn(1, 5) * 1000L
    }

    fun importMedia(uri: android.net.Uri, slot: String, apply: (UserSettings, String) -> UserSettings) {
        viewModelScope.launch {
            val stored = graph.media.import(uri, slot)
            graph.settings.update { apply(it, stored.path) }
        }
    }

    fun importWidgetBackground(config: com.moonbags.app.data.local.WidgetConfigEntity, uri: android.net.Uri) {
        viewModelScope.launch {
            val stored = graph.media.import(uri, "widget-${config.appWidgetId}")
            updateWidgetConfig(config.copy(backgroundPath = stored.path))
        }
    }

    fun updateWidgetConfig(config: WidgetConfigEntity) {
        viewModelScope.launch {
            val all = graph.db.widgets().observe().first()
            val styled = if (all.isEmpty()) listOf(config) else all.map { other ->
                other.copy(
                    backgroundPath = config.backgroundPath,
                    scrim = config.scrim,
                    alpha = config.alpha,
                    acrylic = config.acrylic,
                    acrylicHue = config.acrylicHue,
                    acrylicSat = config.acrylicSat,
                    acrylicLight = config.acrylicLight,
                    coinId = if (other.appWidgetId == config.appWidgetId) config.coinId else other.coinId,
                    showPrice = if (other.appWidgetId == config.appWidgetId) config.showPrice else other.showPrice,
                    hidden = if (other.appWidgetId == config.appWidgetId) config.hidden else other.hidden,
                    kind = if (other.appWidgetId == config.appWidgetId) config.kind else other.kind,
                )
            }
            styled.forEach { graph.db.widgets().upsert(it) }
            com.moonbags.app.widget.updateAllWidgets(getApplication())
        }
    }

    private fun launchIo(ok: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                notice.value = ok
                error.value = null
            } catch (exception: Exception) {
                error.value = exception.message ?: "Something failed"
            }
        }
    }

    private suspend fun refreshQuiet() {
        val fresh = graph.settings.flow.first()
        graph.market.refresh(fresh.currency, fresh.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        updateAllWidgets(getApplication())
    }

    private data class Quad(val settings: UserSettings, val refreshing: Boolean, val error: String?, val notice: String?)
}

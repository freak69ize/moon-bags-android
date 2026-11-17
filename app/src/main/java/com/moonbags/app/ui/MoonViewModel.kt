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
import com.moonbags.app.widget.updateAllWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MoonViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as MoonBagsApp).graph
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val notice = MutableStateFlow<String?>(null)

    val state = combine(graph.settings.flow, refreshing, error, notice) { settings, busy, err, note ->
        Quad(settings, busy, err, note)
    }.flatMapLatest { quad ->
        combine(
            graph.db.market().observeGlobal(quad.settings.currency),
            graph.db.market().observeQuotes(),
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
        refresh()
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

    suspend fun search(query: String): List<SearchCoinDto> = graph.market.search(query)

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
            graph.db.widgets().upsert(config)
            com.moonbags.app.widget.updateWidget(getApplication(), config.kind, config.appWidgetId)
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

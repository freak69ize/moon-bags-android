package com.moonbags.app.ui.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moonbags.app.MoonBagsApp
import com.moonbags.app.data.local.QuoteEntity
import com.moonbags.app.data.local.WidgetConfigEntity
import com.moonbags.app.domain.CompactMoney
import com.moonbags.app.ui.theme.MoonTheme
import com.moonbags.app.widget.WidgetKinds
import com.moonbags.app.widget.updateWidget
import kotlinx.coroutines.launch

class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
        val kind = WidgetKinds.fromProvider(provider)
        setContent {
            MoonTheme(theme = "ink", materialYou = false) {
                ConfigureForm(appWidgetId, kind) { saved ->
                    if (saved) {
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                    }
                    finish()
                }
            }
        }
    }
}

@Composable
private fun ConfigureForm(appWidgetId: Int, kind: String, onDone: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val graph = (context.applicationContext as MoonBagsApp).graph
    val scope = rememberCoroutineScope()
    val quotes by produceState(emptyList<QuoteEntity>()) { value = graph.db.market().quotes() }
    var coinId by remember { mutableStateOf(quotes.firstOrNull()?.coinId ?: "bitcoin") }
    var showPrice by remember { mutableStateOf(kind == WidgetKinds.PILL || kind == WidgetKinds.SPARK) }
    var scrim by remember { mutableFloatStateOf(0.55f) }
    var alpha by remember { mutableFloatStateOf(0.7f) }
    var path by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) path = graph.media.import(uri, "widget-$appWidgetId").path
    }
    val coin = quotes.find { it.coinId == coinId }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Place widget", style = MaterialTheme.typography.headlineMedium)
        Text(kindLabel(kind), color = MaterialTheme.colorScheme.primary)
        Text(
            if (kind == WidgetKinds.MCAP) "Total market cap" else CompactMoney.price(coin?.price ?: 0.0, "usd"),
            style = MaterialTheme.typography.displaySmall,
        )
        if (kind != WidgetKinds.MCAP && kind != WidgetKinds.BOOK && kind != WidgetKinds.WATCH) {
            quotes.forEach { quote ->
                OutlinedButton(onClick = { coinId = quote.coinId }, modifier = Modifier.fillMaxWidth()) {
                    Text(quote.symbol + " · " + quote.name)
                }
            }
        }
        if (kind == WidgetKinds.TILE) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Show spot price")
                Switch(checked = showPrice, onCheckedChange = { showPrice = it })
            }
        }
        Text("Transparency ${((1f - alpha) * 100).toInt()}%")
        Slider(value = 1f - alpha, onValueChange = { alpha = (1f - it).coerceIn(0.15f, 1f) }, valueRange = 0f..0.85f)
        Text("Scrim ${(scrim * 100).toInt()}%")
        Slider(value = scrim, onValueChange = { scrim = it }, valueRange = 0f..0.9f)
        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (path == null) "Pick PNG, JPG, or GIF" else "Background ready")
        }
        Button(
            onClick = {
                scope.launch {
                    graph.db.widgets().upsert(
                        WidgetConfigEntity(
                            appWidgetId = appWidgetId,
                            kind = kind,
                            coinId = coinId,
                            showPrice = showPrice,
                            backgroundPath = path,
                            scrim = scrim,
                            hidden = false,
                            alpha = alpha,
                        ),
                    )
                    updateWidget(context, kind, appWidgetId)
                    onDone(true)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add to home screen") }
        OutlinedButton(onClick = { onDone(false) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

private fun kindLabel(kind: String) = when (kind) {
    WidgetKinds.TILE -> "1×1 coin cap"
    WidgetKinds.PILL -> "Price pill"
    WidgetKinds.SPARK -> "Sparkline card"
    WidgetKinds.WATCH -> "Watchlist"
    WidgetKinds.BOOK -> "Portfolio net worth"
    else -> "Resizable total market cap"
}

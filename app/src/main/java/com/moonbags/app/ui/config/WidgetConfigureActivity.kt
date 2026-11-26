package com.moonbags.app.ui.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.moonbags.app.data.media.samsungGalleryIntent
import com.moonbags.app.domain.CompactMoney
import com.moonbags.app.ui.theme.MoonTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.moonbags.app.widget.WidgetKinds
import com.moonbags.app.widget.updateAllWidgets
import com.moonbags.app.widget.updateWidget
import kotlinx.coroutines.flow.first
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
            MoonTheme(theme = "ink") {
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
    val allQuotes by produceState(emptyList<QuoteEntity>()) { value = graph.db.market().quotes() }
    val settings by produceState(com.moonbags.app.data.settings.UserSettings()) { value = graph.settings.flow.first() }
    val watchIds = settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val quotes = allQuotes.filter { it.coinId in watchIds && it.currency.equals(settings.currency, true) }

    var selectedId by remember { mutableStateOf("") }

    LaunchedEffect(quotes) {
        if (selectedId.isBlank() && quotes.isNotEmpty()) {
            selectedId = quotes.first().coinId
        }
    }
    var showPrice by remember { mutableStateOf(kind == WidgetKinds.PILL || kind == WidgetKinds.SPARK || kind == WidgetKinds.SPARK_BAR) }
    var scrim by remember { mutableFloatStateOf(0.55f) }
    var alpha by remember { mutableFloatStateOf(0.7f) }
    var path by remember { mutableStateOf<String?>(null) }
    val savedRadius by produceState(22) { value = graph.settings.flow.first().radiusDp }
    var radius by remember(savedRadius) { mutableFloatStateOf(savedRadius.toFloat()) }
    val subHex by produceState("#8E8E93") { value = graph.settings.flow.first().subtextColor }
    val sub = runCatching { Color(android.graphics.Color.parseColor(subHex)) }.getOrDefault(Color(0xFF8E8E93))
    val sliderColors = SliderDefaults.colors(thumbColor = sub, activeTrackColor = sub, inactiveTrackColor = sub.copy(alpha = 0.35f))
    val picker = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (context.contentResolver.getType(uri)?.contains("gif") == true) return@rememberLauncherForActivityResult
        path = graph.media.import(uri, "widget-$appWidgetId").path
    }
    val selected = quotes.find { it.coinId == selectedId }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Place widget", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(kindLabel(kind), color = Color.White)
        Text(
            if (kind == WidgetKinds.MCAP) "Total market cap" else CompactMoney.price(selected?.price ?: 0.0, "usd"),
            color = Color.White,
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            "Selected: ${selected?.symbol ?: "none"} · ${selected?.name ?: ""}",
            color = Color(0xFF3DDC84),
            style = MaterialTheme.typography.titleMedium,
        )

        if (kind != WidgetKinds.MCAP && kind != WidgetKinds.BOOK && kind != WidgetKinds.WATCH) {
            quotes.forEach { quote ->
                val isSelected = quote.coinId == selectedId
                OutlinedButton(
                    onClick = { selectedId = quote.coinId },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSelected) Color(0xFF3DDC84) else Color.White,
                    ),
                ) {
                    Text(
                        (if (isSelected) "✓ " else "") + quote.symbol + " · " + quote.name,
                        color = if (isSelected) Color(0xFF3DDC84) else Color.White,
                    )
                }
            }
        }
        if (kind == WidgetKinds.TILE) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Show spot price", color = Color.White)
                Switch(checked = showPrice, onCheckedChange = { showPrice = it })
            }
        }
        Text("Transparency", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text("How see-through the widget is, including a background picture. ${((1f - alpha) * 100).toInt()}%", color = Color.White)
        Slider(value = 1f - alpha, onValueChange = { alpha = (1f - it).coerceIn(0f, 1f) }, valueRange = 0f..1f, colors = sliderColors)
        Text("Scrim ${(scrim * 100).toInt()}%", color = Color.White)
        Slider(value = scrim, onValueChange = { scrim = it }, valueRange = 0f..0.9f, colors = sliderColors)
        Text("Corner roundness ${radius.toInt()}", color = Color.White)
        Slider(
            value = radius,
            onValueChange = { radius = it },
            valueRange = 0f..36f,
            colors = sliderColors,
            onValueChangeFinished = {
                scope.launch {
                    graph.settings.update { it.copy(radiusDp = radius.toInt().coerceIn(0, 36)) }
                    updateAllWidgets(context)
                }
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(radius.toInt().dp))
                .border(1.dp, Color.White, RoundedCornerShape(radius.toInt().dp)),
        )
        OutlinedButton(onClick = { picker.launch(samsungGalleryIntent(context)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Text(if (path == null) "Pick PNG or JPG" else "Background ready", color = Color.White)
        }
        Button(
            onClick = {
                val chosen = selectedId.ifBlank { quotes.firstOrNull()?.coinId ?: "bitcoin" }
                scope.launch {
                    context.getSharedPreferences("widget_coins", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putString("coin_$appWidgetId", chosen)
                        .apply()
                    graph.settings.update { it.copy(radiusDp = radius.toInt().coerceIn(0, 36)) }
                    graph.db.widgets().upsert(
                        WidgetConfigEntity(
                            appWidgetId = appWidgetId,
                            kind = kind,
                            coinId = chosen,
                            showPrice = showPrice,
                            backgroundPath = path,
                            scrim = scrim,
                            hidden = false,
                            alpha = alpha,
                        ),
                    )
                    kotlinx.coroutines.delay(300)
                    updateWidget(context, kind, appWidgetId)
                    onDone(true)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = sub, contentColor = Color.White),
        ) { Text("Add to home screen", color = Color.White) }
        OutlinedButton(onClick = { onDone(false) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Cancel", color = Color.White) }
    }
}

private fun kindLabel(kind: String) = when (kind) {
    WidgetKinds.TILE -> "1×1 coin cap"
    WidgetKinds.PILL -> "Price pill"
    WidgetKinds.SPARK -> "Sparkline card"
    WidgetKinds.SPARK_BAR -> "Sparkline 2×1"
    WidgetKinds.WATCH -> "Watchlist"
    WidgetKinds.BOOK -> "Portfolio net worth"
    else -> "Resizable total market cap"
}

package com.moonbags.app.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moonbags.app.data.local.QuoteEntity
import com.moonbags.app.data.remote.SearchCoinDto
import com.moonbags.app.domain.CompactMoney
import com.moonbags.app.ui.theme.Brass
import com.moonbags.app.ui.theme.Down
import com.moonbags.app.ui.theme.Ink
import com.moonbags.app.ui.theme.MoonTheme
import com.moonbags.app.ui.theme.Up
import com.moonbags.app.widget.WidgetKinds
import com.moonbags.app.widget.render
import kotlinx.coroutines.launch
import java.io.File

private enum class Tab(val label: String) { Market("Market"), Studio("Studio"), Bags("Bags"), Settings("Settings") }

@Composable
fun MoonRoot(vm: MoonViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var splash by remember { mutableStateOf(true) }
    MoonTheme(state.settings.theme, state.settings.materialYou) {
        if (splash) {
            LaunchSplash(state.settings.splashPath) {
                splash = false
            }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(vm.splashMillis())
                splash = false
            }
            return@MoonTheme
        }
        var tab by remember { mutableStateOf(Tab.Market) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Moon Bags", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif)
                        Text(state.settings.currency.uppercase() + " desk", color = Brass, style = MaterialTheme.typography.labelMedium)
                    }
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = Brass,
                        modifier = Modifier.clickable { vm.refresh() }.padding(8.dp),
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Icon(
                                    when (item) {
                                        Tab.Market -> Icons.Outlined.ShowChart
                                        Tab.Studio -> Icons.Outlined.GridView
                                        Tab.Bags -> Icons.Outlined.AccountBalanceWallet
                                        Tab.Settings -> Icons.Outlined.Settings
                                    },
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.notice?.let { Text(it, color = Brass) }
                when (tab) {
                    Tab.Market -> MarketPage(state, vm)
                    Tab.Studio -> StudioPage(state, vm)
                    Tab.Bags -> BagsPage(state, vm)
                    Tab.Settings -> SettingsPage(state, vm)
                }
            }
        }
    }
}

@Composable
private fun LaunchSplash(path: String?, onSkip: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink).clickable(onClick = onSkip)) {
        if (!path.isNullOrBlank()) {
            AsyncImage(model = File(path), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Moon Bags", color = com.moonbags.app.ui.theme.Paper, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.displayMedium)
            Text("NIGHT DESK", color = Brass)
        }
    }
}

@Composable
private fun MarketPage(state: DeskState, vm: MoonViewModel) {
    val currency = state.settings.currency
    Text(CompactMoney.format(state.global?.marketCap ?: 0.0, currency), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Serif)
    Text(
        CompactMoney.percent(state.global?.change24h ?: 0.0) + " 24h · vol " + CompactMoney.format(state.global?.volume ?: 0.0, currency),
        color = if ((state.global?.change24h ?: 0.0) >= 0) Up else Down,
    )
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchCoinDto>>(emptyList()) }
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = query,
        onValueChange = {
            query = it
            scope.launch { hits = if (it.length < 2) emptyList() else runCatching { vm.search(it) }.getOrDefault(emptyList()) }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Add a coin") },
        singleLine = true,
    )
    hits.forEach { hit ->
        Text(
            "${hit.symbol.uppercase()}  ${hit.name}",
            modifier = Modifier.fillMaxWidth().clickable {
                val ids = state.settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                if (hit.id !in ids) ids += hit.id
                vm.patch { settings -> settings.copy(watchIds = ids.joinToString(",")) }
                vm.refresh()
                query = ""
                hits = emptyList()
            }.padding(vertical = 8.dp),
        )
    }
    val watch = state.settings.watchIds.split(",").map { it.trim() }.toSet()
    state.quotes.filter { it.coinId in watch }.forEach { quote ->
        QuoteRow(quote, currency) {
            val ids = watch.filterNot { it == quote.coinId }
            vm.patch { it.copy(watchIds = ids.joinToString(",")) }
        }
    }
}

@Composable
private fun QuoteRow(quote: QuoteEntity, currency: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(quote.symbol, style = MaterialTheme.typography.titleMedium)
            Text(quote.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(CompactMoney.format(quote.price, currency), fontFamily = FontFamily.Serif)
            Text(CompactMoney.percent(quote.change24h), color = if (quote.change24h >= 0) Up else Down)
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun StudioPage(state: DeskState, vm: MoonViewModel) {
    Text("Drop a widget on the home screen. Tune it here. GIFs loop in this preview and become a still frame on the launcher.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    var pendingId by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val config = state.widgets.find { it.appWidgetId == pendingId }
        if (uri != null && config != null) vm.importWidgetBackground(config, uri)
    }
    if (state.widgets.isEmpty()) {
        Text("No widgets placed yet. Long-press the home screen, choose Widgets, then Moon Bags.")
        return
    }
    state.widgets.forEach { config ->
        val snap = render(config, state.settings.currency, state.settings.hideBalances, state.global, state.quotes, state.holdings, null)
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(state.settings.radiusDp.dp)).padding(16.dp)) {
            if (!config.backgroundPath.isNullOrBlank()) {
                AsyncImage(model = File(config.backgroundPath), contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = config.scrim)))
            }
            Column {
                Text(snap.title, color = Brass)
                Text(snap.primary, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineMedium)
                Text(snap.secondary, color = if (snap.up) Up else Down)
                snap.lines.forEach { Text("${it.first}  ${it.second}") }
            }
        }
        if (config.kind == WidgetKinds.TILE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spot price instead of cap")
                Switch(checked = config.showPrice, onCheckedChange = { vm.updateWidgetConfig(config.copy(showPrice = it)) })
            }
        }
        Text("Scrim ${(config.scrim * 100).toInt()}%")
        Slider(value = config.scrim, onValueChange = { vm.updateWidgetConfig(config.copy(scrim = it)) }, valueRange = 0f..0.9f)
        OutlinedButton(
            onClick = {
                pendingId = config.appWidgetId
                picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("PNG, JPG, or GIF background")
        }
    }
}

@Composable
private fun BagsPage(state: DeskState, vm: MoonViewModel) {
    val currency = state.settings.currency
    val rows = state.holdings.map { holding ->
        val price = state.quotes.find { it.coinId == holding.coinId }?.price ?: 0.0
        holding to holding.amount * price
    }.sortedByDescending { it.second }
    val total = rows.sumOf { it.second }
    val hidden = state.settings.hideBalances
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Net worth", color = Brass)
            Text(if (hidden) "••••" else CompactMoney.format(total, currency), fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineLarge)
        }
        TextButton(onClick = { vm.patch { it.copy(hideBalances = !hidden) } }) { Text(if (hidden) "Show" else "Hide") }
    }
    var symbol by remember { mutableStateOf("BTC") }
    var amount by remember { mutableStateOf("") }
    OutlinedTextField(symbol, { symbol = it }, label = { Text("Ticker") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        val qty = amount.toDoubleOrNull()
        if (qty != null && qty > 0) vm.addManual(symbol, qty)
    }, modifier = Modifier.fillMaxWidth()) { Text("Add holding") }
    var chain by remember { mutableStateOf("eth") }
    var address by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = chain == "eth", onClick = { chain = "eth" }, label = { Text("Ethereum") })
        FilterChip(selected = chain == "sol", onClick = { chain = "sol" }, label = { Text("Solana") })
    }
    OutlinedTextField(address, { address = it }, label = { Text("Public address") }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = { vm.trackWallet(chain, address) }, modifier = Modifier.fillMaxWidth()) { Text("Fetch public balance") }
    rows.forEach { (holding, value) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${holding.symbol}  ${holding.source}")
                Text(holding.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (hidden) "••••" else CompactMoney.format(value, currency))
                TextButton(onClick = { vm.removeHolding(holding.id) }) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun SettingsPage(state: DeskState, vm: MoonViewModel) {
    val settings = state.settings
    Box(Modifier.fillMaxWidth()) {
        if (!settings.settingsPath.isNullOrBlank()) {
            val imageMod = if (Build.VERSION.SDK_INT >= 31) Modifier.blur((settings.settingsDim * 18).dp) else Modifier
            AsyncImage(
                model = File(settings.settingsPath),
                contentDescription = null,
                modifier = imageMod.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f)).padding(8.dp)) {
            Text("Fiat")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("usd", "eur", "gbp", "jpy").forEach { code ->
                    FilterChip(selected = settings.currency == code, onClick = { vm.patch { it.copy(currency = code) } }, label = { Text(code.uppercase()) })
                }
            }
            Text("Sync")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    FilterChip(selected = settings.intervalMin == minutes, onClick = { vm.patch { it.copy(intervalMin = minutes) } }, label = { Text(if (minutes == 60) "1 hr" else "$minutes m") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ink", "paper", "oled").forEach { theme ->
                    FilterChip(selected = settings.theme == theme, onClick = { vm.patch { it.copy(theme = theme) } }, label = { Text(theme) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Material You")
                Switch(checked = settings.materialYou, onCheckedChange = { checked -> vm.patch { it.copy(materialYou = checked) } })
            }
            Text("Glass ${(settings.glass * 100).toInt()}%")
            Slider(settings.glass, { value -> vm.patch { it.copy(glass = value) } }, valueRange = 0f..0.8f)
            Text("Corners ${settings.radiusDp} dp")
            Slider(settings.radiusDp.toFloat(), { value -> vm.patch { it.copy(radiusDp = value.toInt()) } }, valueRange = 8f..32f)
            Text("Type ${"%.2f".format(settings.fontScale)}")
            Slider(settings.fontScale, { value -> vm.patch { it.copy(fontScale = value) } }, valueRange = 0.9f..1.2f)
            Text("Splash ${settings.splashSeconds}s")
            Slider(settings.splashSeconds.toFloat(), { value -> vm.patch { it.copy(splashSeconds = value.toInt().coerceIn(1, 5)) } }, valueRange = 1f..5f)
            MediaPick("Splash image or GIF") { uri -> vm.importMedia(uri, "splash") { settings, path -> settings.copy(splashPath = path) } }
            MediaPick("Settings background") { uri -> vm.importMedia(uri, "settings") { settings, path -> settings.copy(settingsPath = path) } }
            TextButton(onClick = { vm.patch { it.copy(splashPath = null, settingsPath = null) } }) { Text("Reset media") }
            Text("Exchange keys stay in the Android Keystore. Disable trading and withdrawals.", color = Down)
            var cbName by remember { mutableStateOf("") }
            var cbPem by remember { mutableStateOf("") }
            var bnKey by remember { mutableStateOf("") }
            var bnSecret by remember { mutableStateOf("") }
            OutlinedTextField(cbName, { cbName = it }, label = { Text("Coinbase key name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cbPem, { cbPem = it }, label = { Text("Coinbase EC private key PEM") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.saveCoinbase(cbName, cbPem); cbPem = "" }, modifier = Modifier.fillMaxWidth()) { Text("Save Coinbase key") }
            OutlinedTextField(bnKey, { bnKey = it }, label = { Text("Binance read-only key") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bnSecret, { bnSecret = it }, label = { Text("Binance secret") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.saveBinance(bnKey, bnSecret); bnSecret = "" }, modifier = Modifier.fillMaxWidth()) { Text("Save Binance key") }
            OutlinedButton(onClick = { vm.syncExchanges() }, modifier = Modifier.fillMaxWidth()) { Text("Sync exchange balances") }
            TextButton(onClick = { vm.clearSecrets() }) { Text("Clear keys") }
        }
    }
}

@Composable
private fun MediaPick(label: String, onUri: (android.net.Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> if (uri != null) onUri(uri) }
    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

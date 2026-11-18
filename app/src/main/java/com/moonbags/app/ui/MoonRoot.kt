package com.moonbags.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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

private enum class Page { Tape, Widgets, Settings }

private val Night = Color(0xFF0E0E10)
private val Card = Color(0xFF1C1C1E)
private val Pill = Color(0xFF2C2C2E)

@Composable
fun MoonRoot(vm: MoonViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var splash by remember { mutableStateOf(true) }
    MoonTheme(state.settings.theme, state.settings.materialYou) {
        if (splash) {
            LaunchSplash(state.settings.splashPath) { splash = false }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(vm.splashMillis())
                splash = false
            }
            return@MoonTheme
        }
        var page by remember { mutableStateOf(Page.Tape) }
        var menu by remember { mutableStateOf("home") }
        Box(Modifier.fillMaxSize().background(Night)) {
            if (page == Page.Settings && !state.settings.settingsPath.isNullOrBlank()) {
                AsyncImage(model = File(state.settings.settingsPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.settings.settingsDim.coerceIn(0.05f, 0.85f))))
            }
            when (page) {
                Page.Tape -> TapePage(state, vm)
                Page.Widgets -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { page = Page.Tape }) { Text("Close", color = Color.White) }
                        Text("Widgets", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(48.dp))
                    }
                    StudioPage(state, vm)
                }
                Page.Settings -> SettingsPage(state, vm, menu, { menu = it }) { page = Page.Tape; menu = "home" }
            }
            if (page == Page.Tape) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).clip(CircleShape).background(Pill).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clickable { page = Page.Widgets },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color.Transparent).then(Modifier)) {
                            Canvas(Modifier.size(18.dp)) {
                                drawCircle(Color.White, style = Stroke(width = 2.dp.toPx()))
                            }
                        }
                    }
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(40.dp).clickable { page = Page.Settings })
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
private fun TapePage(state: DeskState, vm: MoonViewModel) {
    val currency = state.settings.currency
    val sub = parseHex(state.settings.subtextColor)
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchCoinDto>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val watch = state.settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.error?.let { Text(it, color = Down) }
            state.quotes.filter { it.coinId in watch }.forEach { quote ->
                WatchedRow(quote, currency, sub) {
                    vm.patch { it.copy(watchIds = watch.filterNot { id -> id == quote.coinId }.joinToString(",")) }
                }
            }
        }
        if (hits.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 84.dp).clip(RoundedCornerShape(16.dp)).background(Card).padding(vertical = 4.dp),
            ) {
                hits.take(8).forEach { hit ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.addWatched(hit)
                            query = ""
                            hits = emptyList()
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            if (hit.image.isNotBlank()) {
                                AsyncImage(model = hit.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Text(hit.symbol.take(1), color = Color.Black)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(hit.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(spotLabel(hit.price, currency), color = Color.White)
                            }
                            Text(hit.symbol.uppercase() + "  " + CompactMoney.format(hit.marketCap, currency) + if (hit.chain.isBlank()) "" else "  " + hit.chain, color = sub, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    scope.launch { hits = if (it.trim().length < 2) emptyList() else runCatching { vm.search(it) }.getOrDefault(emptyList()) }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search Crypto") },
                singleLine = true,
                shape = CircleShape,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Pill,
                    unfocusedContainerColor = Pill,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Pill).clickable {
                    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                    GmsBarcodeScanning.getClient(context, options).startScan()
                        .addOnSuccessListener { barcode ->
                            query = barcode.rawValue.orEmpty()
                            scope.launch { hits = if (query.length < 2) emptyList() else runCatching { vm.search(query) }.getOrDefault(emptyList()) }
                        }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR", tint = Color.White)
            }
        }
    }
}

private fun spotLabel(price: Double, currency: String) = if (price > 0 && price < 0.01) "<$0.01" else CompactMoney.format(price, currency)

@Composable
private fun WatchedRow(quote: QuoteEntity, currency: String, sub: Color, onRemove: () -> Unit) {
    var drag by remember(quote.coinId) { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxWidth()) {
        Text("Remove", color = Color(0xFFFF5A6A), modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(drag.roundToInt(), 0) }
                .background(Color(0xFF1C1C1E))
                .padding(vertical = 10.dp)
                .pointerInput(quote.coinId) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag > 90f) onRemove()
                            drag = 0f
                        },
                        onHorizontalDrag = { _, amount -> drag = (drag + amount).coerceIn(0f, 160f) },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (!quote.imageUrl.isNullOrBlank()) {
                    AsyncImage(model = quote.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(quote.symbol.take(1), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(quote.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(spotLabel(quote.price, currency), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
                Text(
                    spotLabel(quote.price, currency) + (if (quote.change24h >= 0) "  ▲  " else "  ▼  ") + quote.symbol + "  " + CompactMoney.format(quote.marketCap, currency),
                    color = sub,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun parseHex(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF8E8E93))

@Composable
private fun ColorPicker(hex: String, onChange: (String) -> Unit) {
    val color = parseHex(hex)
    val hsv = remember(hex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color))
        ThinSlider("Hue", hsv[0], 0f..360f, color, Color(0xFF8E8E93)) { hue ->
            onChange(String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hue, hsv[1].coerceAtLeast(0.15f), hsv[2].coerceIn(0.25f, 0.95f)))))
        }
        ThinSlider("Light", hsv[2], 0.15f..0.95f, color, Color(0xFF8E8E93)) { light ->
            onChange(String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceAtLeast(0.15f), light))))
        }
    }
}

@Composable
private fun ThinSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, color: Color, sub: Color, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(value.toInt().toString(), color = sub, fontSize = 12.sp)
        Box(
            Modifier.fillMaxWidth().height(28.dp).pointerInput(range) {
                fun apply(x: Float) {
                    val p = (x / size.width).coerceIn(0f, 1f)
                    onChange(range.start + p * (range.endInclusive - range.start))
                }
                detectTapGestures { apply(it.x) }
                detectHorizontalDragGestures { change, _ -> apply(change.position.x) }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2f
                val p = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                val x = p * size.width
                drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(0f, y), Offset(x, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color, radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
            }
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
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = config.alpha), RoundedCornerShape(state.settings.radiusDp.dp))
                .padding(16.dp),
        ) {
            if (!config.backgroundPath.isNullOrBlank()) {
                AsyncImage(
                    model = File(config.backgroundPath),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = config.alpha,
                )
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = config.scrim * config.alpha)))
            }
            Column {
                Text(snap.title, color = Brass)
                Text(snap.primary, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineMedium)
                Text(snap.secondary, color = if (snap.up) Up else Down)
                TrendLine(snap.spark, snap.up)
                snap.lines.forEach { Text("${it.first}  ${it.second}") }
            }
        }
        Text("Transparency ${((1f - config.alpha) * 100).toInt()}%")
        Slider(
            value = 1f - config.alpha,
            onValueChange = { vm.updateWidgetConfig(config.copy(alpha = (1f - it).coerceIn(0.15f, 1f))) },
            valueRange = 0f..0.85f,
        )
        if (config.kind == WidgetKinds.TILE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spot price instead of cap")
                Switch(checked = config.showPrice, onCheckedChange = { vm.updateWidgetConfig(config.copy(showPrice = it)) })
            }
        }
        Text("PNG only. GIFs are not used on widgets.", color = Color(0xFF8E8E93), fontSize = 12.sp)
        OutlinedButton(
            onClick = {
                pendingId = config.appWidgetId
                picker.launch(PickVisualMediaRequest(PickVisualMedia.SingleMimeType("image/png")))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add custom background")
        }
        Button(
            onClick = { vm.updateWidgetConfig(config.copy(acrylic = !config.acrylic, backgroundPath = if (config.acrylic) config.backgroundPath else null)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (config.acrylic) "Acrylic background on" else "Acrylic background") }
        if (config.acrylic) {
            val slider = parseHex(state.settings.sliderColor)
            val sub = parseHex(state.settings.subtextColor)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Black" to Triple(0f, 0f, 0.08f), "Red" to Triple(4f, 0.72f, 0.32f), "Blue" to Triple(214f, 0.7f, 0.32f), "Green" to Triple(142f, 0.55f, 0.28f)).forEach { (name, tone) ->
                    FilterChip(selected = false, onClick = { vm.updateWidgetConfig(config.copy(acrylicHue = tone.first, acrylicSat = tone.second, acrylicLight = tone.third)) }, label = { Text(name) })
                }
            }
            ThinSlider("Hue", config.acrylicHue, 0f..360f, slider, sub) { vm.updateWidgetConfig(config.copy(acrylicHue = it, acrylicSat = if (config.acrylicSat == 0f) 0.6f else config.acrylicSat)) }
            ThinSlider("Lighter or darker", config.acrylicLight, 0.04f..0.8f, slider, sub) { vm.updateWidgetConfig(config.copy(acrylicLight = it)) }
            Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp)).background(Color.hsl(config.acrylicHue, config.acrylicSat.coerceIn(0f, 1f), config.acrylicLight.coerceIn(0f, 1f))))
        }
    }
}

@Composable
private fun MemePicker(state: DeskState, vm: MoonViewModel) {
    var query by remember { mutableStateOf("") }
    var chain by remember { mutableStateOf("all") }
    var hits by remember { mutableStateOf<List<SearchCoinDto>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query, chain) {
        kotlinx.coroutines.delay(250)
        hits = runCatching { vm.searchMemes(query, chain) }.getOrDefault(emptyList())
    }
    Text("Solana and Base memes", color = Brass)
    OutlinedTextField(query, { query = it }, label = { Text("Name or contract address") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = chain == "all", onClick = { chain = "all" }, label = { Text("Both") })
        FilterChip(selected = chain == "solana", onClick = { chain = "solana" }, label = { Text("Solana") })
        FilterChip(selected = chain == "base", onClick = { chain = "base" }, label = { Text("Base") })
    }
    hits.forEach { hit ->
        OutlinedButton(
            onClick = {
                val target = state.widgets.find { it.kind == WidgetKinds.TILE || it.kind == WidgetKinds.PILL } ?: state.widgets.firstOrNull()
                if (target == null) return@OutlinedButton
                scope.launch { vm.pinMeme(target, hit) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${hit.name} · ${hit.chain} · ${CompactMoney.format(hit.marketCap, "usd")}")
        }
    }
}

@Composable
private fun AppPicker(onClose: () -> Unit, onPick: (String, String) -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        resolved
            .map { it.loadLabel(context.packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text("Choose app") },
        text = {
            LazyColumn(Modifier.height(360.dp)) {
                items(apps, key = { it.second }) { (label, pkg) ->
                    TextButton(onClick = { onPick(pkg, label) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    )
}

@Composable
private fun TrendLine(values: List<Double>, up: Boolean) {
    if (values.size < 2) return
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val min = values.min()
        val max = values.max().let { if (it == min) it + 1 else it }
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index / (values.size - 1f) * size.width
            val y = size.height - ((value - min) / (max - min)).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = if (up) Up else Down, style = Stroke(width = 4f, cap = StrokeCap.Round))
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
private fun SettingsPage(state: DeskState, vm: MoonViewModel, menu: String, onMenu: (String) -> Unit, onClose: () -> Unit) {
    val settings = state.settings
    val sub = parseHex(settings.subtextColor)
    val slider = parseHex(settings.sliderColor)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (menu == "home") onClose() else onMenu("home") }) { Text(if (menu == "home") "Close" else "Back", color = Color.White) }
            Text(if (menu == "theme") "Theme Settings" else "Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(48.dp))
        }
        if (menu == "home") {
            listOf(
                "general" to "General",
                "theme" to "Theme Settings",
                "splash" to "Splash",
                "tap" to "Widget tap",
                "keys" to "Exchange keys",
            ).forEach { (id, title) ->
                Text(title, color = Color.White, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).clickable { onMenu(id) }.padding(16.dp))
            }
        }
        if (menu == "general") {
            Text("Fiat", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("usd", "eur", "gbp", "jpy").forEach { code ->
                    FilterChip(selected = settings.currency == code, onClick = { vm.patch { it.copy(currency = code) } }, label = { Text(code.uppercase()) })
                }
            }
            Text("Sync", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { minutes ->
                    FilterChip(selected = settings.intervalMin == minutes, onClick = { vm.patch { it.copy(intervalMin = minutes) } }, label = { Text(if (minutes == 60) "1 hr" else "$minutes m") })
                }
            }
            TextButton(onClick = { vm.patch { it.copy(hideBalances = !settings.hideBalances) } }) { Text(if (settings.hideBalances) "Balances hidden" else "Balances visible", color = Color.White) }
        }
        if (menu == "theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ink", "oled", "paper").forEach { theme ->
                    FilterChip(selected = settings.theme == theme, onClick = { vm.patch { it.copy(theme = theme) } }, label = { Text(theme) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Material You", color = Color.White)
                Switch(checked = settings.materialYou, onCheckedChange = { checked -> vm.patch { it.copy(materialYou = checked) } })
            }
            Text("Slider color", color = Color.White)
            ColorPicker(settings.sliderColor) { value -> vm.patch { it.copy(sliderColor = value) } }
            Text("Subtext color", color = Color.White)
            ColorPicker(settings.subtextColor) { value -> vm.patch { it.copy(subtextColor = value) } }
            ThinSlider("Glass", settings.glass, 0f..0.8f, slider, sub) { value -> vm.patch { it.copy(glass = value) } }
            ThinSlider("Corners", settings.radiusDp.toFloat(), 8f..32f, slider, sub) { value -> vm.patch { it.copy(radiusDp = value.toInt()) } }
            ThinSlider("Type", settings.fontScale, 0.9f..1.2f, slider, sub) { value -> vm.patch { it.copy(fontScale = value) } }
            ThinSlider("Scrim", settings.scrim, 0f..0.9f, slider, sub) { value -> vm.patch { it.copy(scrim = value) } }
            ThinSlider("Settings dim", settings.settingsDim, 0.05f..0.9f, slider, sub) { value -> vm.patch { it.copy(settingsDim = value) } }
            MediaPick("Settings background") { uri -> vm.importMedia(uri, "settings") { current, path -> current.copy(settingsPath = path) } }
        }
        if (menu == "splash") {
            ThinSlider("Splash seconds", settings.splashSeconds.toFloat(), 1f..5f, slider, sub) { value -> vm.patch { it.copy(splashSeconds = value.toInt().coerceIn(1, 5)) } }
            MediaPick("Splash image or GIF") { uri -> vm.importMedia(uri, "splash") { current, path -> current.copy(splashPath = path) } }
            TextButton(onClick = { vm.patch { it.copy(splashPath = null) } }) { Text("Reset splash", color = sub) }
        }
        if (menu == "tap") {
            var picking by remember { mutableStateOf(false) }
            Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (settings.tapLabel.isBlank()) "Choose app" else "Opens ${settings.tapLabel}")
            }
            if (picking) {
                AppPicker(onClose = { picking = false }, onPick = { pkg, label ->
                    vm.patch { it.copy(tapPackage = pkg, tapLabel = label) }
                    picking = false
                })
            }
        }
        if (menu == "keys") {
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

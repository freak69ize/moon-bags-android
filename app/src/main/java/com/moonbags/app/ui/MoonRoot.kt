package com.moonbags.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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

private val Night = Color(0xFF131318)
private val Card = Color(0xFF1C1C1E)
private val CardFace = Color(0xFF3A3842)
private val Pill = Color(0xFF2C2C2E)
private val SheetEase = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.8f, 0.2f, 1f)

@Composable
fun MoonRoot(vm: MoonViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var splash by remember { mutableStateOf(true) }
    MoonTheme(state.settings.theme) {
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
                Page.Tape -> TapePage(state, vm, onWidgets = { page = Page.Widgets }, onSettings = { page = Page.Settings })
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
            Text("Moon Bags", color = com.moonbags.app.ui.theme.Paper, style = MaterialTheme.typography.displayMedium)
            Text("NIGHT DESK", color = Brass)
        }
    }
}

@Composable
private fun TapePage(state: DeskState, vm: MoonViewModel, onWidgets: () -> Unit, onSettings: () -> Unit) {
    val currency = state.settings.currency
    val sub = parseHex(state.settings.subtextColor)
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchCoinDto>>(emptyList()) }
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val watch = state.settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    Box(Modifier.fillMaxSize().background(Night)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 176.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MoonBags", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(Pill).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clickable(onClick = onWidgets), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(20.dp)) {
                            drawCircle(Color.White, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                    Box(Modifier.size(40.dp).clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(20.dp)) {
                            val r = 1.6.dp.toPx()
                            val y = size.height / 2f
                            listOf(0.22f, 0.5f, 0.78f).forEach { fraction ->
                                drawCircle(Color.White, r, Offset(size.width * fraction, y))
                            }
                        }
                    }
                }
            }
            state.error?.let { Text(it, color = Down, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val rows = state.quotes.filter { it.coinId in watch }
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(parseHex(state.settings.cardColor)).padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        Text(if (state.refreshing) "Pulling prices…" else "Search a name or contract to add a coin.", color = sub, fontSize = 14.sp)
                    }
                }
                rows.forEach { quote ->
                    WatchedRow(quote, currency, sub, parseHex(state.settings.cardColor)) {
                        vm.patch { it.copy(watchIds = watch.filterNot { id -> id == quote.coinId }.joinToString(",")) }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            val progress = lift.value
            val inset = 12.dp * (1f - progress)
            val travel = with(LocalDensity.current) { maxHeight.toPx() }.coerceAtLeast(1f)
            Box(
                Modifier
                    .padding(start = inset, end = inset, bottom = inset)
                    .fillMaxWidth()
                    .height(96.dp + (maxHeight - 96.dp) * progress)
                    .clip(RoundedCornerShape(if (progress > 0.92f) 0.dp else 28.dp))
                    .background(parseHex(state.settings.searchBarColor).copy(alpha = state.settings.searchBarAlpha.coerceIn(0.15f, 1f)))
                    .border(1.dp, Color.White.copy(alpha = state.settings.pillBorderAlpha.coerceIn(0f, 1f)), RoundedCornerShape(if (progress > 0.92f) 0.dp else 28.dp)),
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SearchGrabber(
                        onDelta = { amount ->
                            scope.launch { lift.snapTo((lift.value - amount / travel).coerceIn(0f, 1f)) }
                        },
                        onEnd = {
                            scope.launch { lift.animateTo(if (lift.value > 0.32f) 1f else 0f, tween(460, easing = SheetEase)) }
                        },
                        onTap = {
                            scope.launch { lift.animateTo(if (lift.value > 0.5f) 0f else 1f, tween(460, easing = SheetEase)) }
                        },
                    )
                    if (progress > 0.55f) {
                        SearchField(query, parseHex(state.settings.searchPillColor), state.settings.pillBorderAlpha, onScan = {
                            val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                            GmsBarcodeScanning.getClient(context, options).startScan()
                                .addOnSuccessListener { barcode ->
                                    query = barcode.rawValue.orEmpty()
                                    scope.launch { hits = if (query.length < 2) emptyList() else runCatching { vm.search(query) }.getOrDefault(emptyList()) }
                                }
                        }) {
                            query = it
                            scope.launch { hits = if (it.trim().length < 2) emptyList() else runCatching { vm.search(it) }.getOrDefault(emptyList()) }
                        }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(parseHex(state.settings.searchPillColor)).border(1.dp, Color.White.copy(alpha = state.settings.pillBorderAlpha), CircleShape).clickable {
                                scope.launch { lift.animateTo(1f, tween(460, easing = SheetEase)) }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(20.dp))
                            Text("SEARCH COINMARKETCAP", color = Color(0xFFC7C7CC), fontSize = 15.sp)
                        }
                    }
                    if (progress > 0.08f) {
                        Column(Modifier.fillMaxWidth().weight(1f).graphicsLayer { alpha = progress }.verticalScroll(rememberScrollState())) {
                            if (hits.isEmpty()) {
                                Text(if (query.trim().length < 2) "Search a name or contract." else "No coins matched.", color = Color(0xFF8E8E93), modifier = Modifier.padding(top = 28.dp), fontSize = 14.sp)
                            }
                            hits.forEach { hit ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        vm.addWatched(hit)
                                        query = ""
                                        hits = emptyList()
                                        scope.launch { lift.animateTo(0f, tween(420, easing = SheetEase)) }
                                    }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                        if (hit.image.isNotBlank()) AsyncImage(model = hit.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else Text(hit.symbol.take(1), color = Color.White)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(hit.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(hit.symbol.uppercase(), color = sub, fontSize = 13.sp)
                                    }
                                    MiniSpark(hit.spark, hit.change24h >= 0, Modifier.width(56.dp).height(32.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.End) {
                                        Text(CompactMoney.price(hit.price, currency, 4), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text((if (hit.change24h >= 0) "↗ " else "↘ ") + String.format("%.2f%%", kotlin.math.abs(hit.change24h)), color = if (hit.change24h >= 0) Color(0xFF3DDC84) else Color(0xFFFF6B81), fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun spotLabel(price: Double, currency: String) = CompactMoney.price(price, currency)

@Composable
private fun SearchGrabber(onDelta: (Float) -> Unit, onEnd: () -> Unit, onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { onEnd() },
                    onVerticalDrag = { _, amount -> onDelta(amount) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color(0xFF8E8E93)))
    }
}

@Composable
private fun SearchField(value: String, pill: Color, borderAlpha: Float, onScan: (() -> Unit)?, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(pill)
                .border(1.dp, Color.White.copy(alpha = borderAlpha), CircleShape),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(20.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color(0xFFC7C7CC), fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFC7C7CC)),
                modifier = Modifier.fillMaxSize().padding(horizontal = 44.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) {
                            Text("SEARCH COINMARKETCAP", color = Color(0xFFC7C7CC), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        inner()
                    }
                },
            )
        }
        if (onScan != null) {
            Box(Modifier.size(48.dp).clickable(onClick = onScan), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR", tint = Color(0xFFF3F4F6))
            }
        }
    }
}

@Composable
private fun WatchedRow(quote: QuoteEntity, currency: String, sub: Color, card: Color, onRemove: () -> Unit) {
    var drag by remember(quote.coinId) { mutableFloatStateOf(0f) }
    val reveal = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    val up = quote.change24h >= 0
    Box(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        if (drag > 1f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(96.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFE10600))
                    .clickable(enabled = drag > 24f, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("Delete", color = Color.White, fontSize = 14.sp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(drag.roundToInt(), 0) }
                .shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.45f), spotColor = Color.Black.copy(alpha = 0.45f))
                .clip(RoundedCornerShape(28.dp))
                .background(card)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .pointerInput(quote.coinId) {
                    detectHorizontalDragGestures(
                        onDragEnd = { drag = if (drag > reveal * 0.35f) reveal else 0f },
                        onHorizontalDrag = { _, amount -> drag = (drag + amount).coerceIn(0f, reveal) },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                if (!quote.imageUrl.isNullOrBlank()) {
                    AsyncImage(model = quote.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(quote.symbol.take(1), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(quote.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(quote.symbol.uppercase(), color = sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MiniSpark(quote.sparkline, up, Modifier.width(44.dp).height(32.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.width(108.dp), horizontalAlignment = Alignment.End) {
                Text(tapePrice(quote.price, currency), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                Text((if (up) "↗ " else "↘ ") + String.format("%.2f%%", kotlin.math.abs(quote.change24h)), color = if (up) Color(0xFF3DDC84) else Color(0xFFFF6B81), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MiniSpark(raw: String, up: Boolean, modifier: Modifier) {
    val values = remember(raw) { raw.split(",").mapNotNull { it.toDoubleOrNull() } }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max().let { if (it == min) it + 1.0 else it }
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            val x = index / (values.size - 1f) * size.width
            val y = (1f - ((value - min) / (max - min)).toFloat()) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = if (up) Color(0xFF3DDC84) else Color(0xFFFF6B81), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun tapePrice(price: Double, currency: String) = CompactMoney.price(price, currency)

private fun parseHex(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF8E8E93))

private val BasicColors = listOf(
    "#000000",
    "#E23D3D",
    "#3AA0FF",
    "#A56BFF",
    "#F472B6",
    "#3DDC84",
    "#F08A2A",
)

@Composable
private fun ToneControl(label: String, hex: String, onChange: (String) -> Unit) {
    val color = parseHex(hex)
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    fun lighter(step: Float): String {
        val packed = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], (hsv[2] + step).coerceIn(0.02f, 0.96f)))
        return String.format("#%06X", 0xFFFFFF and packed)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape))
            BasicColors.forEach { swatch ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parseHex(swatch))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { onChange(swatch) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lighter or darker", color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(lighter(-0.08f)) }) { Text("−", color = Color.White, fontSize = 22.sp) }
            TextButton(onClick = { onChange(lighter(0.08f)) }) { Text("+", color = Color.White, fontSize = 22.sp) }
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
                awaitEachGesture {
                    val down = awaitFirstDown()
                    apply(down.position.x)
                    drag(down.id) { change ->
                        apply(change.position.x)
                        change.consume()
                    }
                }
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
            Text(CompactMoney.price(quote.price, currency))
            Text(CompactMoney.percent(quote.change24h), color = if (quote.change24h >= 0) Up else Down)
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun StudioPage(state: DeskState, vm: MoonViewModel) {
    val settings = state.settings
    val slider = parseHex(settings.sliderColor)
    val sub = parseHex(settings.subtextColor)
    var pickingApp by remember { mutableStateOf(false) }
    var pendingId by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        val config = state.widgets.find { it.appWidgetId == pendingId } ?: state.widgets.firstOrNull()
        if (uri != null && config != null) vm.importWidgetBackground(config, uri)
    }
    ToneControl("Widget color", settings.widgetBorder) { value -> vm.patch { it.copy(widgetBorder = value) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Tint background", color = Color.White)
        Switch(checked = settings.widgetTint, onCheckedChange = { checked -> vm.patch { it.copy(widgetTint = checked) } })
    }
    ThinSlider("Transparency", (1f - settings.widgetAlpha) * 100f, 0f..100f, slider, sub) { value ->
        vm.patch { it.copy(widgetAlpha = (1f - value / 100f).coerceIn(0f, 1f)) }
    }
    Button(onClick = { pickingApp = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (settings.tapLabel.isBlank()) "Widget tap opens nothing" else "Widget tap opens ${settings.tapLabel}")
    }
    if (pickingApp) {
        AppPicker(onClose = { pickingApp = false }, onPick = { pkg, label ->
            vm.patch { it.copy(tapPackage = pkg, tapLabel = label) }
            pickingApp = false
        })
    }
    Text("PNG only. The picture and transparency update the home screen widgets.", color = Color(0xFF8E8E93), fontSize = 12.sp)
    val target = state.widgets.firstOrNull()
    if (target != null) {
        if (!target.backgroundPath.isNullOrBlank()) {
            TextButton(onClick = { vm.updateWidgetConfig(target.copy(backgroundPath = null)) }) { Text("Remove background", color = Color.White) }
        }
        OutlinedButton(
            onClick = {
                pendingId = target.appWidgetId
                picker.launch(PickVisualMediaRequest(PickVisualMedia.SingleMimeType("image/png")))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add custom background") }
        Button(
            onClick = { vm.updateWidgetConfig(target.copy(acrylic = !target.acrylic, backgroundPath = if (target.acrylic) target.backgroundPath else null)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (target.acrylic) "Acrylic background on" else "Acrylic background") }
        if (target.acrylic) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Black" to Triple(0f, 0f, 0.08f), "Red" to Triple(4f, 0.72f, 0.32f), "Blue" to Triple(214f, 0.7f, 0.32f), "Green" to Triple(142f, 0.55f, 0.28f)).forEach { (name, tone) ->
                    FilterChip(selected = false, onClick = { vm.updateWidgetConfig(target.copy(acrylicHue = tone.first, acrylicSat = tone.second, acrylicLight = tone.third)) }, label = { Text(name) })
                }
            }
            ThinSlider("Hue", target.acrylicHue, 0f..360f, slider, sub) { vm.updateWidgetConfig(target.copy(acrylicHue = it, acrylicSat = if (target.acrylicSat == 0f) 0.6f else target.acrylicSat)) }
            ThinSlider("Lighter or darker", target.acrylicLight, 0.04f..0.8f, slider, sub) { vm.updateWidgetConfig(target.copy(acrylicLight = it)) }
        }
    }
    if (state.widgets.isEmpty()) {
        Text("No widgets on the home screen yet. Long-press the home screen, choose Widgets, then Moon Bags. These settings apply as soon as one is placed.", color = Color.White)
        return
    }
    state.widgets.forEach { config ->
        val snap = render(config, state.settings.currency, state.settings.hideBalances, state.global, state.quotes, state.holdings, null)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(state.settings.radiusDp.dp))
                .border(1.dp, parseHex(settings.widgetBorder), RoundedCornerShape(state.settings.radiusDp.dp))
                .background(
                    if (config.backgroundPath.isNullOrBlank()) {
                        (if (settings.widgetTint) parseHex(settings.widgetBorder) else Color(0xFF3A3842)).copy(alpha = settings.widgetAlpha)
                    } else {
                        Color.Transparent
                    },
                    RoundedCornerShape(state.settings.radiusDp.dp),
                )
                .padding(16.dp),
        ) {
            if (!config.backgroundPath.isNullOrBlank()) {
                AsyncImage(
                    model = File(config.backgroundPath),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = settings.widgetAlpha,
                )
                if (settings.widgetTint) {
                    Box(Modifier.matchParentSize().background(parseHex(settings.widgetBorder).copy(alpha = 0.45f * settings.widgetAlpha)))
                }
            }
            Column {
                Text(snap.title, color = Brass)
                Text(snap.primary, style = MaterialTheme.typography.headlineMedium)
                Text(snap.secondary, color = if (snap.up) Up else Down)
                TrendLine(snap.spark, snap.up)
                snap.lines.forEach { Text("${it.first}  ${it.second}") }
            }
        }
        if (config.kind == WidgetKinds.TILE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spot price instead of cap", color = Color.White)
                Switch(checked = config.showPrice, onCheckedChange = { vm.updateWidgetConfig(config.copy(showPrice = it)) })
            }
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
            Text(if (hidden) "••••" else CompactMoney.format(total, currency), style = MaterialTheme.typography.headlineLarge)
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
            ToneControl("Cards", settings.cardColor) { value -> vm.patch { it.copy(cardColor = value) } }
            ToneControl("Search pill", settings.searchPillColor) { value -> vm.patch { it.copy(searchPillColor = value) } }
            ToneControl("Search bar", settings.searchBarColor) { value -> vm.patch { it.copy(searchBarColor = value) } }
            ThinSlider("Search bar transparency", (1f - settings.searchBarAlpha) * 100f, 0f..85f, slider, sub) { value ->
                vm.patch { it.copy(searchBarAlpha = (1f - value / 100f).coerceIn(0.15f, 1f)) }
            }
            ThinSlider("Pill border", settings.pillBorderAlpha * 100f, 0f..80f, slider, sub) { value ->
                vm.patch { it.copy(pillBorderAlpha = (value / 100f).coerceIn(0f, 0.8f)) }
            }
            ToneControl("Slider color", settings.sliderColor) { value -> vm.patch { it.copy(sliderColor = value) } }
            ToneControl("Subtext color", settings.subtextColor) { value -> vm.patch { it.copy(subtextColor = value) } }
            ThinSlider("Corners", settings.radiusDp.toFloat(), 8f..32f, slider, sub) { value -> vm.patch { it.copy(radiusDp = value.toInt()) } }
            ThinSlider("Type", settings.fontScale, 0.9f..1.2f, slider, sub) { value -> vm.patch { it.copy(fontScale = value) } }
            MediaPick("Settings background") { uri -> vm.importMedia(uri, "settings") { current, path -> current.copy(settingsPath = path) } }
            if (!settings.settingsPath.isNullOrBlank()) {
                TextButton(onClick = { vm.patch { it.copy(settingsPath = null) } }) { Text("Remove background", color = Color.White) }
            }
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

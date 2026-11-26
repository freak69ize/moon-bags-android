package com.moonbags.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.MoreVert
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
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
import com.moonbags.app.data.media.samsungGalleryIntent
import com.moonbags.app.domain.CompactMoney
import com.moonbags.app.ui.theme.Brass
import com.moonbags.app.ui.theme.Down
import com.moonbags.app.ui.theme.Ink
import com.moonbags.app.ui.theme.MoonTheme
import com.moonbags.app.ui.theme.Up
import com.moonbags.app.data.local.WidgetConfigEntity
import com.moonbags.app.data.settings.UserSettings
import com.moonbags.app.widget.WidgetKinds
import com.moonbags.app.widget.WidgetSnap
import com.moonbags.app.widget.render
import kotlinx.coroutines.launch
import java.io.File

private enum class Page { Tape, Widgets, Settings }

private val Night = Color(0xFF131318)
private val CardFace = Color(0xFF3A3842)
private val Pill = Color(0xFF2C2C2E)
private val SheetEase = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.8f, 0.2f, 1f)

@Composable
private fun BouncyOverlay(opaque: Boolean, onDismissed: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    var closing by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.9f) }
    val overshoot = remember { android.view.animation.OvershootInterpolator(1.45f) }
    LaunchedEffect(closing) {
        if (!closing) {
            scale.snapTo(0.9f)
            scale.animateTo(1f, tween(280, easing = { overshoot.getInterpolation(it) }))
        } else {
            scale.animateTo(0.9f, tween(260, easing = { overshoot.getInterpolation(it) }))
            onDismissed()
        }
    }
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        Modifier.fillMaxSize().then(if (opaque) Modifier.background(Night) else Modifier).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    ) {
        content {
            runCatching { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
            closing = true
        }
    }
}

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
        BackHandler(enabled = page != Page.Tape) {
            when (page) {
                Page.Widgets -> page = Page.Tape
                Page.Settings -> {
                    val parent = settingsParent(menu)
                    if (parent == null) {
                        page = Page.Tape
                        menu = "home"
                    } else {
                        menu = parent
                    }
                }
                else -> Unit
            }
        }
        Box(Modifier.fillMaxSize().background(Night)) {
            if (!state.settings.settingsPath.isNullOrBlank()) {
                AsyncImage(model = File(state.settings.settingsPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.settings.settingsDim.coerceIn(0.05f, 0.85f))))
            }
            when (page) {
                Page.Tape -> TapePage(state, vm, onWidgets = { page = Page.Widgets }, onSettings = { page = Page.Settings })
                Page.Widgets -> BouncyOverlay(opaque = state.settings.settingsPath.isNullOrBlank(), onDismissed = { page = Page.Tape }) { close ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = close) { Text("Close", color = Color.White) }
                            Text("Widgets", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(48.dp))
                        }
                        StudioPage(state, vm)
                    }
                    }
                }
                Page.Settings -> BouncyOverlay(opaque = state.settings.settingsPath.isNullOrBlank(), onDismissed = { page = Page.Tape; menu = "home" }) { close ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        SettingsPage(state, vm, menu, { menu = it }) { close() }
                    }
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
            Text("Moon Bags", color = com.moonbags.app.ui.theme.Paper, style = MaterialTheme.typography.displayMedium)
            Text("NIGHT DESK", color = Brass)
        }
    }
}

private fun strongTap(view: android.view.View, context: android.content.Context) {
    runCatching { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
    runCatching {
        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(28, 120))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(28)
        }
    }
}

@Composable
private fun TapePage(state: DeskState, vm: MoonViewModel, onWidgets: () -> Unit, onSettings: () -> Unit) {
    val currency = state.settings.currency
    val sub = parseHex(state.settings.subtextColor)
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchCoinDto>>(emptyList()) }
    var detail by remember { mutableStateOf<SearchCoinDto?>(null) }
    var lift by remember { mutableFloatStateOf(0f) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var order by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val watch = state.settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val refreshingNow by rememberUpdatedState(state.refreshing)
    val draggingNow by rememberUpdatedState(draggingId)
    var pull by remember { mutableFloatStateOf(0f) }
    val pullThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val pullScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull <= 0f || available.y >= 0f || refreshingNow || draggingNow != null) return Offset.Zero
                val eat = kotlin.math.min(pull, -available.y)
                pull -= eat
                return Offset(0f, -eat)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (refreshingNow || draggingNow != null || source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pull = (pull + available.y * 0.5f).coerceAtMost(pullThreshold * 1.5f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull >= pullThreshold && draggingNow == null && !refreshingNow) vm.refresh()
                pull = 0f
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720
        Box(Modifier.fillMaxSize().statusBarsPadding().nestedScroll(pullScroll)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = draggingId == null).padding(bottom = 176.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MoonBags", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(Pill).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clickable {
                        strongTap(view, context)
                        onWidgets()
                    }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(20.dp)) {
                            drawCircle(Color.White, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                    Box(Modifier.size(40.dp).clickable {
                        strongTap(view, context)
                        onSettings()
                    }, contentAlignment = Alignment.Center) {
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
            val rows = state.quotes.filter { it.coinId in watch }
            LaunchedEffect(rows.joinToString { it.coinId }) {
                val ids = rows.map { it.coinId }
                order = if (order.isEmpty()) ids else ids.sortedBy { id -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
            }
            val shown = order.mapNotNull { id -> rows.find { it.coinId == id } }
            val line = parseHex(state.settings.menuLine)
            val group = parseHex(state.settings.cardColor).copy(alpha = state.settings.menuAlpha.coerceIn(0.2f, 1f))
            val rowPx = with(LocalDensity.current) { 76.dp.toPx() }
            val dropIndex = if (dragStart < 0) -1 else (dragStart + kotlin.math.round(dragDy / rowPx).toInt()).coerceIn(0, (shown.size - 1).coerceAtLeast(0))
            Column(
                Modifier
                    .padding(horizontal = if (wide) 16.dp else 12.dp)
                    .then(if (draggingId == null) Modifier.clip(RoundedCornerShape(16.dp)).clipToBounds() else Modifier)
                    .background(group, RoundedCornerShape(16.dp)),
            ) {
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        Text(if (state.refreshing) "Pulling prices…" else "Search a name or contract to add a coin.", color = sub, fontSize = 14.sp)
                    }
                }
                shown.forEachIndexed { index, quote ->
                    val dragging = draggingId == quote.coinId
                    val shift = when {
                        dragging || dragStart < 0 || dropIndex < 0 -> 0f
                        dragStart < dropIndex && index in (dragStart + 1)..dropIndex -> -rowPx
                        dragStart > dropIndex && index in dropIndex until dragStart -> rowPx
                        else -> 0f
                    }
                    val animatedShift by animateFloatAsState(shift, tween(180), label = "shift")
                    WatchedRow(
                        quote,
                        currency,
                        sub,
                        group,
                        dragging = dragging,
                        lift = if (dragging) dragDy else animatedShift,
                        onOpen = {
                            detail = SearchCoinDto(
                                id = quote.coinId,
                                symbol = quote.symbol,
                                name = quote.name,
                                price = quote.price,
                                marketCap = quote.marketCap,
                                image = quote.imageUrl.orEmpty(),
                                change24h = quote.change24h,
                                spark = quote.sparkline,
                            )
                        },
                        onRemove = {
                            vm.patch { it.copy(watchIds = watch.filterNot { id -> id == quote.coinId }.joinToString(",")) }
                        },
                        onGrab = {
                            draggingId = quote.coinId
                            dragStart = index
                            dragDy = 0f
                        },
                        onSlide = { dragDy += it },
                        onDrop = {
                            val from = dragStart
                            val to = if (from < 0) from else (from + kotlin.math.round(dragDy / rowPx).toInt()).coerceIn(0, shown.lastIndex)
                            if (from >= 0 && to >= 0 && from != to) {
                                val list = order.toMutableList()
                                val moved = list.removeAt(from)
                                list.add(to, moved)
                                order = list
                                vm.patch { it.copy(watchIds = list.joinToString(",")) }
                            }
                            draggingId = null
                            dragStart = -1
                            dragDy = 0f
                        },
                    )
                    if (index != shown.lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(line))
                    }
                }
            }
            }
        }
            if (state.refreshing || pull > 4f) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }
        BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight()) {
            val progress = lift.coerceIn(0f, 1f)
            val wideSheet = maxWidth >= 600.dp
            val travel = with(LocalDensity.current) { maxHeight.toPx().coerceAtLeast(1f) }
            val sheetWidth = if (wideSheet) minOf(maxWidth * 0.94f, 640.dp) else maxWidth
            val statusTop = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
            fun glide(target: Float, ms: Int = 280) {
                scope.launch {
                    val anim = Animatable(lift)
                    anim.animateTo(target, tween(ms, easing = SheetEase)) { lift = value }
                }
            }
            BackHandler(enabled = detail != null || progress > 0.08f) {
                if (detail != null) detail = null else glide(0f, 260)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp * (1f - progress), end = 12.dp * (1f - progress), bottom = 12.dp * (1f - progress))
                    .width(sheetWidth)
                    .height(76.dp + (maxHeight - 76.dp) * progress)
                    .clip(RoundedCornerShape(if (progress > 0.92f) 0.dp else 28.dp))
                    .background(parseHex(state.settings.searchBarColor).copy(alpha = state.settings.searchBarAlpha.coerceIn(0.15f, 1f)))
                    .border(1.dp, Color.White.copy(alpha = state.settings.pillBorderAlpha.coerceIn(0f, 1f)), RoundedCornerShape(if (progress > 0.92f) 0.dp else 28.dp)),
            ) {
                Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp + statusTop * progress, bottom = 12.dp)) {
                    val dragPill = Modifier.holdToDrag(
                        onDelta = { amount ->
                            if (amount < -6f) glide(1f) else if (amount > 0f) lift = (lift - amount / travel).coerceIn(0f, 1f)
                        },
                        onEnd = { if (lift < 0.98f) glide(if (lift > 0.08f) 1f else 0f) },
                    )
                    if (progress > 0.45f) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                glide(0f, 260)
                            }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                    }
                    if (progress > 0.55f) {
                        SearchField(query, parseHex(state.settings.searchPillColor), state.settings.pillBorderAlpha, dragPill, onScan = {
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
                            Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(parseHex(state.settings.searchPillColor)).border(1.dp, Color.White.copy(alpha = state.settings.pillBorderAlpha), CircleShape).then(dragPill).clickable {
                                glide(1f)
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
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(parseHex(state.settings.cardColor).copy(alpha = state.settings.menuAlpha.coerceIn(0.2f, 1f)))
                                        .clickable { detail = hit }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                                        if (hit.image.isNotBlank()) AsyncImage(model = hit.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else Text(hit.symbol.take(1), color = Color.White)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(hit.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(listOf(hit.symbol.uppercase(), hit.chain).filter { it.isNotBlank() }.joinToString(" · "), color = sub, fontSize = 13.sp)
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
        detail?.let { hit ->
            Box(Modifier.zIndex(4f).fillMaxSize()) {
            CoinDetailPage(hit, currency, vm, onBack = { detail = null }, onAdd = {
                vm.addWatched(hit)
                detail = null
                query = ""
                hits = emptyList()
                scope.launch {
                    val anim = Animatable(lift)
                    anim.animateTo(0f, tween(260, easing = SheetEase)) { lift = value }
                }
            })
            }
        }
    }
}

private fun spotLabel(price: Double, currency: String) = CompactMoney.price(price, currency)

private fun Modifier.holdToDrag(onDelta: (Float) -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if ((change.position - start).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull false
            }
        }
        if (releasedEarly != null) return@awaitEachGesture
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            onDelta(change.position.y - change.previousPosition.y)
            change.consume()
            if (!change.pressed) break
        }
        onEnd()
    }
}

@Composable
private fun SearchField(value: String, pill: Color, borderAlpha: Float, drag: Modifier, onScan: (() -> Unit)?, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(pill)
                .border(1.dp, Color.White.copy(alpha = borderAlpha), CircleShape)
                .then(drag),
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
private fun WatchedRow(
    quote: QuoteEntity,
    currency: String,
    sub: Color,
    card: Color,
    dragging: Boolean,
    lift: Float,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onGrab: () -> Unit,
    onSlide: (Float) -> Unit,
    onDrop: () -> Unit,
) {
    var drag by remember(quote.coinId) { mutableFloatStateOf(0f) }
    var crossed by remember(quote.coinId) { mutableStateOf(false) }
    val reveal = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    val view = androidx.compose.ui.platform.LocalView.current
    val up = quote.change24h >= 0
    val shape = if (dragging) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 2f else 0f)
            .graphicsLayer {
                translationY = lift
                val scale = if (dragging) 1.04f else 1f
                scaleX = scale
                scaleY = scale
                shadowElevation = if (dragging) 18.dp.toPx() else 0f
                this.shape = shape
                clip = dragging
            }
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(shape),
    ) {
        if (!dragging && drag > 1f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(with(androidx.compose.ui.platform.LocalDensity.current) { drag.toDp().coerceAtMost(96.dp) })
                    .fillMaxHeight()
                    .background(Color(0xFFE10600))
                    .clickable(enabled = drag > 24f, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("Delete", color = Color.White, fontSize = 14.sp, maxLines = 1)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(if (dragging) 0 else drag.roundToInt(), 0) }
                .background(card, shape)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .pointerInput(quote.coinId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val slop = viewConfiguration.touchSlop
                        val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "up"
                                if (!change.pressed) return@withTimeoutOrNull "up"
                                val delta = change.position - start
                                if (delta.getDistance() > slop) {
                                    val sideways = kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y)
                                    if (sideways) change.consume()
                                    return@withTimeoutOrNull if (sideways) "side" else "cancel"
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            "up"
                        }
                        when (outcome) {
                            "up" -> onOpen()
                            "side" -> {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val next = (change.position.x - start.x).coerceIn(0f, reveal)
                                    if (!crossed && next >= reveal * 0.35f) {
                                        crossed = true
                                        view.performHapticFeedback(
                                            if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.LONG_PRESS,
                                        )
                                    }
                                    if (next < reveal * 0.35f) crossed = false
                                    drag = next
                                    change.consume()
                                    if (!change.pressed) break
                                }
                                drag = if (drag > reveal * 0.35f) reveal else 0f
                                if (drag == 0f) crossed = false
                            }
                            null -> {
                                onGrab()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    onSlide(change.position.y - change.previousPosition.y)
                                    change.consume()
                                    if (!change.pressed) break
                                }
                                onDrop()
                            }
                        }
                    }
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

@Composable
private fun CoinDetailPage(hit: SearchCoinDto, currency: String, vm: MoonViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    var stats by remember(hit.id) { mutableStateOf<com.moonbags.app.data.repo.CoinStats?>(null) }
    var candles by remember(hit.id) { mutableStateOf(false) }
    var capAxis by remember(hit.id) { mutableStateOf(false) }
    var scrub by remember(hit.id) { mutableFloatStateOf(-1f) }
    LaunchedEffect(hit.id) { stats = runCatching { vm.coinStats(hit) }.getOrNull() }
    val price = stats?.price?.takeIf { it > 0 } ?: hit.price
    val cap = stats?.marketCap?.takeIf { it > 0 } ?: hit.marketCap
    val priceSeries = when {
        (stats?.prices?.size ?: 0) >= 2 -> stats?.prices.orEmpty()
        else -> hit.spark.split(",").mapNotNull { it.toDoubleOrNull() }
    }
    val chartSeries = when {
        capAxis && (stats?.caps?.size ?: 0) >= 2 -> stats?.caps.orEmpty()
        priceSeries.size >= 2 -> priceSeries
        else -> emptyList()
    }
    val loading = stats == null
    val scrubIndex = if (scrub < 0f || chartSeries.isEmpty()) -1 else (scrub * (chartSeries.size - 1)).toInt().coerceIn(0, chartSeries.lastIndex)
    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E10)).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(hit.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.symbol.uppercase(), color = Color(0xFF8E8E93), fontSize = 13.sp)
            }
            TextButton(onClick = onAdd) { Text("Add", color = Color.White) }
        }
        Text(CompactMoney.price(if (scrubIndex >= 0 && !capAxis) chartSeries[scrubIndex] else price, currency), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text((if (hit.change24h >= 0) "+" else "") + String.format("%.2f%%", hit.change24h), color = if (hit.change24h >= 0) Color(0xFF3DDC84) else Color(0xFFFF6B81))
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF16161A)), contentAlignment = Alignment.Center) {
            if (priceSeries.size >= 2) {
                MiniSpark(priceSeries.joinToString(","), hit.change24h >= 0, Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp))
            } else {
                Text(if (loading) "Loading sparkline" else "No recent prices", color = Color(0xFF8E8E93), fontSize = 13.sp)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !candles, onClick = { candles = false }, label = { Text("Line") })
            FilterChip(selected = candles, onClick = { candles = true }, label = { Text("Candles") })
            FilterChip(selected = !capAxis, onClick = { capAxis = false }, label = { Text("Price") })
            FilterChip(selected = capAxis, onClick = { capAxis = true }, label = { Text("Market cap") })
        }
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            DetailChart(chartSeries, hit.change24h >= 0, candles, scrub) { scrub = it }
            if (chartSeries.size < 2) {
                Text(
                    if (loading) "Loading chart" else "No history yet",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        StatRow("Market cap", moneyOrDash(cap, currency))
        StatRow("24h volume", moneyOrDash(stats?.volume ?: 0.0, currency))
        StatRow("Circulating supply", countOrDash(stats?.circulating ?: 0.0))
        StatRow("All-time high", moneyOrDash(stats?.ath ?: 0.0, currency))
        StatRow("Trading activity", textOrDash(stats?.activity ?: ""))
        StatRow("Typical hold time", textOrDash(stats?.holdTime ?: ""))
        StatRow("Popularity rank", if ((stats?.rank ?: 0) > 0) "#${stats?.rank}" else "—")
        StatRow("Mintable", mintableOrDash(stats?.mintable ?: ""))
        StatRow("Holders", countTextOrDash(stats?.holders ?: ""))
        StatRow("Network", networkName(stats?.network?.ifBlank { hit.chain } ?: hit.chain))
        StatRow("Contract", textOrDash(stats?.contract ?: ""))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val shown = value.trim().ifBlank { "—" }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFA1A1AA), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        Text(
            shown,
            color = Color(0xFFF4F4F5),
            fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 36.dp, max = 190.dp),
        )
    }
}

private fun moneyOrDash(value: Double, currency: String) = if (value > 0.0 && !value.isNaN()) CompactMoney.format(value, currency) else "—"

private fun countOrDash(value: Double) = if (value > 0.0 && !value.isNaN()) CompactMoney.format(value, "usd").removePrefix("$") else "—"

private fun textOrDash(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.isEmpty() || trimmed == "null") "—" else trimmed
}

private fun mintableOrDash(value: String): String = when (value.trim().lowercase()) {
    "yes" -> "Yes"
    "no" -> "No"
    else -> "—"
}

private fun countTextOrDash(value: String): String {
    val trimmed = value.trim()
    val number = trimmed.replace(",", "").toDoubleOrNull()
    return when {
        number != null && number > 0.0 -> countOrDash(number)
        trimmed.isEmpty() || trimmed == "—" -> "—"
        else -> trimmed
    }
}

private fun networkName(raw: String): String {
    val key = raw.trim().lowercase()
    if (key.isEmpty()) return "—"
    return when (key) {
        "sol", "solana", "solona" -> "Solana"
        "eth", "ethereum" -> "Ethereum"
        "base" -> "Base"
        "bsc", "bnb", "binance-smart-chain" -> "BNB Chain"
        "polygon", "matic", "polygon-pos" -> "Polygon"
        "arb", "arbitrum", "arbitrum-one" -> "Arbitrum"
        "op", "optimism", "optimistic-ethereum" -> "Optimism"
        "avax", "avalanche" -> "Avalanche"
        "tron", "trx" -> "Tron"
        "sui" -> "Sui"
        else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Composable
private fun DetailChart(values: List<Double>, up: Boolean, candles: Boolean, scrub: Float, onScrub: (Float) -> Unit) {
    val color = if (up) Color(0xFF3DDC84) else Color(0xFFFF6B81)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF16161A))
            .pointerInput(values) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onScrub((down.position.x / size.width).coerceIn(0f, 1f))
                    drag(down.id) { change ->
                        onScrub((change.position.x / size.width).coerceIn(0f, 1f))
                        change.consume()
                    }
                }
            },
    ) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max().let { if (it == min) it + 1 else it }
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 12.dp.toPx()
        fun yOf(value: Double) = bottom - ((value - min) / (max - min)).toFloat() * (bottom - top)
        if (!candles) {
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, value ->
                val x = left + index / (values.size - 1f) * (right - left)
                val y = yOf(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        } else {
            val group = (values.size / 28).coerceAtLeast(1)
            val chunks = values.chunked(group)
            val slot = (right - left) / chunks.size.coerceAtLeast(1)
            chunks.forEachIndexed { index, chunk ->
                val open = chunk.first()
                val close = chunk.last()
                val high = chunk.max()
                val low = chunk.min()
                val x = left + index * slot + slot / 2f
                val rising = close >= open
                val paint = if (rising) Color(0xFF3DDC84) else Color(0xFFFF6B81)
                drawLine(paint, Offset(x, yOf(high)), Offset(x, yOf(low)), strokeWidth = 1.5.dp.toPx())
                val bodyTop = yOf(maxOf(open, close))
                val bodyBottom = yOf(minOf(open, close)).coerceAtLeast(bodyTop + 1f)
                drawRect(paint, topLeft = Offset(x - slot * 0.28f, bodyTop), size = androidx.compose.ui.geometry.Size(slot * 0.56f, bodyBottom - bodyTop))
            }
        }
        if (scrub >= 0f) {
            val x = left + scrub.coerceIn(0f, 1f) * (right - left)
            drawLine(Color.White.copy(alpha = 0.7f), Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
        }
    }
}

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
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(color).border(1.dp, Color(0xFFF0F0F0), CircleShape))
            BasicColors.forEach { swatch ->
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(parseHex(swatch))
                        .border(1.dp, Color(0xFFF0F0F0), CircleShape)
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
private fun ThinSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, color: Color, sub: Color, decimals: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Text(label, color = Color.White, fontSize = 14.sp)
        Text(if (decimals == 0) value.toInt().toString() else String.format("%.1f", value), color = sub, fontSize = 12.sp)
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
    val view = androidx.compose.ui.platform.LocalView.current
    val tap: (() -> Unit) -> Unit = { block ->
        runCatching { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
        block()
    }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (context.contentResolver.getType(uri)?.contains("gif") == true) return@rememberLauncherForActivityResult
        val config = state.widgets.find { it.appWidgetId == pendingId } ?: state.widgets.firstOrNull()
        if (config != null) vm.importWidgetBackground(config, uri)
    }
    SettingPanel(settings.menuAlpha) {
    ToneControl("Widget outline", settings.widgetBorder) { value -> vm.patch { it.copy(widgetBorder = value) } }
    ToneControl("Widget background", opaque(settings.widgetFill)) { value ->
        vm.patch { it.copy(widgetFill = value, widgetAlpha = if (it.widgetAlpha < 0.04f) 0.72f else it.widgetAlpha) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Clear" to null, "Black" to "#000000", "Ink" to "#141414", "Red" to "#E23D3D", "Blue" to "#3AA0FF").forEach { (name, hex) ->
            FilterChip(
                selected = false,
                onClick = {
                    tap {
                        if (hex == null) vm.patch { it.copy(widgetAlpha = 0f) }
                        else vm.patch { it.copy(widgetFill = hex, widgetAlpha = if (it.widgetAlpha < 0.04f) 0.72f else it.widgetAlpha) }
                        state.widgets.firstOrNull()?.let { vm.updateWidgetConfig(it.copy(backgroundPath = null, acrylic = false)) }
                    }
                },
                label = { Text(name) },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Tint background", color = Color.White)
        Switch(checked = settings.widgetTint, onCheckedChange = { checked -> tap { vm.patch { it.copy(widgetTint = checked) } } })
    }
    ThinSlider("Transparency", (1f - settings.widgetAlpha) * 100f, 0f..100f, slider, sub) { value ->
        vm.patch { it.copy(widgetAlpha = (1f - value / 100f).coerceIn(0f, 1f)) }
    }
    ThinSlider("Corner roundness", settings.radiusDp.toFloat(), 0f..36f, slider, sub) { value ->
        vm.patch { it.copy(radiusDp = value.toInt().coerceIn(0, 36)) }
    }
    ThinSlider("Border width", settings.widgetStrokeDp, 0.5f..8f, slider, sub, decimals = 1) { value ->
        vm.patch { it.copy(widgetStrokeDp = value.coerceIn(0.5f, 8f)) }
    }
    Button(onClick = { tap { pickingApp = true } }, modifier = Modifier.fillMaxWidth()) {
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
            TextButton(onClick = { tap { vm.updateWidgetConfig(target.copy(backgroundPath = null)) } }) { Text("Remove background", color = Color.White) }
        }
        OutlinedButton(
            onClick = {
                tap {
                    pendingId = target.appWidgetId
                    picker.launch(samsungGalleryIntent(context))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add custom background") }
        Button(
            onClick = { tap { vm.updateWidgetConfig(target.copy(acrylic = !target.acrylic, backgroundPath = if (target.acrylic) target.backgroundPath else null)) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (target.acrylic) "Acrylic background on" else "Acrylic background") }
        if (target.acrylic) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Black" to Triple(0f, 0f, 0.08f), "Red" to Triple(4f, 0.72f, 0.32f), "Blue" to Triple(214f, 0.7f, 0.32f), "Green" to Triple(142f, 0.55f, 0.28f)).forEach { (name, tone) ->
                    FilterChip(selected = false, onClick = { tap { vm.updateWidgetConfig(target.copy(acrylicHue = tone.first, acrylicSat = tone.second, acrylicLight = tone.third)) } }, label = { Text(name) })
                }
            }
            ThinSlider("Hue", target.acrylicHue, 0f..360f, slider, sub) { vm.updateWidgetConfig(target.copy(acrylicHue = it, acrylicSat = if (target.acrylicSat == 0f) 0.6f else target.acrylicSat)) }
            ThinSlider("Lighter or darker", target.acrylicLight, 0.04f..0.8f, slider, sub) { vm.updateWidgetConfig(target.copy(acrylicLight = it)) }
        }
    }
    if (state.widgets.isEmpty()) {
        Text("No widgets on the home screen yet. Long-press the home screen, choose Widgets, then Moon Bags. The background below is what they will use.", color = Color.White)
    }
    val previewConfig = state.widgets.firstOrNull()
    val snap = render(
        previewConfig ?: com.moonbags.app.data.local.WidgetConfigEntity(0, WidgetKinds.MCAP, "bitcoin", false, null, settings.scrim, false, settings.widgetAlpha),
        settings.currency,
        settings.hideBalances,
        state.global,
        state.quotes,
        state.holdings,
        null,
    )
    WidgetPreview(settings, previewConfig, snap)
    state.widgets.drop(1).forEach { config ->
        val extra = render(config, settings.currency, settings.hideBalances, state.global, state.quotes, state.holdings, null)
        WidgetPreview(settings, config, extra)
    }
    state.widgets.forEach { config ->
        if (config.kind == WidgetKinds.TILE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spot price instead of cap", color = Color.White)
                Switch(checked = config.showPrice, onCheckedChange = { checked -> tap { vm.updateWidgetConfig(config.copy(showPrice = checked)) } })
            }
        }
    }
    }
}

private fun opaque(hex: String): String {
    val raw = hex.removePrefix("#")
    return if (raw.length >= 8) "#" + raw.takeLast(6) else if (raw.length == 6) "#$raw" else "#000000"
}

@Composable
private fun WidgetPreview(settings: UserSettings, config: WidgetConfigEntity?, snap: WidgetSnap) {
    val shape = RoundedCornerShape(settings.radiusDp.dp)
    val alpha = settings.widgetAlpha
    val border = parseHex(settings.widgetBorder)
    val fill = parseHex(settings.widgetFill)
    Box(Modifier.fillMaxWidth().height(168.dp)) {
        Box(Modifier.matchParentSize().clip(shape)) {
            if (config != null && !config.backgroundPath.isNullOrBlank() && alpha > 0.004f) {
                AsyncImage(
                    model = File(config.backgroundPath),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = alpha,
                )
                if (settings.widgetTint) {
                    Box(Modifier.matchParentSize().background(border.copy(alpha = 0.45f * alpha)))
                }
            } else if (config?.acrylic == true && alpha > 0.004f) {
                val argb = android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(config.acrylicHue, config.acrylicSat.coerceIn(0f, 1f), config.acrylicLight.coerceIn(0f, 1f)))
                Box(Modifier.matchParentSize().background(Color(argb)))
            } else if (alpha > 0.004f && fill.alpha > 0.004f) {
                Box(Modifier.matchParentSize().background(fill.copy(alpha = alpha)))
            }
        }
        Box(Modifier.matchParentSize().border(settings.widgetStrokeDp.coerceIn(0.5f, 8f).dp, border, shape))
        Column(Modifier.padding(16.dp)) {
            Text(snap.title, color = Brass)
            Text(snap.primary, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(snap.secondary, color = if (snap.up) Up else Down)
            TrendLine(snap.spark, snap.up)
            snap.lines.forEach { Text("${it.first}  ${it.second}", color = Color.White) }
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
    val line = parseHex(settings.menuLine)
    val parent = settingsParent(menu)
    Column(Modifier.widthIn(max = 760.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (parent == null) onClose() else onMenu(parent) }) { Text(if (parent == null) "Close" else "Back", color = Color.White) }
            Text(settingsTitle(menu), color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(48.dp))
        }
        if (menu == "home") {
            SettingGroup(
                listOf(
                    "general" to "General",
                    "theme" to "Theme Settings",
                    "splash" to "Splash",
                    "tap" to "Widget tap",
                    "keys" to "Exchange keys",
                ),
                line,
                settings.menuAlpha,
                onMenu,
            )
        }
        if (menu == "general") {
            SettingPanel(settings.menuAlpha) {
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
        }
        if (menu == "theme") {
            SettingGroup(
                listOf(
                    "theme-cards" to "Cards",
                    "theme-search" to "Search",
                    "theme-background" to "Background",
                    "theme-text" to "Text",
                    "theme-corners" to "Corners",
                ),
                line,
                settings.menuAlpha,
                onMenu,
            )
        }
        if (menu == "theme-cards") {
            SettingPanel(settings.menuAlpha) {
                ToneControl("Cards", settings.cardColor) { value -> vm.patch { it.copy(cardColor = value) } }
                ToneControl("Divider", settings.menuLine) { value -> vm.patch { it.copy(menuLine = value) } }
                ThinSlider("Transparency", (1f - settings.menuAlpha) * 100f, 0f..80f, slider, sub) { value ->
                    vm.patch { it.copy(menuAlpha = (1f - value / 100f).coerceIn(0.2f, 1f)) }
                }
            }
        }
        if (menu == "theme-search") {
            SettingPanel(settings.menuAlpha) {
                ToneControl("Search pill", settings.searchPillColor) { value -> vm.patch { it.copy(searchPillColor = value) } }
                ToneControl("Search bar", settings.searchBarColor) { value -> vm.patch { it.copy(searchBarColor = value) } }
                ThinSlider("Search bar transparency", (1f - settings.searchBarAlpha) * 100f, 0f..85f, slider, sub) { value ->
                    vm.patch { it.copy(searchBarAlpha = (1f - value / 100f).coerceIn(0.15f, 1f)) }
                }
                ThinSlider("Pill border", settings.pillBorderAlpha * 100f, 0f..80f, slider, sub) { value ->
                    vm.patch { it.copy(pillBorderAlpha = (value / 100f).coerceIn(0f, 0.8f)) }
                }
            }
        }
        if (menu == "theme-background") {
            SettingPanel(settings.menuAlpha) {
                MediaPick("Settings background") { uri -> vm.importMedia(uri, "settings") { current, path -> current.copy(settingsPath = path) } }
                if (!settings.settingsPath.isNullOrBlank()) {
                    TextButton(onClick = { vm.patch { it.copy(settingsPath = null) } }) { Text("Remove background", color = Color.White) }
                }
                ThinSlider("Transparency", settings.settingsDim * 100f, 5f..85f, slider, sub) { value ->
                    vm.patch { it.copy(settingsDim = (value / 100f).coerceIn(0.05f, 0.85f)) }
                }
            }
        }
        if (menu == "theme-text") {
            SettingPanel(settings.menuAlpha) {
                ToneControl("Slider color", settings.sliderColor) { value -> vm.patch { it.copy(sliderColor = value) } }
                ToneControl("Subtext color", settings.subtextColor) { value -> vm.patch { it.copy(subtextColor = value) } }
                ThinSlider("Type", settings.fontScale, 0.9f..1.2f, slider, sub) { value -> vm.patch { it.copy(fontScale = value) } }
            }
        }
        if (menu == "theme-corners") {
            SettingPanel(settings.menuAlpha) {
                ThinSlider("Corners", settings.radiusDp.toFloat(), 0f..36f, slider, sub) { value -> vm.patch { it.copy(radiusDp = value.toInt().coerceIn(0, 36)) } }
            }
        }
        if (menu == "splash") {
            SettingPanel(settings.menuAlpha) {
                ThinSlider("Splash seconds", settings.splashSeconds.toFloat(), 1f..5f, slider, sub) { value -> vm.patch { it.copy(splashSeconds = value.toInt().coerceIn(1, 5)) } }
                MediaPick("Splash image or GIF") { uri -> vm.importMedia(uri, "splash") { current, path -> current.copy(splashPath = path) } }
                TextButton(onClick = { vm.patch { it.copy(splashPath = null) } }) { Text("Reset splash", color = sub) }
            }
        }
        if (menu == "tap") {
            var picking by remember { mutableStateOf(false) }
            BackHandler(enabled = picking) { picking = false }
            SettingPanel(settings.menuAlpha) {
                Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settings.tapLabel.isBlank()) "Choose app" else "Opens ${settings.tapLabel}")
                }
            }
            if (picking) {
                AppPicker(onClose = { picking = false }, onPick = { pkg, label ->
                    vm.patch { it.copy(tapPackage = pkg, tapLabel = label) }
                    picking = false
                })
            }
        }
        if (menu == "keys") {
            SettingPanel(settings.menuAlpha) {
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
}

private fun settingsParent(menu: String): String? = when {
    menu == "home" -> null
    menu.startsWith("theme-") -> "theme"
    else -> "home"
}

private fun settingsTitle(menu: String) = when (menu) {
    "home" -> "Settings"
    "theme" -> "Theme Settings"
    "theme-cards" -> "Cards"
    "theme-search" -> "Search"
    "theme-background" -> "Background"
    "theme-text" -> "Text"
    "theme-corners" -> "Corners"
    "general" -> "General"
    "splash" -> "Splash"
    "tap" -> "Widget tap"
    "keys" -> "Exchange keys"
    else -> "Settings"
}

@Composable
private fun SettingGroup(items: List<Pair<String, String>>, line: Color, alpha: Float, onClick: (String) -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = alpha.coerceIn(0.05f, 1f)))) {
        items.forEachIndexed { index, (id, title) ->
            Text(
                title,
                color = Color.White,
                modifier = Modifier.fillMaxWidth().clickable {
                    runCatching { view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
                    onClick(id)
                }.padding(16.dp),
            )
            if (index != items.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(line))
            }
        }
    }
}

@Composable
private fun SettingPanel(alpha: Float, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = alpha.coerceIn(0.05f, 1f))).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun MediaPick(label: String, onUri: (android.net.Uri) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        onUri(uri)
    }
    OutlinedButton(onClick = { picker.launch(samsungGalleryIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

package com.moonbags.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.moonbags.app.MoonBagsApp
import com.moonbags.app.R
import com.moonbags.app.data.local.GlobalEntity
import com.moonbags.app.data.local.HoldingEntity
import com.moonbags.app.data.local.MoonDatabase
import com.moonbags.app.data.local.QuoteEntity
import com.moonbags.app.data.local.WidgetConfigEntity
import com.moonbags.app.domain.CompactMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object WidgetKinds {
    const val MCAP = "mcap"
    const val TILE = "tile"
    const val PILL = "pill"
    const val SPARK = "spark"
    const val SPARK_BAR = "sparkbar"
    const val WATCH = "watch"
    const val BOOK = "book"

    fun fromProvider(className: String): String = when {
        className.endsWith("SingleCoinReceiver") -> TILE
        className.endsWith("PillReceiver") -> PILL
        className.endsWith("SparklineBarReceiver") -> SPARK_BAR
        className.endsWith("SparklineReceiver") -> SPARK
        className.endsWith("WatchlistReceiver") -> WATCH
        className.endsWith("PortfolioReceiver") -> BOOK
        else -> MCAP
    }
}

data class WidgetSnap(
    val primary: String,
    val secondary: String,
    val up: Boolean,
    val title: String,
    val lines: List<Pair<String, String>>,
    val spark: List<Double>,
    val background: Bitmap?,
    val scrim: Float,
    val hidden: Boolean,
    val alpha: Float = 0.7f,
    val tapPackage: String = "",
    val acrylic: Boolean = false,
    val acrylicHue: Float = 220f,
    val acrylicSat: Float = 0.55f,
    val acrylicLight: Float = 0.18f,
    val border: Color = Color(0xFFD0BCFF),
    val fill: Color = Color.Transparent,
    val refreshNote: String = "",
    val cornerDp: Int = 22,
)

private val Paper = Color(0xFFEFE8DC)
private val White = Color(0xFFFFFFFF)
private val Ink = Color(0xFF1D1B20)
private val UpColor = Color(0xFF3DDC84)
private val DownColor = Color(0xFFFF4D4D)

object WidgetSnapshots {
    suspend fun load(context: Context, glanceId: GlanceId, kind: String): WidgetSnap {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        return loadId(context, id, kind)
    }

    suspend fun loadId(context: Context, id: Int, kind: String): WidgetSnap {
        val graph = (context.applicationContext as MoonBagsApp).graph
        val settings = graph.settings.flow.first()
        val config = graph.db.widgets().get(id) ?: WidgetConfigEntity(
            appWidgetId = id,
            kind = kind,
            coinId = "bitcoin",
            showPrice = kind != WidgetKinds.TILE && kind != WidgetKinds.MCAP,
            backgroundPath = null,
            scrim = settings.scrim,
            hidden = false,
            alpha = 0.7f,
        )
        val live = runCatching { graph.market.liveGlobal() }.getOrNull()
        val storedGlobal = graph.db.market().global(settings.currency)
        val global = if (live != null) {
            val fresh = com.moonbags.app.data.local.GlobalEntity(settings.currency, live.first, live.second, live.third, System.currentTimeMillis())
            runCatching { graph.db.market().upsertGlobal(fresh) }
            fresh
        } else storedGlobal
        val stored = graph.db.market().quotes().filter { it.currency == settings.currency }
        val spots = runCatching { graph.market.freshCmc(settings.currency, stored.map { it.symbol to it.coinId }) }.getOrDefault(emptyMap())
        val quotes = stored.map { quote ->
            val spot = spots[quote.symbol.uppercase()] ?: return@map quote
            if (spot.first <= 0.0) quote else quote.copy(
                price = spot.first,
                change24h = spot.second,
                marketCap = if (spot.third > 0.0) spot.third else quote.marketCap,
            )
        }
        val holdings = graph.db.holdings().all()
        val raw = graph.media.decodeForWidget(config.backgroundPath, 640)
        val tint = runCatching { android.graphics.Color.parseColor(settings.widgetBorder) }.getOrDefault(0xFF3A3842.toInt())
        val background = if (raw == null || settings.widgetAlpha <= 0.004f) null else widgetPlate(raw, tint, settings.widgetAlpha, settings.widgetTint)
        return render(config, settings.currency, settings.hideBalances, global, quotes, holdings, background)
            .copy(
                alpha = settings.widgetAlpha,
                tapPackage = settings.tapPackage,
                acrylic = config.acrylic,
                acrylicHue = config.acrylicHue,
                acrylicSat = config.acrylicSat,
                acrylicLight = config.acrylicLight,
                border = runCatching { Color(android.graphics.Color.parseColor(settings.widgetBorder)) }.getOrDefault(Color(0xFFD0BCFF)),
                fill = runCatching { Color(android.graphics.Color.parseColor(settings.widgetFill)) }.getOrDefault(Color.Transparent),
                refreshNote = readRefreshNote(context),
                cornerDp = settings.radiusDp.coerceIn(0, 48),
            )
    }
}

fun render(
    config: WidgetConfigEntity,
    currency: String,
    hideAll: Boolean,
    global: GlobalEntity?,
    quotes: List<QuoteEntity>,
    holdings: List<HoldingEntity>,
    background: Bitmap?,
): WidgetSnap {
    val coin = quotes.find { it.coinId == config.coinId } ?: quotes.firstOrNull()
    val hidden = config.kind == WidgetKinds.BOOK && (config.hidden || hideAll)
    fun money(value: Double) = if (hidden) "••••" else CompactMoney.format(value, currency)
    return when (config.kind) {
        WidgetKinds.TILE -> WidgetSnap(
            primary = if (config.showPrice && !config.coinId.startsWith("cmc-")) cmcSpot(coin?.price ?: 0.0, currency) else money(coin?.marketCap ?: 0.0),
            secondary = coin?.symbol ?: "",
            up = (coin?.change24h ?: 0.0) >= 0,
            title = if (config.coinId.startsWith("cmc-")) coin?.name ?: "Meme" else coin?.symbol ?: "COIN",
            lines = emptyList(),
            spark = if (config.coinId.startsWith("cmc-")) parseSpark(coin?.sparkline) else emptyList(),
            background = background,
            scrim = config.scrim,
            hidden = hidden,
        )
        WidgetKinds.PILL, WidgetKinds.SPARK, WidgetKinds.SPARK_BAR -> WidgetSnap(
            primary = cmcSpot(coin?.price ?: 0.0, currency),
            secondary = CompactMoney.percent(coin?.change24h ?: 0.0),
            up = (coin?.change24h ?: 0.0) >= 0,
            title = coin?.symbol ?: "COIN",
            lines = emptyList(),
            spark = parseSpark(coin?.sparkline),
            background = background,
            scrim = config.scrim,
            hidden = hidden,
        )
        WidgetKinds.WATCH -> WidgetSnap(
            primary = "",
            secondary = "",
            up = true,
            title = "Watchlist",
            lines = quotes.take(8).map { it.symbol to cmcSpot(it.price, currency) },
            spark = emptyList(),
            background = background,
            scrim = config.scrim,
            hidden = hidden,
        )
        WidgetKinds.BOOK -> {
            val valued = holdings.map { holding ->
                val price = quotes.find { it.coinId == holding.coinId }?.price ?: 0.0
                holding.symbol to holding.amount * price
            }.sortedByDescending { it.second }
            WidgetSnap(
                primary = money(valued.sumOf { it.second }),
                secondary = "net worth",
                up = true,
                title = "Moon bags",
                lines = valued.take(3).map { it.first to money(it.second) },
                spark = emptyList(),
                background = background,
                scrim = config.scrim,
                hidden = hidden,
            )
        }
        else -> WidgetSnap(
            primary = CompactMoney.cap(global?.marketCap ?: 0.0, currency),
            secondary = CompactMoney.percent(global?.change24h ?: 0.0),
            up = (global?.change24h ?: 0.0) >= 0,
            title = "Total market cap",
            lines = emptyList(),
            spark = parseSpark(quotes.find { it.coinId == "bitcoin" }?.sparkline ?: coin?.sparkline),
            background = background,
            scrim = config.scrim,
            hidden = hidden,
            alpha = config.alpha,
        )
    }
}

private fun parseSpark(raw: String?): List<Double> {
    if (raw.isNullOrBlank()) return emptyList()
    val all = raw.split(",").mapNotNull { it.toDoubleOrNull() }
    if (all.size <= 48) return all
    val step = all.size / 48
    return all.filterIndexed { index, _ -> index % step == 0 }
}

private fun cmcSpot(value: Double, currency: String): String {
    if (value.isNaN() || value.isInfinite() || value == 0.0) return "—"
    val symbol = CompactMoney.symbol(currency)
    val abs = kotlin.math.abs(value)
    val text = when {
        currency.equals("jpy", true) && abs >= 1.0 -> String.format(java.util.Locale.US, "%,.0f", abs)
        abs >= 1.0 -> String.format(java.util.Locale.US, "%,.2f", abs)
        abs >= 0.01 -> String.format(java.util.Locale.US, "%.4f", abs)
        abs >= 0.0001 -> String.format(java.util.Locale.US, "%.6f", abs)
        else -> String.format(java.util.Locale.US, "%.8f", abs).trimEnd('0').trimEnd('.')
    }
    return (if (value < 0) "-" else "") + symbol + text
}

fun frameBitmap(width: Int, height: Int, color: Int, stroke: Float, radius: Float): Bitmap {
    val out = Bitmap.createBitmap(width.coerceAtLeast(8), height.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val inset = stroke / 2f
    val rect = RectF(inset, inset, out.width - inset, out.height - inset)
    val corner = (radius - inset).coerceAtLeast(0f).coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
    canvas.drawRoundRect(rect, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        this.color = color
        strokeJoin = Paint.Join.ROUND
    })
    canvas.drawRect(0f, out.height - stroke / 2f, out.width.toFloat(), out.height.toFloat(), Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    })
    return out.freeze()
}

private fun acrylicColor(hue: Float, sat: Float, light: Float, alpha: Float): Color {
    val argb = android.graphics.Color.HSVToColor(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        floatArrayOf(hue, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f)),
    )
    return Color(argb)
}

fun widgetPlate(image: Bitmap, tint: Int, alpha: Float, tintOn: Boolean): Bitmap {
    val width = 320
    val height = 180
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val strength = alpha.coerceIn(0f, 1f)
    val scale = maxOf(width / image.width.toFloat(), height / image.height.toFloat())
    val drawnWidth = image.width * scale
    val drawnHeight = image.height * scale
    val left = (width - drawnWidth) / 2f
    val top = (height - drawnHeight) / 2f
    canvas.drawBitmap(
        image,
        null,
        android.graphics.RectF(left, top, left + drawnWidth, top + drawnHeight),
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (strength * 255).toInt() },
    )
    if (tintOn) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            color = tint
            this.alpha = (strength * 0.45f * 255).toInt()
        })
    }
    return out.freeze()
}

private fun Bitmap.freeze(): Bitmap {
    val software = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    return if (software.isMutable) software.copy(Bitmap.Config.ARGB_8888, false) else software
}

fun sparkBitmap(values: List<Double>, up: Boolean, width: Int = 280, height: Int = 120, strokePx: Float = 3.5f): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(8), height.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
    if (values.size < 2) return bitmap.freeze()
    val min = values.min()
    val max = values.max().let { if (it == min) it + 1 else it }
    val color = if (up) 0xFF3DDC84.toInt() else 0xFFFF4D4D.toInt()
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val pad = strokePx + 1f
    val innerW = (bitmap.width - pad * 2).coerceAtLeast(1f)
    val innerH = (bitmap.height - pad * 2).coerceAtLeast(1f)
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = pad + index / (values.size - 1f) * innerW
        val y = pad + ((1f - ((value - min) / (max - min)).toFloat()) * innerH)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    Canvas(bitmap).drawPath(path, stroke)
    return bitmap.freeze()
}

private fun clipToWidget(source: Bitmap, width: Int, height: Int, radius: Float): Bitmap {
    val out = Bitmap.createBitmap(width.coerceAtLeast(8), height.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val path = Path().apply {
        addRoundRect(RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), radius, radius, Path.Direction.CW)
    }
    canvas.clipPath(path)
    canvas.drawBitmap(source, null, RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG))
    return out.freeze()
}

@Composable
private fun Chrome(snap: WidgetSnap, eye: Boolean = false, showTitle: Boolean = true, tapBody: Boolean = true, clipBackground: Boolean = false, contentPadding: androidx.compose.ui.unit.Dp = 16.dp, showRefresh: Boolean = true, body: @Composable ColumnScope.() -> Unit) {
    val context = LocalContext.current
    val open = launchAction(context, snap.tapPackage)
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val cornerDp = snap.cornerDp.coerceIn(0, 48)
    val cornerPx = cornerDp * density
    val widthPx = (size.width.value * density).toInt().coerceAtLeast(8)
    val heightPx = (size.height.value * density).toInt().coerceAtLeast(8)
    val frame = frameBitmap(
        widthPx,
        heightPx,
        android.graphics.Color.argb((snap.border.alpha * 255).toInt().coerceIn(0, 255), (snap.border.red * 255).toInt(), (snap.border.green * 255).toInt(), (snap.border.blue * 255).toInt()),
        1f,
        cornerPx,
    )
    val plate = snap.background?.let { raw -> clipToWidget(raw, widthPx, heightPx, cornerPx) }
    Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(cornerDp.dp)) {
        if (plate != null) {
            Image(provider = ImageProvider(plate), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } else if (snap.acrylic && snap.alpha > 0.004f) {
            Box(modifier = GlanceModifier.fillMaxSize().background(ColorProvider(acrylicColor(snap.acrylicHue, snap.acrylicSat, snap.acrylicLight, snap.alpha)))) {}
        } else if (snap.alpha > 0.004f && snap.fill.alpha > 0.004f) {
            Box(modifier = GlanceModifier.fillMaxSize().background(ColorProvider(snap.fill.copy(alpha = snap.alpha)))) {}
        }
        Image(provider = ImageProvider(frame), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(modifier = GlanceModifier.fillMaxSize().padding(contentPadding)) {
            if (showTitle) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (snap.title.isNotBlank()) {
                        Text(
                            snap.title,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = ColorProvider(White), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    if (eye) {
                        Image(
                            provider = ImageProvider(if (snap.hidden) R.drawable.ic_eye_off else R.drawable.ic_eye),
                            contentDescription = "Toggle privacy",
                            modifier = GlanceModifier.size(18.dp).clickable(privacyClick()),
                        )
                    }
                }
            }
            OpenBody(if (tapBody) open else null, body)
            if (showRefresh) RefreshFoot(snap.refreshNote)
        }
    }
}

private fun launchAction(context: Context, pkg: String): Action? {
    if (pkg.isBlank()) return null
    val intent = context.packageManager.getLaunchIntentForPackage(pkg.trim()) ?: return null
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return actionStartActivity(intent)
}

@Composable
private fun ColumnScope.OpenBody(open: Action?, body: @Composable ColumnScope.() -> Unit) {
    val modifier = GlanceModifier.fillMaxWidth().defaultWeight()
    if (open == null) Column(modifier) { body() } else Column(modifier.clickable(open)) { body() }
}

private fun refreshClick() = actionRunCallback<RefreshAction>()
private fun privacyClick() = actionRunCallback<TogglePrivacyAction>()

@Composable
private fun RefreshFoot(note: String, wide: Boolean = true) {
    Row(
        modifier = if (wide) GlanceModifier.fillMaxWidth() else GlanceModifier,
        horizontalAlignment = Alignment.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (note.isNotBlank()) {
            Text(note, style = TextStyle(color = ColorProvider(Color(0xFFEFE8DC).copy(alpha = 0.65f)), fontSize = 10.sp))
        }
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = if (note.isBlank()) "Refresh" else note,
            modifier = GlanceModifier.size(24.dp).background(ColorProvider(Color.Transparent)).clickable(refreshClick()),
            colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Color(0xFFE8E6E6).copy(alpha = 0.65f))),
        )
    }
}

class TotalMarketCapWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.MCAP)
        provideContent {
            val density = LocalContext.current.resources.displayMetrics.density
            val open = launchAction(LocalContext.current, snap.tapPackage)
            val chartWidth = 96.dp
            val chart = sparkBitmap(snap.spark, snap.up, (chartWidth.value * density).toInt(), (22f * density).toInt(), 2f)
            Chrome(snap, showTitle = false, tapBody = false, showRefresh = false, contentPadding = 8.dp) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    val textMod = if (open != null) GlanceModifier.fillMaxWidth().clickable(open) else GlanceModifier.fillMaxWidth()
                    Text(
                        snap.primary,
                        modifier = textMod,
                        style = TextStyle(color = ColorProvider(Paper), fontSize = 14.4.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        snap.secondary + " 24h",
                        modifier = if (open != null) GlanceModifier.clickable(open) else GlanceModifier,
                        style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = 10.4.sp),
                    )
                    if (snap.spark.size > 1) {
                        val chartMod = GlanceModifier.width(chartWidth).height(16.dp).padding(top = 2.dp)
                        Image(
                            provider = ImageProvider(chart),
                            contentDescription = "Market trend",
                            modifier = if (open != null) chartMod.clickable(open) else chartMod,
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        if (snap.refreshNote.isNotBlank()) {
                            Text(snap.refreshNote, style = TextStyle(color = ColorProvider(Color(0xFFEFE8DC).copy(alpha = 0.65f)), fontSize = 8.sp))
                        }
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = if (snap.refreshNote.isBlank()) "Refresh" else snap.refreshNote,
                            modifier = GlanceModifier.size(14.dp).background(ColorProvider(Color.Transparent)).clickable(refreshClick()),
                            colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(Color(0xFFE8E6E6).copy(alpha = 0.65f))),
                        )
                    }
                }
            }
        }
    }
}

class SingleCoinWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.TILE)
        val chart = sparkBitmap(snap.spark, snap.up, 240, 72, 2f)
        provideContent {
            val open = launchAction(LocalContext.current, snap.tapPackage)
            val size = LocalSize.current
            val tight = size.width < 220.dp || size.height < 72.dp
            val roomy = size.width >= 300.dp && size.height >= 84.dp
            val chartWidth = when {
                tight -> 72.dp
                roomy -> 132.dp
                else -> 84.dp
            }
            Chrome(snap, showTitle = false, tapBody = false, contentPadding = if (tight) 8.dp else if (roomy) 14.dp else 12.dp) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val body = GlanceModifier.defaultWeight().background(ColorProvider(Color.Transparent))
                    Column(modifier = if (open != null) body.clickable(open) else body) {
                        Text(snap.title, style = TextStyle(color = ColorProvider(Paper), fontSize = if (tight) 11.sp else if (roomy) 18.sp else 13.sp, fontWeight = FontWeight.Bold))
                        Text(snap.primary, style = TextStyle(color = ColorProvider(White), fontSize = if (roomy) 16.sp else 12.sp))
                    }
                    if (snap.spark.size > 1) {
                        val chartMod = GlanceModifier.width(chartWidth).height(if (roomy) 44.dp else 32.dp).background(ColorProvider(Color.Transparent))
                        Image(
                            provider = ImageProvider(chart),
                            contentDescription = "Trend",
                            modifier = if (open != null) chartMod.clickable(open) else chartMod,
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
            }
        }
    }
}

class PillWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.PILL)
        provideContent {
            val open = launchAction(LocalContext.current, snap.tapPackage)
            Chrome(snap, showTitle = false, tapBody = false, showRefresh = false, contentPadding = 8.dp) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val body = GlanceModifier.defaultWeight()
                    Column(modifier = if (open != null) body.clickable(open) else body) {
                        Text(snap.title, style = TextStyle(color = ColorProvider(White), fontSize = 8.8.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text(snap.primary, style = TextStyle(color = ColorProvider(Paper), fontSize = 14.4.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Text(snap.secondary, style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = 10.4.sp), maxLines = 1)
                    RefreshFoot(snap.refreshNote, wide = false)
                }
            }
        }
    }
}

class SparklineWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.SPARK)
        val chart = sparkBitmap(snap.spark, snap.up)
        provideContent {
            val height = LocalSize.current.height
            val nameSize = when {
                height < 140.dp -> 18.sp
                height > 260.dp -> 34.sp
                else -> 22.sp
            }
            Chrome(snap, clipBackground = true) {
                Text(snap.primary, style = TextStyle(color = ColorProvider(Paper), fontSize = nameSize, fontWeight = FontWeight.Bold))
                Text(snap.secondary, style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = if (height > 260.dp) 16.sp else 12.sp))
                Image(provider = ImageProvider(chart), contentDescription = "7 day trend", modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentScale = ContentScale.FillBounds)
            }
        }
    }
}

class SparklineBarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.SPARK_BAR)
        val chart = sparkBitmap(snap.spark, snap.up, 280, 64, 2f)
        provideContent {
            val open = launchAction(LocalContext.current, snap.tapPackage)
            Chrome(snap, showTitle = false, tapBody = false, showRefresh = false, clipBackground = true, contentPadding = 8.dp) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val body = GlanceModifier.defaultWeight()
                    Column(modifier = if (open != null) body.clickable(open) else body) {
                        Text(snap.title, style = TextStyle(color = ColorProvider(White), fontSize = 8.8.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text(snap.primary, style = TextStyle(color = ColorProvider(Paper), fontSize = 14.4.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    val chartMod = GlanceModifier.width(88.dp).height(24.dp)
                    Image(
                        provider = ImageProvider(chart),
                        contentDescription = "7 day trend",
                        modifier = if (open != null) chartMod.clickable(open) else chartMod,
                        contentScale = ContentScale.FillBounds,
                    )
                    Text(snap.secondary, style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = 10.4.sp), maxLines = 1)
                    RefreshFoot(snap.refreshNote, wide = false)
                }
            }
        }
    }
}

class WatchlistWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.WATCH)
        provideContent {
            Chrome(snap) {
                LazyColumn {
                    items(snap.lines, itemId = { it.first.hashCode().toLong() }) { line ->
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(2.dp)) {
                            Text(line.first, style = TextStyle(color = ColorProvider(Paper), fontSize = 13.sp))
                            Text(line.second, style = TextStyle(color = ColorProvider(White), fontSize = 13.sp, textAlign = TextAlign.End))
                        }
                    }
                }
            }
        }
    }
}

class PortfolioWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.BOOK)
        provideContent {
            Chrome(snap, eye = true) {
                Text(snap.primary, style = TextStyle(color = ColorProvider(Paper), fontSize = 26.sp, fontWeight = FontWeight.Bold))
                snap.lines.forEach { line ->
                    Text("${line.first}  ${line.second}", style = TextStyle(color = ColorProvider(Paper), fontSize = 12.sp))
                }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.widgetPulse(done = false)
        writeRefresh(context, "busy", 0L)
        try {
            val graph = (context.applicationContext as MoonBagsApp).graph
            val settings = graph.settings.flow.first()
            val ids = settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            graph.market.refresh(settings.currency, ids)
            writeRefresh(context, "ok", System.currentTimeMillis())
        } catch (_: Exception) {
            writeRefresh(context, "fail", System.currentTimeMillis())
        }
        context.widgetPulse(done = true)
        updateAllWidgets(context)
    }
}

private fun Context.widgetPulse(done: Boolean) {
    runCatching {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val effect = if (done) android.os.VibrationEffect.EFFECT_TICK else android.os.VibrationEffect.EFFECT_CLICK
            vibrator.vibrate(android.os.VibrationEffect.createPredefined(effect))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (done) 12 else 18)
        }
    }
}

private const val REFRESH_PREFS = "widget_refresh"

private fun writeRefresh(context: Context, status: String, at: Long) {
    context.getSharedPreferences(REFRESH_PREFS, Context.MODE_PRIVATE).edit().putString("status", status).putLong("at", at).apply()
}

private fun readRefreshNote(context: Context): String {
    val prefs = context.getSharedPreferences(REFRESH_PREFS, Context.MODE_PRIVATE)
    return when (prefs.getString("status", "idle")) {
        "busy" -> "Refreshing"
        "fail" -> "Failed"
        "ok" -> {
            val at = prefs.getLong("at", 0L)
            if (at == 0L) "" else "Updated " + android.text.format.DateFormat.format("h:mm", at)
        }
        else -> ""
    }
}

class TogglePrivacyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        MoonDatabase.get(context).widgets().toggleHidden(id)
        PortfolioWidget().update(context, glanceId)
    }
}

suspend fun updateWidget(context: Context, kind: String, appWidgetId: Int) {
    val themed = context.widgetThemed()
    val widget = widgetFor(kind)
    val manager = GlanceAppWidgetManager(themed)
    val match = manager.getGlanceIds(widget.javaClass).firstOrNull { manager.getAppWidgetId(it) == appWidgetId }
    if (match != null) widget.update(themed, match) else widget.updateAll(themed)
}

suspend fun updateAllWidgets(context: Context) {
    val themed = context.widgetThemed()
    listOf(WidgetKinds.MCAP, WidgetKinds.TILE, WidgetKinds.PILL, WidgetKinds.SPARK, WidgetKinds.SPARK_BAR, WidgetKinds.WATCH, WidgetKinds.BOOK)
        .forEach { kind ->
            runCatching { widgetFor(kind).updateAll(themed) }
        }
}

private fun Context.widgetThemed(): Context =
    android.view.ContextThemeWrapper(this, com.moonbags.app.R.style.Theme_MoonBags_Widget)

fun widgetFor(kind: String): GlanceAppWidget = when (kind) {
    WidgetKinds.TILE -> SingleCoinWidget()
    WidgetKinds.PILL -> PillWidget()
    WidgetKinds.SPARK -> SparklineWidget()
    WidgetKinds.SPARK_BAR -> SparklineBarWidget()
    WidgetKinds.WATCH -> WatchlistWidget()
    WidgetKinds.BOOK -> PortfolioWidget()
    else -> TotalMarketCapWidget()
}

open class MoonWidgetReceiver(private val widget: GlanceAppWidget) : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = widget

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context.widgetThemed(), intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { MoonDatabase.get(context).widgets().delete(it) }
            } finally {
                pending.finish()
            }
        }
    }
}

class TotalMarketCapReceiver : MoonWidgetReceiver(TotalMarketCapWidget())
class SingleCoinReceiver : MoonWidgetReceiver(SingleCoinWidget())
class PillReceiver : MoonWidgetReceiver(PillWidget())
class SparklineReceiver : MoonWidgetReceiver(SparklineWidget())
class SparklineBarReceiver : MoonWidgetReceiver(SparklineBarWidget())
class WatchlistReceiver : MoonWidgetReceiver(WatchlistWidget())
class PortfolioReceiver : MoonWidgetReceiver(PortfolioWidget())


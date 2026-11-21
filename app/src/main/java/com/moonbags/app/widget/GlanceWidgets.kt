package com.moonbags.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moonbags.app.MoonBagsApp
import com.moonbags.app.R
import com.moonbags.app.data.local.GlobalEntity
import com.moonbags.app.data.local.HoldingEntity
import com.moonbags.app.data.local.MoonDatabase
import com.moonbags.app.data.local.QuoteEntity
import com.moonbags.app.data.local.WidgetConfigEntity
import com.moonbags.app.domain.CompactMoney
import com.moonbags.app.work.PriceSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object WidgetKinds {
    const val MCAP = "mcap"
    const val TILE = "tile"
    const val PILL = "pill"
    const val SPARK = "spark"
    const val WATCH = "watch"
    const val BOOK = "book"

    fun fromProvider(className: String): String = when {
        className.endsWith("SingleCoinReceiver") -> TILE
        className.endsWith("PillReceiver") -> PILL
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
)

private val Paper = Color(0xFFEFE8DC)
private val Brass = Color(0xFFD4A24A)
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
        val global = graph.db.market().global(settings.currency)
        val quotes = graph.db.market().quotes().filter { it.currency == settings.currency }
        val holdings = graph.db.holdings().all()
        val raw = graph.media.decodeForWidget(config.backgroundPath, 640)
        val tint = runCatching { android.graphics.Color.parseColor(settings.widgetBorder) }.getOrDefault(0xFF3A3842.toInt())
        val background = widgetPlate(raw, tint, settings.widgetAlpha, settings.widgetTint)
        return render(config, settings.currency, settings.hideBalances, global, quotes, holdings, background)
            .copy(
                alpha = settings.widgetAlpha,
                tapPackage = settings.tapPackage,
                acrylic = config.acrylic,
                acrylicHue = config.acrylicHue,
                acrylicSat = config.acrylicSat,
                acrylicLight = config.acrylicLight,
                border = runCatching { Color(android.graphics.Color.parseColor(settings.widgetBorder)) }.getOrDefault(Color(0xFFD0BCFF)),
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
            primary = if (config.showPrice && !config.coinId.startsWith("cmc-")) CompactMoney.price(coin?.price ?: 0.0, currency) else money(coin?.marketCap ?: 0.0),
            secondary = coin?.symbol ?: "",
            up = (coin?.change24h ?: 0.0) >= 0,
            title = if (config.coinId.startsWith("cmc-")) coin?.name ?: "Meme" else coin?.symbol ?: "COIN",
            lines = emptyList(),
            spark = if (config.coinId.startsWith("cmc-")) parseSpark(coin?.sparkline) else emptyList(),
            background = background,
            scrim = config.scrim,
            hidden = hidden,
        )
        WidgetKinds.PILL, WidgetKinds.SPARK -> WidgetSnap(
            primary = CompactMoney.price(coin?.price ?: 0.0, currency),
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
            lines = quotes.take(8).map { it.symbol to CompactMoney.price(it.price, currency) },
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

fun frameBitmap(width: Int, height: Int, color: Int, stroke: Float, radius: Float): Bitmap {
    val out = Bitmap.createBitmap(width.coerceAtLeast(8), height.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val inset = stroke / 2f + 4f
    val rect = RectF(inset, inset, out.width - inset, out.height - inset)
    val corner = radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
    canvas.drawRoundRect(rect, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        this.color = color
        strokeJoin = Paint.Join.ROUND
    })
    return out.freeze()
}

fun widgetPlate(image: Bitmap?, tint: Int, alpha: Float, tintOn: Boolean): Bitmap {
    val width = 320
    val height = 180
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val radius = 16f
    val bounds = android.graphics.RectF(2f, 2f, width - 2f, height - 2f)
    val clip = Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) }
    val strength = alpha.coerceIn(0f, 1f)
    canvas.save()
    canvas.clipPath(clip)
    if (image != null) {
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
                this.alpha = (strength * 0.5f * 255).toInt()
            })
        }
    } else {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (tintOn) tint else 0xFF3A3842.toInt()
            this.alpha = (strength * 255).toInt()
        })
    }
    canvas.restore()
    return out.freeze()
}

private fun Bitmap.freeze(): Bitmap {
    val software = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    return if (software.isMutable) software.copy(Bitmap.Config.ARGB_8888, false) else software
}

fun sparkBitmap(values: List<Double>, up: Boolean): Bitmap {
    val width = 280
    val height = 120
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (values.size < 2) return bitmap
    val min = values.min()
    val max = values.max().let { if (it == min) it + 1 else it }
    val color = if (up) 0xFF3DDC84.toInt() else 0xFFFF4D4D.toInt()
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = index / (values.size - 1f) * width
        val y = ((1f - ((value - min) / (max - min)).toFloat()) * (height - 8f)) + 4f
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val canvas = Canvas(bitmap)
    canvas.drawPath(path, stroke)
    return bitmap.freeze()
}

@Composable
private fun Chrome(snap: WidgetSnap, eye: Boolean = false, showTitle: Boolean = true, tapBody: Boolean = true, body: @Composable ColumnScope.() -> Unit) {
    val context = LocalContext.current
    val open = launchAction(context, snap.tapPackage)
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val frame = frameBitmap(
        (size.width.value * density).toInt(),
        (size.height.value * density).toInt(),
        android.graphics.Color.argb((snap.border.alpha * 255).toInt().coerceIn(0, 255), (snap.border.red * 255).toInt(), (snap.border.green * 255).toInt(), (snap.border.blue * 255).toInt()),
        2.5f * density,
        22f * density,
    )
    val plate = snap.background?.freeze()
    Box(modifier = GlanceModifier.fillMaxSize()) {
        val fill = if (plate != null) {
            GlanceModifier.fillMaxSize().background(ImageProvider(plate), ContentScale.FillBounds)
        } else {
            GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF3A3842).copy(alpha = snap.alpha.coerceIn(0.15f, 1f))))
        }
        Box(modifier = fill) {}
        Image(provider = ImageProvider(frame), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
            if (showTitle) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (snap.title.isNotBlank()) {
                        Text(
                            snap.title,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = ColorProvider(Brass), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    if (eye) {
                        Image(
                            provider = ImageProvider(if (snap.hidden) R.drawable.ic_eye_off else R.drawable.ic_eye),
                            contentDescription = "Toggle privacy",
                            modifier = GlanceModifier.size(18.dp).clickable(privacyClick()),
                        )
                    }
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier.size(28.dp).clickable(refreshClick()),
                    )
                }
            }
            OpenBody(if (tapBody) open else null, body)
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

class TotalMarketCapWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.MCAP)
        val chart = sparkBitmap(snap.spark, snap.up)
        provideContent {
            val height = LocalSize.current.height
            val open = launchAction(LocalContext.current, snap.tapPackage)
            Chrome(snap, showTitle = false, tapBody = false) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            snap.primary,
                            modifier = if (open != null) GlanceModifier.defaultWeight().clickable(open) else GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = ColorProvider(Paper),
                                fontSize = when {
                                    height < 80.dp -> 18.sp
                                    height < 150.dp -> 28.sp
                                    else -> 40.sp
                                },
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = "Refresh",
                            modifier = GlanceModifier.size(28.dp).clickable(refreshClick()),
                        )
                    }
                    Text(
                        snap.secondary + " 24h",
                        modifier = if (open != null) GlanceModifier.clickable(open) else GlanceModifier,
                        style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = 13.sp),
                    )
                }
                if (snap.spark.size > 1) {
                    val chartMod = GlanceModifier.fillMaxWidth().defaultWeight()
                    Image(
                        provider = ImageProvider(chart),
                        contentDescription = "Market trend",
                        modifier = if (open != null) chartMod.clickable(open) else chartMod,
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
    }
}

class SingleCoinWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.TILE)
        val chart = sparkBitmap(snap.spark, snap.up)
        provideContent {
            Chrome(snap) {
                Text(snap.title, style = TextStyle(color = ColorProvider(Paper), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                if (snap.spark.size > 1) {
                    Image(
                        provider = ImageProvider(chart),
                        contentDescription = "Trend",
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
                Text(snap.primary, style = TextStyle(color = ColorProvider(Brass), fontSize = 12.sp))
            }
        }
    }
}

class PillWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.PILL)
        provideContent {
            Chrome(snap) {
                Text(snap.primary, style = TextStyle(color = ColorProvider(Paper), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text(snap.secondary, style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = 13.sp))
            }
        }
    }
}

class SparklineWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.SPARK)
        val chart = sparkBitmap(snap.spark, snap.up)
        provideContent {
            Chrome(snap) {
                Text(snap.primary, style = TextStyle(color = ColorProvider(Paper), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text(snap.secondary, style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = 12.sp))
                Image(provider = ImageProvider(chart), contentDescription = "7 day trend", modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentScale = ContentScale.FillBounds)
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
                            Text(line.second, style = TextStyle(color = ColorProvider(Brass), fontSize = 13.sp, textAlign = TextAlign.End))
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
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<PriceSyncWorker>().build())
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
    val widget = widgetFor(kind)
    val manager = GlanceAppWidgetManager(context)
    val match = manager.getGlanceIds(widget.javaClass).firstOrNull { manager.getAppWidgetId(it) == appWidgetId }
    if (match != null) widget.update(context, match) else widget.updateAll(context)
}

suspend fun updateAllWidgets(context: Context) {
    listOf(WidgetKinds.MCAP, WidgetKinds.TILE, WidgetKinds.PILL, WidgetKinds.SPARK, WidgetKinds.WATCH, WidgetKinds.BOOK)
        .forEach { kind ->
            runCatching { widgetFor(kind).updateAll(context) }
        }
}

fun widgetFor(kind: String): GlanceAppWidget = when (kind) {
    WidgetKinds.TILE -> SingleCoinWidget()
    WidgetKinds.PILL -> PillWidget()
    WidgetKinds.SPARK -> SparklineWidget()
    WidgetKinds.WATCH -> WatchlistWidget()
    WidgetKinds.BOOK -> PortfolioWidget()
    else -> TotalMarketCapWidget()
}

open class MoonWidgetReceiver(private val widget: GlanceAppWidget) : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = widget

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
class WatchlistReceiver : MoonWidgetReceiver(WatchlistWidget())
class PortfolioReceiver : MoonWidgetReceiver(PortfolioWidget())


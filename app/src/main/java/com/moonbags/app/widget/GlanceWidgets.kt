package com.moonbags.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
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
)

private val Paper = Color(0xFFEFE8DC)
private val Brass = Color(0xFFD4A24A)
private val Ink = Color(0xFF0C1016)
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
        val background = fadeBitmap(graph.media.decodeForWidget(config.backgroundPath), config.alpha)
        return render(config, settings.currency, settings.hideBalances, global, quotes, holdings, background)
            .copy(alpha = config.alpha, tapPackage = settings.tapPackage)
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
            primary = if (config.showPrice && !config.coinId.startsWith("cmc-")) money(coin?.price ?: 0.0) else money(coin?.marketCap ?: 0.0),
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
            primary = money(coin?.price ?: 0.0),
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
            lines = quotes.take(8).map { it.symbol to CompactMoney.format(it.price, currency) },
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
            primary = CompactMoney.format(global?.marketCap ?: 0.0, currency),
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

fun fadeBitmap(source: Bitmap?, alpha: Float): Bitmap? {
    if (source == null) return null
    val faded = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(faded)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (alpha.coerceIn(0.15f, 1f) * 255).toInt() }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return faded
}

fun sparkBitmap(values: List<Double>, up: Boolean): Bitmap {
    val width = 900
    val height = 360
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (values.size < 2) return bitmap
    val min = values.min()
    val max = values.max().let { if (it == min) it + 1 else it }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (up) 0xFF3DDC84.toInt() else 0xFFFF4D4D.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = 2f + index / (values.size - 1f) * (width - 4f)
        val y = 4f + (1f - ((value - min) / (max - min)).toFloat()) * (height - 8f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    Canvas(bitmap).drawPath(path, paint)
    return bitmap
}

@Composable
private fun Chrome(snap: WidgetSnap, eye: Boolean = false, body: @Composable () -> Unit) {
    val context = LocalContext.current
    val open = if (snap.tapPackage.isBlank()) {
        null
    } else {
        context.packageManager.getLaunchIntentForPackage(snap.tapPackage.trim())?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            actionStartActivity(intent)
        }
    }
    val veil = Ink.copy(alpha = snap.alpha.coerceIn(0.15f, 1f))
    Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(22.dp).background(ColorProvider(veil))) {
        if (snap.background != null) {
            Image(
                provider = ImageProvider(snap.background),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color.Black.copy(alpha = snap.scrim.coerceIn(0f, 0.9f) * snap.alpha))),
            ) {}
        }
        Column(modifier = GlanceModifier.fillMaxSize().padding(10.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snap.title,
                    style = TextStyle(color = ColorProvider(Brass), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
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
                    modifier = GlanceModifier.size(18.dp).clickable(refreshClick()),
                )
            }
            OpenBody(open, body)
        }
    }
}

@Composable
private fun OpenBody(open: Action?, body: @Composable () -> Unit) {
    if (open == null) {
        body()
    } else {
        Box(modifier = GlanceModifier.fillMaxSize().clickable(open)) { body() }
    }
}

private fun refreshClick() = actionRunCallback<RefreshAction>()
private fun privacyClick() = actionRunCallback<TogglePrivacyAction>()

class TotalMarketCapWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 40.dp), DpSize(180.dp, 120.dp), DpSize(280.dp, 220.dp), DpSize(360.dp, 360.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshots.load(context, id, WidgetKinds.MCAP)
        val chart = sparkBitmap(snap.spark, snap.up)
        provideContent {
            val height = LocalSize.current.height
            Chrome(snap) {
                Text(
                    snap.primary,
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
                Text(
                    snap.secondary + " 24h",
                    style = TextStyle(color = ColorProvider(if (snap.up) UpColor else DownColor), fontSize = 13.sp),
                )
                if (height >= 90.dp && snap.spark.size > 1) {
                    val chartHeight = (height - 96.dp).coerceAtLeast(72.dp)
                    Image(
                        provider = ImageProvider(chart),
                        contentDescription = "Market trend",
                        modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
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
                        modifier = GlanceModifier.fillMaxWidth().height(40.dp),
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
                Image(provider = ImageProvider(chart), contentDescription = "7 day trend", modifier = GlanceModifier.fillMaxWidth().height(64.dp))
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
        .forEach { widgetFor(it).updateAll(context) }
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


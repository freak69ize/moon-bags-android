package com.moonbags.app.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moonbags.app.MoonBagsApp
import com.moonbags.app.widget.PillWidget
import com.moonbags.app.widget.PortfolioWidget
import com.moonbags.app.widget.SingleCoinWidget
import com.moonbags.app.widget.SparklineBarWidget
import com.moonbags.app.widget.SparklineWidget
import com.moonbags.app.widget.TotalMarketCapWidget
import com.moonbags.app.widget.WatchlistWidget
import kotlinx.coroutines.flow.first

class PriceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = (applicationContext as MoonBagsApp).graph
            val settings = graph.settings.flow.first()
            val ids = settings.watchIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            graph.market.refresh(settings.currency, ids)
            if (graph.secrets.load().let { it.hasBinance || it.hasCoinbase }) {
                runCatching { graph.market.syncExchanges() }
            }
            TotalMarketCapWidget().updateAll(applicationContext)
            SingleCoinWidget().updateAll(applicationContext)
            PillWidget().updateAll(applicationContext)
            SparklineWidget().updateAll(applicationContext)
            SparklineBarWidget().updateAll(applicationContext)
            WatchlistWidget().updateAll(applicationContext)
            PortfolioWidget().updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

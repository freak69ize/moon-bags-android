package com.moonbags.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moonbags.app.data.local.MoonDatabase
import com.moonbags.app.data.media.MediaStorageManager
import com.moonbags.app.data.repo.MarketRepository
import com.moonbags.app.data.security.SecureCredentialStore
import com.moonbags.app.data.settings.SettingsStore
import com.moonbags.app.work.PriceSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AppGraph(context: android.content.Context) {
    val db = MoonDatabase.get(context)
    val secrets = SecureCredentialStore(context)
    val media = MediaStorageManager(context)
    val settings = SettingsStore(context)
    val market = MarketRepository(db, secrets)
}

class MoonBagsApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        scheduleSync(15)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val minutes = graph.settings.flow.first().intervalMin.toLong()
            if (minutes != 15L) scheduleSync(minutes)
        }
    }

    fun scheduleSync(minutes: Long) {
        val request = PeriodicWorkRequestBuilder<PriceSyncWorker>(minutes.coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "moonbags-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

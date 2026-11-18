package com.moonbags.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "global_market")
data class GlobalEntity(
    @PrimaryKey val currency: String,
    val marketCap: Double,
    val volume: Double,
    val change24h: Double,
    val updatedAt: Long,
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey val coinId: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val marketCap: Double,
    val change24h: Double,
    val sparkline: String,
    val imageUrl: String?,
    val currency: String,
    val updatedAt: Long,
)

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey val id: String,
    val source: String,
    val symbol: String,
    val coinId: String,
    val amount: Double,
    val label: String,
    val address: String?,
)

@Entity(tableName = "widget_configs")
data class WidgetConfigEntity(
    @PrimaryKey val appWidgetId: Int,
    val kind: String,
    val coinId: String,
    val showPrice: Boolean,
    val backgroundPath: String?,
    val scrim: Float,
    val hidden: Boolean,
    val alpha: Float = 0.7f,
)

@Dao
interface MarketDao {
    @Query("SELECT * FROM global_market WHERE currency = :currency LIMIT 1")
    fun observeGlobal(currency: String): Flow<GlobalEntity?>

    @Query("SELECT * FROM global_market WHERE currency = :currency LIMIT 1")
    suspend fun global(currency: String): GlobalEntity?

    @Upsert
    suspend fun upsertGlobal(entity: GlobalEntity)

    @Query("SELECT * FROM quotes ORDER BY marketCap DESC")
    fun observeQuotes(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes")
    suspend fun quotes(): List<QuoteEntity>

    @Upsert
    suspend fun upsertQuotes(items: List<QuoteEntity>)
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY symbol")
    fun observe(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings")
    suspend fun all(): List<HoldingEntity>

    @Upsert
    suspend fun upsert(items: List<HoldingEntity>)

    @Query("DELETE FROM holdings WHERE source = :source")
    suspend fun deleteSource(source: String)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widget_configs")
    fun observe(): Flow<List<WidgetConfigEntity>>

    @Query("SELECT * FROM widget_configs WHERE appWidgetId = :id")
    suspend fun get(id: Int): WidgetConfigEntity?

    @Upsert
    suspend fun upsert(entity: WidgetConfigEntity)

    @Query("UPDATE widget_configs SET hidden = NOT hidden WHERE appWidgetId = :id")
    suspend fun toggleHidden(id: Int)

    @Query("DELETE FROM widget_configs WHERE appWidgetId = :id")
    suspend fun delete(id: Int)
}

@Database(
    entities = [GlobalEntity::class, QuoteEntity::class, HoldingEntity::class, WidgetConfigEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MoonDatabase : RoomDatabase() {
    abstract fun market(): MarketDao
    abstract fun holdings(): HoldingDao
    abstract fun widgets(): WidgetDao

    companion object {
        @Volatile private var instance: MoonDatabase? = null

        fun get(context: Context): MoonDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MoonDatabase::class.java,
                "moonbags.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}

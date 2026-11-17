package com.moonbags.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@Serializable
data class GlobalEnvelope(val data: GlobalDataDto = GlobalDataDto())

@Serializable
data class GlobalDataDto(
    @SerialName("total_market_cap") val totalMarketCap: Map<String, Double> = emptyMap(),
    @SerialName("total_volume") val totalVolume: Map<String, Double> = emptyMap(),
    @SerialName("market_cap_change_percentage_24h_usd") val change24h: Double = 0.0,
)

@Serializable
data class MarketCoinDto(
    val id: String = "",
    val symbol: String = "",
    val name: String = "",
    val image: String? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("market_cap") val marketCap: Double? = null,
    @SerialName("price_change_percentage_24h") val change24h: Double? = null,
    @SerialName("sparkline_in_7d") val sparkline: SparklineDto? = null,
)

@Serializable
data class SparklineDto(val price: List<Double> = emptyList())

@Serializable
data class SearchEnvelope(val coins: List<SearchCoinDto> = emptyList())

@Serializable
data class SearchCoinDto(
    val id: String = "",
    val symbol: String = "",
    val name: String = "",
)

@Serializable
data class CoinbaseSpotEnvelope(val data: CoinbaseSpotDto = CoinbaseSpotDto())

@Serializable
data class CoinbaseSpotDto(
    val base: String = "",
    val currency: String = "",
    val amount: String = "0",
)

@Serializable
data class ExchangeStatsDto(
    val open: String = "0",
    val last: String = "0",
)

interface CoinGeckoApi {
    @GET("api/v3/global")
    suspend fun global(): GlobalEnvelope

    @GET("api/v3/coins/markets")
    suspend fun markets(
        @Query("vs_currency") currency: String,
        @Query("ids") ids: String,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") change: String = "24h",
    ): List<MarketCoinDto>

    @GET("api/v3/search")
    suspend fun search(@Query("query") query: String): SearchEnvelope
}

interface CoinbasePublicApi {
    @GET("v2/prices/{pair}/spot")
    suspend fun spot(@Path("pair", encoded = true) pair: String): CoinbaseSpotEnvelope
}

interface CoinbaseExchangeApi {
    @GET("products/{product}/stats")
    suspend fun stats(@Path("product", encoded = true) product: String): ExchangeStatsDto
}

object MoonJson {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
}

object HttpClients {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "MoonBags/1.0 (Android)")
                    .header("Accept", "application/json")
                    .build(),
            )
        }
        .build()

    private fun retrofit(base: String): Retrofit = Retrofit.Builder()
        .baseUrl(base)
        .client(okHttp)
        .addConverterFactory(MoonJson.json.asConverterFactory("application/json".toMediaType()))
        .build()

    val gecko: CoinGeckoApi = retrofit("https://api.coingecko.com/").create(CoinGeckoApi::class.java)
    val coinbase: CoinbasePublicApi = retrofit("https://api.coinbase.com/").create(CoinbasePublicApi::class.java)
    val exchange: CoinbaseExchangeApi = retrofit("https://api.exchange.coinbase.com/").create(CoinbaseExchangeApi::class.java)
}

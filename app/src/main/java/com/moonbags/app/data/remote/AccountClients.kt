package com.moonbags.app.data.remote

import com.moonbags.app.data.security.ExchangeCredentials
import com.moonbags.app.domain.AssetIds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.math.BigInteger

data class RemoteBalance(val symbol: String, val amount: Double, val coinId: String, val source: String, val label: String)

class AccountClients {
    suspend fun coinbaseBalances(creds: ExchangeCredentials): List<RemoteBalance> {
        if (!creds.hasCoinbase) return emptyList()
        val path = "/api/v3/brokerage/accounts"
        val jwt = CoinbaseJwt.bearer(creds.coinbaseKeyName!!, creds.coinbasePem!!, "GET", "api.coinbase.com", path)
        val request = Request.Builder()
            .url("https://api.coinbase.com$path")
            .header("Authorization", "Bearer $jwt")
            .get()
            .build()
        val body = execute(request)
        val parsed = MoonJson.json.decodeFromString(CoinbaseAccountsDto.serializer(), body)
        return parsed.accounts.mapNotNull { account ->
            val amount = account.availableBalance?.value?.toDoubleOrNull() ?: 0.0
            if (amount <= 0.0) return@mapNotNull null
            val symbol = account.currency.uppercase()
            RemoteBalance(symbol, amount, AssetIds.idFor(symbol), "COINBASE", "Coinbase")
        }
    }

    suspend fun binanceBalances(creds: ExchangeCredentials): List<RemoteBalance> {
        if (!creds.hasBinance) return emptyList()
        val query = "timestamp=${System.currentTimeMillis()}&recvWindow=5000"
        val signature = HmacSigner.sign(creds.binanceSecret!!, query)
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/account?$query&signature=$signature")
            .header("X-MBX-APIKEY", creds.binanceKey!!)
            .get()
            .build()
        val body = execute(request)
        val parsed = MoonJson.json.decodeFromString(BinanceAccountDto.serializer(), body)
        return parsed.balances.mapNotNull { row ->
            val amount = (row.free.toDoubleOrNull() ?: 0.0) + (row.locked.toDoubleOrNull() ?: 0.0)
            if (amount <= 0.0) return@mapNotNull null
            val symbol = row.asset.uppercase()
            RemoteBalance(symbol, amount, AssetIds.idFor(symbol), "BINANCE", "Binance")
        }
    }

    suspend fun publicWallet(chain: String, address: String): List<RemoteBalance> {
        return if (chain == "sol") solana(address) else ethereum(address)
    }

    private fun execute(request: Request): String {
        HttpClients.okHttp.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error(text.ifBlank { "HTTP ${response.code}" })
            return text
        }
    }

    private fun ethereum(address: String): List<RemoteBalance> {
        val label = address.take(4) + "…" + address.takeLast(4)
        val balances = mutableListOf<RemoteBalance>()
        val wei = rpc("https://eth.llamarpc.com", "eth_getBalance", """["$address","latest"]""")
        val eth = hexUnits(wei, 18)
        if (eth > 0) balances += RemoteBalance("ETH", eth, "ethereum", "ETH", label)
        val usdc = erc20(address, "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", 6)
        val usdt = erc20(address, "0xdAC17F958D2ee523a2206206994597C13D831ec7", 6)
        if (usdc > 0) balances += RemoteBalance("USDC", usdc, "usd-coin", "ETH", label)
        if (usdt > 0) balances += RemoteBalance("USDT", usdt, "tether", "ETH", label)
        return balances
    }

    private fun erc20(holder: String, token: String, decimals: Int): Double {
        val data = "0x70a08231" + holder.removePrefix("0x").lowercase().padStart(64, '0')
        val hex = rpc("https://eth.llamarpc.com", "eth_call", """[{"to":"$token","data":"$data"},"latest"]""")
        return hexUnits(hex, decimals)
    }

    private fun solana(address: String): List<RemoteBalance> {
        val label = address.take(4) + "…" + address.takeLast(4)
        val balances = mutableListOf<RemoteBalance>()
        val lamports = rpcRaw("https://api.mainnet-beta.solana.com", "getBalance", """["$address"]""")
        val value = lamports.jsonObject["result"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
        if (value > 0) balances += RemoteBalance("SOL", value / 1e9, "solana", "SOL", label)
        return balances
    }

    private fun rpc(url: String, method: String, params: String): String {
        val root = rpcRaw(url, method, params)
        return root.jsonObject["result"]?.jsonPrimitive?.contentOrNull ?: error("Empty RPC result")
    }

    private fun rpcRaw(url: String, method: String, params: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), """{"jsonrpc":"2.0","id":1,"method":"$method","params":$params}"""))
            .build()
        val text = execute(request)
        return MoonJson.json.parseToJsonElement(text).jsonObject
    }

    private fun hexUnits(hex: String, decimals: Int): Double {
        val clean = hex.removePrefix("0x").ifBlank { "0" }
        val value = BigInteger(clean, 16)
        val base = BigInteger.TEN.pow(decimals)
        val whole = value.divide(base)
        val frac = value.remainder(base)
        return whole.toDouble() + frac.toDouble() / base.toDouble()
    }

    private fun String.toMediaType() = okhttp3.MediaType.get(this)
}

@Serializable
data class CoinbaseAccountsDto(val accounts: List<CoinbaseAccountDto> = emptyList())

@Serializable
data class CoinbaseAccountDto(
    val currency: String = "",
    @SerialName("available_balance") val availableBalance: CoinbaseValueDto? = null,
)

@Serializable
data class CoinbaseValueDto(val value: String = "0", val currency: String = "")

@Serializable
data class BinanceAccountDto(val balances: List<BinanceBalanceDto> = emptyList())

@Serializable
data class BinanceBalanceDto(val asset: String = "", val free: String = "0", val locked: String = "0")

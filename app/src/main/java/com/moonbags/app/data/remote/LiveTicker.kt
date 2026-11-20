package com.moonbags.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveTicker(private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()) {
    @Volatile var symbols: Set<String> = emptySet()
    private var socket: WebSocket? = null

    fun start(onTick: (symbol: String, price: Double, open: Double) -> Unit) {
        if (socket != null) return
        val request = Request.Builder().url("wss://stream.binance.com:9443/ws/!miniTicker@arr").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val array = runCatching { JSONArray(text) }.getOrNull() ?: return
                val wanted = symbols
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val pair = row.optString("s")
                    if (!pair.endsWith("USDT")) continue
                    val symbol = pair.removeSuffix("USDT")
                    if (symbol !in wanted) continue
                    val price = row.optString("c").toDoubleOrNull() ?: continue
                    val open = row.optString("o").toDoubleOrNull() ?: price
                    onTick(symbol, price, open)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                startCoinbase(onTick)
            }
        })
    }

    fun stop() {
        socket?.close(1000, "app background")
        socket = null
    }

    private fun startCoinbase(onTick: (symbol: String, price: Double, open: Double) -> Unit) {
        if (socket != null) return
        val products = symbols.map { "$it-USD" }
        if (products.isEmpty()) return
        val request = Request.Builder().url("wss://advanced-trade-ws.coinbase.com").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val body = JSONObject()
                    .put("type", "subscribe")
                    .put("channel", "ticker")
                    .put("product_ids", JSONArray(products))
                webSocket.send(body.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val root = runCatching { JSONObject(text) }.getOrNull() ?: return
                val events = root.optJSONArray("events") ?: return
                for (index in 0 until events.length()) {
                    val tickers = events.optJSONObject(index)?.optJSONArray("tickers") ?: continue
                    for (tick in 0 until tickers.length()) {
                        val row = tickers.optJSONObject(tick) ?: continue
                        val symbol = row.optString("product_id").removeSuffix("-USD")
                        if (symbol !in symbols) continue
                        val price = row.optString("price").toDoubleOrNull() ?: continue
                        onTick(symbol, price, price)
                    }
                }
            }
        })
    }
}

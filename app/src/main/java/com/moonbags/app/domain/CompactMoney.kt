package com.moonbags.app.domain

import java.util.Locale

object CompactMoney {
    private val symbols = mapOf(
        "usd" to "$",
        "eur" to "€",
        "gbp" to "£",
        "jpy" to "¥",
        "cad" to "C$",
        "aud" to "A$",
        "chf" to "CHF ",
    )

    fun symbol(currency: String): String = symbols[currency.lowercase(Locale.US)] ?: ""

    /** Formats large values as $3.42T, $845.2B, $12.4M. */
    fun format(value: Double, currency: String): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val symbol = symbol(currency)
        val sign = if (value < 0) "-" else ""
        val abs = kotlin.math.abs(value)
        val unit = when {
            abs >= 1_000_000_000_000.0 -> 1_000_000_000_000.0 to "T"
            abs >= 1_000_000_000.0 -> 1_000_000_000.0 to "B"
            abs >= 1_000_000.0 -> 1_000_000.0 to "M"
            abs >= 10_000.0 -> 1_000.0 to "K"
            else -> return sign + plain(abs, currency, symbol)
        }
        val scaled = abs / unit.first
        val digits = if (scaled >= 10.0) 1 else 2
        return sign + symbol + trim(String.format(Locale.US, "%.${digits}f", scaled)) + unit.second
    }

    /** Spot prices. digits 5 prints 0.00000, digits 4 prints 0.0000. */
    fun price(value: Double, currency: String, digits: Int = 5): String {
        if (value.isNaN() || value.isInfinite()) return "—"
        val symbol = symbol(currency)
        val sign = if (value < 0) "-" else ""
        val abs = kotlin.math.abs(value)
        val places = if (abs > 0.0 && abs < Math.pow(10.0, -digits.toDouble())) {
            kotlin.math.min(8, kotlin.math.ceil(-kotlin.math.log10(abs)).toInt() + 2)
        } else digits
        val pattern = if (abs >= 1000.0) "%,.${places}f" else "%.${places}f"
        return sign + symbol + String.format(Locale.US, pattern, abs)
    }

    /** Global cap like the CoinMarketCap app: $2.13T. */
    fun cap(value: Double, currency: String): String {
        if (value.isNaN() || value <= 0) return "—"
        val symbol = symbol(currency)
        val abs = kotlin.math.abs(value)
        return when {
            abs >= 1_000_000_000_000.0 -> symbol + floor2(abs / 1_000_000_000_000.0) + "T"
            abs >= 1_000_000_000.0 -> symbol + floor2(abs / 1_000_000_000.0) + "B"
            else -> format(value, currency)
        }
    }

    private fun floor2(scaled: Double): String {
        val cut = kotlin.math.floor(scaled * 100.0) / 100.0
        return String.format(Locale.US, "%.2f", cut)
    }

    fun percent(value: Double): String {
        if (value.isNaN()) return "—"
        val sign = if (value > 0) "+" else ""
        return sign + String.format(Locale.US, "%.2f%%", value)
    }

    private fun plain(abs: Double, currency: String, symbol: String): String {
        val digits = when {
            currency.equals("jpy", true) && abs >= 1 -> 0
            abs >= 1 -> 2
            abs >= 0.01 -> 4
            else -> 6
        }
        return symbol + trim(String.format(Locale.US, "%.${digits}f", abs))
    }

    private fun trim(raw: String): String {
        if (!raw.contains('.')) return raw
        return raw.trimEnd('0').trimEnd('.')
    }
}

object AssetIds {
    val bySymbol: Map<String, String> = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "SOL" to "solana",
        "DOGE" to "dogecoin",
        "USDT" to "tether",
        "USDC" to "usd-coin",
        "BNB" to "binancecoin",
        "XRP" to "ripple",
        "ADA" to "cardano",
        "AVAX" to "avalanche-2",
        "LINK" to "chainlink",
        "LTC" to "litecoin",
        "BCH" to "bitcoin-cash",
        "DOT" to "polkadot",
        "ATOM" to "cosmos",
        "NEAR" to "near",
        "APT" to "aptos",
        "SUI" to "sui",
        "TON" to "the-open-network",
        "SHIB" to "shiba-inu",
        "WBTC" to "wrapped-bitcoin",
        "WETH" to "weth",
        "POL" to "polygon-ecosystem-token",
        "MATIC" to "matic-network",
    )

    fun idFor(symbol: String): String =
        bySymbol[symbol.uppercase(Locale.US)] ?: symbol.lowercase(Locale.US)

    val coinbaseProducts = setOf("BTC", "ETH", "SOL", "DOGE", "LTC", "BCH", "LINK", "AVAX", "ADA", "DOT", "ATOM", "NEAR", "APT", "SUI", "SHIB")
}

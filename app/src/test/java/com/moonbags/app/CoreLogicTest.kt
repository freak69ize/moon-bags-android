package com.moonbags.app

import com.moonbags.app.data.remote.CoinbaseJwt
import com.moonbags.app.data.remote.HmacSigner
import com.moonbags.app.domain.CompactMoney
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreLogicTest {
    @Test
    fun compactMarketCaps() {
        assertEquals("$3.42T", CompactMoney.format(3.42e12, "usd"))
        assertEquals("$845.2B", CompactMoney.format(845.2e9, "usd"))
        assertEquals("$12.4M", CompactMoney.format(12.4e6, "usd"))
    }

    @Test
    fun hmacKnownVector() {
        val sig = HmacSigner.sign("key", "The quick brown fox jumps over the lazy dog")
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", sig)
    }

    @Test
    fun derSignatureFits64Bytes() {
        val der = byteArrayOf(
            0x30, 0x08,
            0x02, 0x02, 0x00, 0x7f,
            0x02, 0x02, 0x01, 0x01,
        )
        val jose = CoinbaseJwt.derEcdsaToJose(der, 2)
        assertEquals(4, jose.size)
        assertEquals(0x7f.toByte(), jose[1])
        assertEquals(0x01.toByte(), jose[3])
    }
}

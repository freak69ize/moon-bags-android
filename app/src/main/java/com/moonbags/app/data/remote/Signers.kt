package com.moonbags.app.data.remote

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacSigner {
    fun sign(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Coinbase CDP / Advanced Trade JWT (ES256).
 * Header kid + payload sub are the key name. Claim uri is "GET host/path".
 */
object CoinbaseJwt {
    fun bearer(keyName: String, pem: String, method: String, host: String, path: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val header = b64url(
            """{"alg":"ES256","kid":"${escape(keyName)}","nonce":"${UUID.randomUUID()}","typ":"JWT"}"""
                .toByteArray(Charsets.UTF_8),
        )
        val uri = "$method $host$path"
        val payload = b64url(
            """{"sub":"${escape(keyName)}","iss":"cdp","nbf":$nowSeconds,"exp":${nowSeconds + 120},"uri":"${escape(uri)}"}"""
                .toByteArray(Charsets.UTF_8),
        )
        val input = "$header.$payload"
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(parsePrivateKey(pem))
        signature.update(input.toByteArray(Charsets.UTF_8))
        val jose = derEcdsaToJose(signature.sign(), 32)
        return "$input.${b64url(jose)}"
    }

    fun derEcdsaToJose(der: ByteArray, size: Int): ByteArray {
        var offset = 2
        if (der.size > 1 && der[1].toInt() and 0x80 != 0) {
            offset = 2 + (der[1].toInt() and 0x7f)
        }
        require(der[offset] == 0x02.toByte()) { "Bad ECDSA signature" }
        val rLen = der[offset + 1].toInt() and 0xff
        var r = der.copyOfRange(offset + 2, offset + 2 + rLen)
        offset += 2 + rLen
        require(der[offset] == 0x02.toByte()) { "Bad ECDSA signature" }
        val sLen = der[offset + 1].toInt() and 0xff
        var s = der.copyOfRange(offset + 2, offset + 2 + sLen)
        r = fit(r, size)
        s = fit(s, size)
        return r + s
    }

    private fun fit(value: ByteArray, size: Int): ByteArray {
        var bytes = value
        while (bytes.size > size && bytes[0] == 0.toByte()) bytes = bytes.copyOfRange(1, bytes.size)
        if (bytes.size > size) bytes = bytes.copyOfRange(bytes.size - size, bytes.size)
        if (bytes.size < size) bytes = ByteArray(size - bytes.size) + bytes
        return bytes
    }

    fun parsePrivateKey(pem: String): PrivateKey {
        val body = pem
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val decoded = Base64.getDecoder().decode(body)
        val pkcs8 = if (pem.contains("BEGIN EC PRIVATE KEY")) sec1ToPkcs8(decoded) else decoded
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    fun sec1ToPkcs8(sec1: ByteArray): ByteArray {
        val version = tlv(0x02, byteArrayOf(0))
        val oid1 = byteArrayOf(0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01)
        val oid2 = byteArrayOf(0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x03, 0x01, 0x07)
        val alg = tlv(0x30, oid1 + oid2)
        val octet = tlv(0x04, sec1)
        return tlv(0x30, version + alg + octet)
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + derLen(content.size) + content

    private fun derLen(len: Int): ByteArray = when {
        len < 128 -> byteArrayOf(len.toByte())
        len < 256 -> byteArrayOf(0x81.toByte(), len.toByte())
        else -> byteArrayOf(0x82.toByte(), (len shr 8).toByte(), (len and 0xff).toByte())
    }

    private fun b64url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

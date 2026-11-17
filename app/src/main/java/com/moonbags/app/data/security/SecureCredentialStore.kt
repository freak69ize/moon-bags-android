package com.moonbags.app.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class ExchangeCredentials(
    val coinbaseKeyName: String? = null,
    val coinbasePem: String? = null,
    val binanceKey: String? = null,
    val binanceSecret: String? = null,
) {
    val hasCoinbase: Boolean get() = !coinbaseKeyName.isNullOrBlank() && !coinbasePem.isNullOrBlank()
    val hasBinance: Boolean get() = !binanceKey.isNullOrBlank() && !binanceSecret.isNullOrBlank()
}

/** Read-only exchange secrets in EncryptedSharedPreferences, backed by the Android Keystore. */
class SecureCredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): ExchangeCredentials = ExchangeCredentials(
        coinbaseKeyName = prefs.getString(CB_NAME, null),
        coinbasePem = prefs.getString(CB_PEM, null),
        binanceKey = prefs.getString(BN_KEY, null),
        binanceSecret = prefs.getString(BN_SECRET, null),
    )

    fun saveCoinbase(keyName: String, pem: String) {
        prefs.edit().putString(CB_NAME, keyName.trim()).putString(CB_PEM, pem.trim()).apply()
    }

    fun saveBinance(key: String, secret: String) {
        prefs.edit().putString(BN_KEY, key.trim()).putString(BN_SECRET, secret.trim()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "moonbags.secrets"
        const val CB_NAME = "cb_name"
        const val CB_PEM = "cb_pem"
        const val BN_KEY = "bn_key"
        const val BN_SECRET = "bn_secret"
    }
}

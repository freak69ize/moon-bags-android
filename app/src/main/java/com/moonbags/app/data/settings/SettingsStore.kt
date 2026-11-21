package com.moonbags.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("moon_settings")

data class UserSettings(
    val currency: String = "usd",
    val intervalMin: Int = 15,
    val theme: String = "ink",
    val materialYou: Boolean = true,
    val glass: Float = 0.18f,
    val radiusDp: Int = 22,
    val fontScale: Float = 1f,
    val scrim: Float = 0.55f,
    val splashSeconds: Int = 2,
    val splashPath: String? = null,
    val settingsPath: String? = null,
    val settingsDim: Float = 0.45f,
    val hideBalances: Boolean = false,
    val watchIds: String = "bitcoin,ethereum,solana,dogecoin",
    val tapPackage: String = "",
    val tapLabel: String = "",
    val sliderColor: String = "#E6E6E6",
    val subtextColor: String = "#8E8E93",
    val widgetAlpha: Float = 0.58f,
    val widgetBorder: String = "#D0BCFF",
    val cardColor: String = "#3A3842",
    val searchPillColor: String = "#242426",
    val searchBarColor: String = "#000000",
    val searchBarAlpha: Float = 0.72f,
    val pillBorderAlpha: Float = 0.28f,
    val widgetTint: Boolean = true,
)

class SettingsStore(private val context: Context) {
    val flow: Flow<UserSettings> = context.settingsStore.data.map { prefs ->
        UserSettings(
            currency = prefs[Keys.currency] ?: "usd",
            intervalMin = prefs[Keys.interval] ?: 15,
            theme = prefs[Keys.theme] ?: "ink",
            materialYou = prefs[Keys.materialYou] ?: true,
            glass = prefs[Keys.glass] ?: 0.18f,
            radiusDp = prefs[Keys.radius] ?: 22,
            fontScale = prefs[Keys.font] ?: 1f,
            scrim = prefs[Keys.scrim] ?: 0.55f,
            splashSeconds = prefs[Keys.splashSeconds] ?: 2,
            splashPath = prefs[Keys.splashPath],
            settingsPath = prefs[Keys.settingsPath],
            settingsDim = prefs[Keys.settingsDim] ?: 0.45f,
            hideBalances = prefs[Keys.hide] ?: false,
            watchIds = prefs[Keys.watch] ?: "bitcoin,ethereum,solana,dogecoin",
            tapPackage = prefs[Keys.tapPackage] ?: "",
            tapLabel = prefs[Keys.tapLabel] ?: "",
            sliderColor = prefs[Keys.sliderColor] ?: "#E6E6E6",
            subtextColor = prefs[Keys.subtextColor] ?: "#8E8E93",
            widgetAlpha = prefs[Keys.widgetAlpha] ?: 0.58f,
            widgetBorder = prefs[Keys.widgetBorder] ?: "#D0BCFF",
            cardColor = prefs[Keys.cardColor] ?: "#3A3842",
            searchPillColor = prefs[Keys.searchPillColor] ?: "#242426",
            searchBarColor = prefs[Keys.searchBarColor] ?: "#000000",
            searchBarAlpha = prefs[Keys.searchBarAlpha] ?: 0.72f,
            pillBorderAlpha = prefs[Keys.pillBorderAlpha] ?: 0.28f,
            widgetTint = prefs[Keys.widgetTint] ?: true,
        )
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.settingsStore.edit { prefs ->
            val next = transform(
                UserSettings(
                    currency = prefs[Keys.currency] ?: "usd",
                    intervalMin = prefs[Keys.interval] ?: 15,
                    theme = prefs[Keys.theme] ?: "ink",
                    materialYou = prefs[Keys.materialYou] ?: true,
                    glass = prefs[Keys.glass] ?: 0.18f,
                    radiusDp = prefs[Keys.radius] ?: 22,
                    fontScale = prefs[Keys.font] ?: 1f,
                    scrim = prefs[Keys.scrim] ?: 0.55f,
                    splashSeconds = prefs[Keys.splashSeconds] ?: 2,
                    splashPath = prefs[Keys.splashPath],
                    settingsPath = prefs[Keys.settingsPath],
                    settingsDim = prefs[Keys.settingsDim] ?: 0.45f,
                    hideBalances = prefs[Keys.hide] ?: false,
                    watchIds = prefs[Keys.watch] ?: "bitcoin,ethereum,solana,dogecoin",
                    tapPackage = prefs[Keys.tapPackage] ?: "",
                    tapLabel = prefs[Keys.tapLabel] ?: "",
                    sliderColor = prefs[Keys.sliderColor] ?: "#E6E6E6",
                    subtextColor = prefs[Keys.subtextColor] ?: "#8E8E93",
                    widgetAlpha = prefs[Keys.widgetAlpha] ?: 0.58f,
                    widgetBorder = prefs[Keys.widgetBorder] ?: "#D0BCFF",
                    cardColor = prefs[Keys.cardColor] ?: "#3A3842",
                    searchPillColor = prefs[Keys.searchPillColor] ?: "#242426",
                    searchBarColor = prefs[Keys.searchBarColor] ?: "#000000",
                    searchBarAlpha = prefs[Keys.searchBarAlpha] ?: 0.72f,
                    pillBorderAlpha = prefs[Keys.pillBorderAlpha] ?: 0.28f,
                    widgetTint = prefs[Keys.widgetTint] ?: true,
                ),
            )
            prefs[Keys.currency] = next.currency
            prefs[Keys.interval] = next.intervalMin
            prefs[Keys.theme] = next.theme
            prefs[Keys.materialYou] = next.materialYou
            prefs[Keys.glass] = next.glass
            prefs[Keys.radius] = next.radiusDp
            prefs[Keys.font] = next.fontScale
            prefs[Keys.scrim] = next.scrim
            prefs[Keys.splashSeconds] = next.splashSeconds
            if (next.splashPath == null) prefs.remove(Keys.splashPath) else prefs[Keys.splashPath] = next.splashPath
            if (next.settingsPath == null) prefs.remove(Keys.settingsPath) else prefs[Keys.settingsPath] = next.settingsPath
            prefs[Keys.settingsDim] = next.settingsDim
            prefs[Keys.hide] = next.hideBalances
            prefs[Keys.watch] = next.watchIds
            prefs[Keys.tapPackage] = next.tapPackage
            prefs[Keys.tapLabel] = next.tapLabel
            prefs[Keys.sliderColor] = next.sliderColor
            prefs[Keys.subtextColor] = next.subtextColor
            prefs[Keys.widgetAlpha] = next.widgetAlpha
            prefs[Keys.widgetBorder] = next.widgetBorder
            prefs[Keys.cardColor] = next.cardColor
            prefs[Keys.searchPillColor] = next.searchPillColor
            prefs[Keys.searchBarColor] = next.searchBarColor
            prefs[Keys.searchBarAlpha] = next.searchBarAlpha
            prefs[Keys.pillBorderAlpha] = next.pillBorderAlpha
            prefs[Keys.widgetTint] = next.widgetTint
        }
    }

    private object Keys {
        val currency = stringPreferencesKey("currency")
        val interval = intPreferencesKey("interval")
        val theme = stringPreferencesKey("theme")
        val materialYou = booleanPreferencesKey("material_you")
        val glass = floatPreferencesKey("glass")
        val radius = intPreferencesKey("radius")
        val font = floatPreferencesKey("font")
        val scrim = floatPreferencesKey("scrim")
        val splashSeconds = intPreferencesKey("splash_seconds")
        val splashPath = stringPreferencesKey("splash_path")
        val settingsPath = stringPreferencesKey("settings_path")
        val settingsDim = floatPreferencesKey("settings_dim")
        val hide = booleanPreferencesKey("hide")
        val watch = stringPreferencesKey("watch")
        val tapPackage = stringPreferencesKey("tap_package")
        val tapLabel = stringPreferencesKey("tap_label")
        val sliderColor = stringPreferencesKey("slider_color")
        val subtextColor = stringPreferencesKey("subtext_color")
        val widgetAlpha = floatPreferencesKey("widget_alpha")
        val widgetBorder = stringPreferencesKey("widget_border")
        val cardColor = stringPreferencesKey("card_color")
        val searchPillColor = stringPreferencesKey("search_pill_color")
        val searchBarColor = stringPreferencesKey("search_bar_color")
        val searchBarAlpha = floatPreferencesKey("search_bar_alpha")
        val pillBorderAlpha = floatPreferencesKey("pill_border_alpha")
        val widgetTint = booleanPreferencesKey("widget_tint")
    }
}

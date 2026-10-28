package com.gimirick.circletosearch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore by preferencesDataStore(name = "settings")

const val MIN_BALL_SIZE_DP = 32
const val MAX_BALL_SIZE_DP = 96

data class SettingsState(
    val ballEnabled: Boolean = true,
    val ballSizeDp: Int = 56,
    val ballXFraction: Float = 0.92f,
    val ballYFraction: Float = 0.5f,
)

class SettingsRepository(private val context: Context) {
    val settings = context.settingsStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
        SettingsState(
            ballEnabled = prefs[Keys.ballEnabled] ?: true,
            ballSizeDp = (prefs[Keys.ballSize] ?: 56).coerceIn(MIN_BALL_SIZE_DP, MAX_BALL_SIZE_DP),
            ballXFraction = (prefs[Keys.ballX] ?: 0.92f).coerceIn(0f, 1f),
            ballYFraction = (prefs[Keys.ballY] ?: 0.5f).coerceIn(0f, 1f),
        )
    }

    suspend fun setBallEnabled(enabled: Boolean) = context.settingsStore.edit { it[Keys.ballEnabled] = enabled }

    suspend fun setBallSize(sizeDp: Int) = context.settingsStore.edit { it[Keys.ballSize] = sizeDp.coerceIn(MIN_BALL_SIZE_DP, MAX_BALL_SIZE_DP) }

    suspend fun setBallPosition(xFraction: Float, yFraction: Float) = context.settingsStore.edit {
        it[Keys.ballX] = xFraction.coerceIn(0f, 1f)
        it[Keys.ballY] = yFraction.coerceIn(0f, 1f)
    }

    private object Keys {
        val ballEnabled = booleanPreferencesKey("ball_enabled")
        val ballSize = intPreferencesKey("ball_size_dp")
        val ballX = floatPreferencesKey("ball_x_fraction")
        val ballY = floatPreferencesKey("ball_y_fraction")
    }
}

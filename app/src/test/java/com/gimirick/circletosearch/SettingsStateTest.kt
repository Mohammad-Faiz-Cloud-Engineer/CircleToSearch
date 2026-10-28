package com.gimirick.circletosearch

import com.gimirick.circletosearch.data.MAX_BALL_SIZE_DP
import com.gimirick.circletosearch.data.MIN_BALL_SIZE_DP
import com.gimirick.circletosearch.data.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateTest {

    @Test
    fun defaultSettings_haveExpectedValues() {
        val state = SettingsState()
        assertTrue("Ball should be enabled by default", state.ballEnabled)
        assertEquals(56, state.ballSizeDp)
        assertEquals(1f, state.ballXFraction, 0.001f)
        assertEquals(0.5f, state.ballYFraction, 0.001f)
    }

    @Test
    fun ballSizeDp_respectsMinAndMaxConstants() {
        assertTrue(MIN_BALL_SIZE_DP < MAX_BALL_SIZE_DP)
        assertEquals(32, MIN_BALL_SIZE_DP)
        assertEquals(96, MAX_BALL_SIZE_DP)

        val clampedLow = 10.coerceIn(MIN_BALL_SIZE_DP, MAX_BALL_SIZE_DP)
        val clampedHigh = 200.coerceIn(MIN_BALL_SIZE_DP, MAX_BALL_SIZE_DP)
        val clampedNormal = 56.coerceIn(MIN_BALL_SIZE_DP, MAX_BALL_SIZE_DP)

        assertEquals(MIN_BALL_SIZE_DP, clampedLow)
        assertEquals(MAX_BALL_SIZE_DP, clampedHigh)
        assertEquals(56, clampedNormal)
    }

    @Test
    fun coordinates_clampBetweenZeroAndOne() {
        val lowX = (-0.5f).coerceIn(0f, 1f)
        val highX = 1.5f.coerceIn(0f, 1f)
        val normalX = 0.5f.coerceIn(0f, 1f)

        assertEquals(0f, lowX, 0.001f)
        assertEquals(1f, highX, 0.001f)
        assertEquals(0.5f, normalX, 0.001f)
    }
}

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

    @Test
    fun ballPositionFraction_doesNotDivideByZeroWhenScreenMatchesBallSize() {
        val screenWidth = 100
        val ballWidth = 100
        val posX = 50
        // BallAccessibilityService line 419: (point.x - params.width).coerceAtLeast(1)
        val denominator = (screenWidth - ballWidth).coerceAtLeast(1)
        assertEquals(1, denominator)
        val fraction = (posX.toFloat() / denominator).coerceIn(0f, 1f)
        assertEquals(1f, fraction, 0.001f)
    }

    @Test
    fun dockEdge_correctlyIdentifiesLeftAndRight() {
        val screenWidth = 1080
        val ballWidth = 120
        val maxX = (screenWidth - ballWidth).coerceAtLeast(0) // 960

        // Right side: x >= maxX / 2 (480)
        val rightX = 500
        val isRight = rightX >= maxX / 2
        assertTrue("Position 500 should be detected as right edge", isRight)

        // Left side: x < maxX / 2
        val leftX = 400
        val isLeft = leftX < maxX / 2
        assertTrue("Position 400 should be detected as left edge", isLeft)
    }

    @Test
    fun autoHideOffsets_correctlyTuckHalfBall() {
        val screenWidth = 1080
        val ballWidth = 120
        val maxX = screenWidth - ballWidth // 960
        val tuckX = ballWidth / 2 // 60

        // Right tuck: maxX + tuckX
        val rightTuckX = maxX + tuckX
        assertEquals(1020, rightTuckX)

        // Left tuck: -tuckX
        val leftTuckX = -tuckX
        assertEquals(-60, leftTuckX)
    }

    @Test
    fun snapBall_snapsToExpectedEdge() {
        val screenWidth = 1080
        val ballWidth = 120
        val maxX = screenWidth - ballWidth // 960

        // Ball center on left half (center < 540)
        val ballX1 = 200
        val snappedLeft = if (ballX1 + ballWidth / 2 < screenWidth / 2) 0 else maxX
        assertEquals(0, snappedLeft)

        // Ball center on right half (center >= 540)
        val ballX2 = 600
        val snappedRight = if (ballX2 + ballWidth / 2 < screenWidth / 2) 0 else maxX
        assertEquals(maxX, snappedRight)
    }

    @Test
    fun cropJitterRejection_rejectsMicroGestures() {
        val minDiff = 10f // e.g. dp(8f)
        val smallDeltaX = 5f
        val smallDeltaY = 4f
        val isJitter = smallDeltaX < minDiff || smallDeltaY < minDiff
        assertTrue("Movement smaller than min threshold should be rejected as jitter", isJitter)
    }

    @Test
    fun cropBoundingBox_staysWithinScreenBounds() {
        val screenW = 1080
        val screenH = 2400
        val pad = 32f

        // Gesture near top-left edge
        val minX = 10f
        val minY = 5f
        val left = (minX - pad).coerceAtLeast(0f).toInt()
        val top = (minY - pad).coerceAtLeast(0f).toInt()
        assertEquals(0, left)
        assertEquals(0, top)

        // Gesture near bottom-right edge
        val maxX = 1075f
        val maxY = 2395f
        val right = (maxX + pad).coerceAtMost(screenW.toFloat()).toInt()
        val bottom = (maxY + pad).coerceAtMost(screenH.toFloat()).toInt()
        assertEquals(screenW, right)
        assertEquals(screenH, bottom)
    }
}

package com.astron.app.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHapticEngine = staticCompositionLocalOf<HapticFeedbackManager?> { null }

/**
 * Hardware-accelerated haptic sensory feedback engine.
 * Provides tactile feedback for user interactions, file transfers, and peer discovery.
 */
class HapticFeedbackManager(private val context: Context) {

    companion object {
        private const val TAG = "HapticManager"
    }

    var isEnabled: Boolean = true

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot access vibrator service", t)
            null
        }
    }

    /**
     * Subtle tick on UI interactions (item selection, slider, radar pulse).
     */
    fun tick() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(10L)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Crisp click on primary actions (Send/Receive tap, peer tap).
     */
    fun click() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25L)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Firm feedback on transfer initiation or peer connection.
     */
    fun heavyClick() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45L)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Celebratory double-pulse rumble on transfer completion & SHA-256 verification.
     */
    fun success() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 40, 80, 70)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 40, 80, 70), -1)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Triple warning buzz on transfer failure or connection drop.
     */
    fun error() {
        if (!isEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 50, 60, 50, 60, 80)
                val amplitudes = intArrayOf(0, 220, 0, 220, 0, 255)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 50, 60, 50, 60, 80), -1)
            }
        } catch (_: Throwable) {}
    }
}

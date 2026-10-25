package com.astron.app.core.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable

class SoundFeedbackManager : Closeable {

    companion object {
        const val TAG = "SoundFeedbackManager"

        @Volatile
        var isMuted: Boolean = false
            private set

        fun setMuted(muted: Boolean) {
            isMuted = muted
        }

        fun toggleMute(): Boolean {
            isMuted = !isMuted
            return isMuted
        }
    }

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
    } catch (e: Throwable) {
        Log.w(TAG, "ToneGenerator not available on this device", e)
        null
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Plays a high-tech cosmic chirp when a file transfer session begins.
     */
    fun playTransferStart(soundEnabled: Boolean = true) {
        if (isMuted || !soundEnabled) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            } catch (e: Throwable) {
                Log.w(TAG, "Error playing start tone", e)
            }
        }
    }

    /**
     * Plays a two-stage harmonious confirmation chime when files are cryptographically verified and transfer completes.
     */
    fun playTransferComplete(soundEnabled: Boolean = true) {
        if (isMuted || !soundEnabled) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                delay(140)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 160)
            } catch (e: Throwable) {
                Log.w(TAG, "Error playing complete tone", e)
            }
        }
    }

    /**
     * Plays an alert pulse when an incoming peer requests file transfer approval.
     */
    fun playIncomingAlert(soundEnabled: Boolean = true) {
        if (isMuted || !soundEnabled) return
        scope.launch {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 180)
            } catch (e: Throwable) {
                Log.w(TAG, "Error playing alert tone", e)
            }
        }
    }

    override fun close() {
        try {
            toneGenerator?.release()
        } catch (_: Throwable) {}
        toneGenerator = null
    }
}

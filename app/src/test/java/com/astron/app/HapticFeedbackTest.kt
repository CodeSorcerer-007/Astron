package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.util.HapticFeedbackManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackTest {

    @Test
    fun testHapticFeedbackManagerNullSafeExecution() {
        val dummyContext = object : ContextWrapper(null) {
            override fun getSystemService(name: String): Any? = null
        }

        val manager = HapticFeedbackManager(dummyContext)
        assertNotNull(manager)

        // All sensory haptic triggers must execute safely without throwing exceptions on devices lacking LRA or vibrator service
        manager.tick()
        manager.click()
        manager.heavyClick()
        manager.success()
        manager.error()
        manager.milestone()
    }

    @Test
    fun testSoundFeedbackManagerMuteToggle() {
        val soundManager = com.astron.app.core.util.SoundFeedbackManager()
        assertNotNull(soundManager)

        com.astron.app.core.util.SoundFeedbackManager.setMuted(false)
        org.junit.Assert.assertFalse(com.astron.app.core.util.SoundFeedbackManager.isMuted)

        val muted = com.astron.app.core.util.SoundFeedbackManager.toggleMute()
        assertTrue(muted)
        assertTrue(com.astron.app.core.util.SoundFeedbackManager.isMuted)

        // Plays should be safely skipped without exceptions when muted
        soundManager.playTransferStart()
        soundManager.playIncomingAlert()
        soundManager.playTransferComplete()

        val unmuted = com.astron.app.core.util.SoundFeedbackManager.toggleMute()
        org.junit.Assert.assertFalse(unmuted)
        soundManager.close()
    }
}

package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.util.HapticFeedbackManager
import org.junit.Assert.assertNotNull
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
    }
}

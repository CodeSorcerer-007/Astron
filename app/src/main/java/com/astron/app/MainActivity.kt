package com.astron.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.astron.app.data.PreferencesRepository
import com.astron.app.ui.navigation.MainNavigation
import com.astron.app.ui.theme.AstronTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var hapticManager: com.astron.app.core.util.HapticFeedbackManager

    private val _sharedUris = mutableStateOf<List<Uri>>(emptyList())
    private val _nfcPayload = mutableStateOf<com.astron.app.core.nfc.NfcPairingPayload?>(null)
    private val _navDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            Log.w("MainActivity", "Edge-to-edge initialization warning", t)
        }

        _sharedUris.value = extractSharedUris(intent)
        val nfc = com.astron.app.core.nfc.NfcManager.parseIntent(intent)
        if (nfc != null) {
            hapticManager.heavyClick()
            _nfcPayload.value = nfc
        }
        val nav = intent?.getStringExtra(com.astron.app.service.AstronNotificationHelper.EXTRA_NAVIGATE_TO)
        if (!nav.isNullOrBlank()) {
            _navDestination.value = nav
        }

        setContent {
            val isDark by preferencesRepository.darkModeFlow.collectAsState(initial = false)
            val isHapticOn by preferencesRepository.hapticsEnabledFlow.collectAsState(initial = true)
            hapticManager.isEnabled = isHapticOn

            androidx.compose.runtime.CompositionLocalProvider(
                com.astron.app.core.util.LocalHapticEngine provides hapticManager
            ) {
                AstronTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val uris by remember { _sharedUris }
                        val nfcData by remember { _nfcPayload }
                        val navTarget by remember { _navDestination }
                        MainNavigation(initialUris = uris, initialNfcPayload = nfcData, initialDestination = navTarget)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uris = extractSharedUris(intent)
        if (uris.isNotEmpty()) {
            _sharedUris.value = uris
        }
        val nfc = com.astron.app.core.nfc.NfcManager.parseIntent(intent)
        if (nfc != null) {
            hapticManager.heavyClick()
            _nfcPayload.value = nfc
        }
        val nav = intent.getStringExtra(com.astron.app.service.AstronNotificationHelper.EXTRA_NAVIGATE_TO)
        if (!nav.isNullOrBlank()) {
            _navDestination.value = nav
        }
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val uris = mutableListOf<Uri>()

        try {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    streamUri?.let { uris.add(it) }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    streamUris?.let { uris.addAll(it) }
                }
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to extract shared URIs safely", e)
        }
        return uris
    }
}

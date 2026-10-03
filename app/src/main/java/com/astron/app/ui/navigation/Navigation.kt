package com.astron.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.astron.app.ui.screens.history.HistoryScreen
import com.astron.app.ui.screens.home.HomeScreen
import com.astron.app.ui.screens.lightdrop.LightDropScreen
import com.astron.app.ui.screens.receive.ReceiveScreen
import com.astron.app.ui.screens.send.SendScreen
import com.astron.app.ui.screens.settings.SettingsScreen
import com.astron.app.ui.screens.transfer.TransferScreen
import com.astron.app.ui.screens.webdrop.WebDropScreen

enum class Screen {
    HOME,
    SEND,
    RECEIVE,
    TRANSFER,
    WEBDROP,
    LIGHTDROP,
    HISTORY,
    SETTINGS
}

@Composable
fun MainNavigation(
    initialUris: List<Uri> = emptyList()
) {
    var currentScreen by remember {
        mutableStateOf(if (initialUris.isNotEmpty()) Screen.SEND else Screen.HOME)
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "navigation"
    ) { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                onNavigateToSend = { currentScreen = Screen.SEND },
                onNavigateToReceive = { currentScreen = Screen.RECEIVE },
                onNavigateToWebDrop = { currentScreen = Screen.WEBDROP },
                onNavigateToLightDrop = { currentScreen = Screen.LIGHTDROP },
                onNavigateToHistory = { currentScreen = Screen.HISTORY },
                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
            )
            Screen.SEND -> SendScreen(
                onNavigateBack = { currentScreen = Screen.HOME },
                onTransferStarted = { currentScreen = Screen.TRANSFER },
                onNavigateToWebDrop = { currentScreen = Screen.WEBDROP },
                onNavigateToLightDrop = { currentScreen = Screen.LIGHTDROP },
                initialUris = initialUris
            )
            Screen.RECEIVE -> ReceiveScreen(
                onNavigateBack = { currentScreen = Screen.HOME },
                onTransferAccepted = { currentScreen = Screen.TRANSFER }
            )
            Screen.TRANSFER -> TransferScreen(
                onDismiss = { currentScreen = Screen.HOME }
            )
            Screen.WEBDROP -> WebDropScreen(
                onNavigateBack = { currentScreen = Screen.HOME }
            )
            Screen.LIGHTDROP -> LightDropScreen(
                onNavigateBack = { currentScreen = Screen.HOME }
            )
            Screen.HISTORY -> HistoryScreen(
                onNavigateBack = { currentScreen = Screen.HOME }
            )
            Screen.SETTINGS -> SettingsScreen(
                onNavigateBack = { currentScreen = Screen.HOME }
            )
        }
    }
}

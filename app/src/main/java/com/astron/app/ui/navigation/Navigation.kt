package com.astron.app.ui.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.astron.app.core.model.TransferItem
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
    SETTINGS,
    FILE_BROWSER
}

@Composable
fun MainNavigation(
    initialUris: List<Uri> = emptyList(),
    initialNfcPayload: com.astron.app.core.nfc.NfcPairingPayload? = null
) {
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.map { screen -> screen.name } },
            restore = { names ->
                mutableStateListOf<Screen>().apply {
                    addAll(names.mapNotNull { name ->
                        try { Screen.valueOf(name) } catch (_: Exception) { null }
                    })
                }
            }
        )
    ) {
        mutableStateListOf<Screen>().apply {
            if (initialUris.isNotEmpty() || initialNfcPayload != null) {
                add(Screen.HOME)
                add(Screen.SEND)
            } else {
                add(Screen.HOME)
            }
        }
    }

    var pendingSendItems by remember { mutableStateOf<List<TransferItem>>(emptyList()) }

    val currentScreen = backStack.lastOrNull() ?: Screen.HOME

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(screen)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun navigateHome() {
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    androidx.compose.runtime.LaunchedEffect(initialNfcPayload) {
        if (initialNfcPayload != null && currentScreen != Screen.SEND && currentScreen != Screen.TRANSFER) {
            navigateTo(Screen.SEND)
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)))
                .togetherWith(fadeOut(animationSpec = tween(180)))
        },
        label = "navigation"
    ) { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                onNavigateToSend = { items ->
                    pendingSendItems = items
                    navigateTo(Screen.SEND)
                },
                onNavigateToReceive = { navigateTo(Screen.RECEIVE) },
                onNavigateToWebDrop = {
                    pendingSendItems = emptyList()
                    navigateTo(Screen.WEBDROP)
                },
                onNavigateToLightDrop = { navigateTo(Screen.LIGHTDROP) },
                onNavigateToHistory = { navigateTo(Screen.HISTORY) },
                onNavigateToSettings = { navigateTo(Screen.SETTINGS) },
                onNavigateToFileBrowser = { navigateTo(Screen.FILE_BROWSER) }
            )
            Screen.SEND -> SendScreen(
                onNavigateBack = { navigateBack() },
                onTransferStarted = { navigateTo(Screen.TRANSFER) },
                onNavigateToWebDrop = { items ->
                    pendingSendItems = items
                    navigateTo(Screen.WEBDROP)
                },
                onNavigateToLightDrop = { navigateTo(Screen.LIGHTDROP) },
                onNavigateToFileBrowser = { navigateTo(Screen.FILE_BROWSER) },
                initialUris = initialUris,
                initialItems = pendingSendItems,
                initialNfcPayload = initialNfcPayload
            )
            Screen.RECEIVE -> ReceiveScreen(
                onNavigateBack = { navigateBack() },
                onTransferAccepted = { navigateTo(Screen.TRANSFER) }
            )
            Screen.TRANSFER -> TransferScreen(
                onDismiss = { navigateHome() }
            )
            Screen.WEBDROP -> WebDropScreen(
                onNavigateBack = { navigateBack() },
                sharedFiles = pendingSendItems
            )
            Screen.LIGHTDROP -> LightDropScreen(
                onNavigateBack = { navigateBack() }
            )
            Screen.HISTORY -> HistoryScreen(
                onNavigateBack = { navigateBack() }
            )
            Screen.SETTINGS -> SettingsScreen(
                onNavigateBack = { navigateBack() }
            )
            Screen.FILE_BROWSER -> com.astron.app.ui.screens.filebrowser.FileBrowserScreen(
                onNavigateBack = { navigateBack() },
                onConfirmSelection = { selected ->
                    pendingSendItems = selected
                    navigateTo(Screen.SEND)
                }
            )
        }
    }
}



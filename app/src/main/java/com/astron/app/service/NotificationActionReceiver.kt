package com.astron.app.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.astron.app.core.util.AppFileManager
import com.astron.app.transport.webdrop.WebDropServer

/**
 * NotificationActionReceiver
 * Handles direct interactive taps from action buttons in Android's notification panel.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            AstronNotificationHelper.ACTION_COPY_WEBDROP_URL -> {
                val url = intent.getStringExtra(AstronNotificationHelper.EXTRA_WEBDROP_URL)
                if (!url.isNullOrBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Astron Web Drop URL", url))
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Web Drop link copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            AstronNotificationHelper.ACTION_STOP_WEBDROP -> {
                try {
                    WebDropServer.activeInstance?.stopServer()
                } catch (_: Throwable) {}
                AstronNotificationHelper.dismissWebDropNotification(context)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Astron Web Drop server stopped", Toast.LENGTH_SHORT).show()
                }
            }

            AstronNotificationHelper.ACTION_OPEN_DOWNLOADS -> {
                AppFileManager.openDownloadsFolder(context)
            }
        }
    }
}

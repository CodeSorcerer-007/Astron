package com.astron.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.astron.app.MainActivity
import com.astron.app.R
import com.astron.app.core.util.NetworkUtils

/**
 * AstronNotificationHelper
 * Centralized manager for rich notification panel experiences:
 * 1. Live Foreground Transfer Progress with ETA, real-time speed, pause/resume, and cancel.
 * 2. Post-transfer celebration notification with direct "View in History" and "Open Files" actions.
 * 3. Interrupted transfer alert with resume action.
 * 4. Heads-up incoming transfer request notification.
 * 5. Web Drop background service notification with "Copy Link" and "Stop" actions.
 */
object AstronNotificationHelper {

    private const val TAG = "AstronNotificationHelper"

    // Channels
    const val CHANNEL_TRANSFERS = "astron_transfer_channel"
    const val CHANNEL_ALERTS = "astron_complete_channel"
    const val CHANNEL_SERVICES = "astron_services_channel"

    // Notification IDs
    const val NOTIFICATION_ID_TRANSFER = 1001
    const val NOTIFICATION_ID_COMPLETE = 1002
    const val NOTIFICATION_ID_FAILED = 1003
    const val NOTIFICATION_ID_WEBDROP = 1004
    const val NOTIFICATION_ID_INCOMING = 1005

    // Intents & Actions
    const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
    const val NAV_TARGET_TRANSFER = "transfer"
    const val NAV_TARGET_HISTORY = "history"
    const val NAV_TARGET_RECEIVE = "receive"
    const val NAV_TARGET_WEBDROP = "webdrop"
    const val NAV_TARGET_HOME = "home"

    const val ACTION_COPY_WEBDROP_URL = "com.astron.app.action.COPY_WEBDROP_URL"
    const val ACTION_STOP_WEBDROP = "com.astron.app.action.STOP_WEBDROP"
    const val ACTION_OPEN_DOWNLOADS = "com.astron.app.action.OPEN_DOWNLOADS"
    const val EXTRA_WEBDROP_URL = "extra_webdrop_url"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        try {
            // 1. Transfer progress channel (silent updates)
            val transferChannel = NotificationChannel(
                CHANNEL_TRANSFERS,
                "Active File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live progress, transfer speed, and controls for ongoing transfers"
                setShowBadge(false)
                enableVibration(false)
            }

            // 2. Alerts and completion channel (prominent, sound + vibration)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Transfer Alerts & Complete",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when transfers complete, incoming transfer requests, and errors"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 200)
            }

            // 3. Web Drop and background services channel
            val servicesChannel = NotificationChannel(
                CHANNEL_SERVICES,
                "Astron Web Drop & Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active local Web Drop browser sharing status"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(transferChannel, alertChannel, servicesChannel))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed creating notification channels", t)
        }
    }

    /**
     * Builds the persistent foreground notification for active transfers with controls.
     */
    fun buildTransferNotification(
        context: Context,
        peerName: String,
        currentFile: String,
        transferred: Long,
        total: Long,
        speedBps: Long,
        isPaused: Boolean
    ): Notification {
        initChannels(context)

        val progress = if (total > 0) ((transferred * 100) / total).toInt().coerceIn(0, 100) else 0
        val remainingBytes = (total - transferred).coerceAtLeast(0L)
        val etaSeconds = if (speedBps > 0) remainingBytes / speedBps else 0L

        val speedStr = NetworkUtils.formatSpeed(speedBps)
        val transferredStr = "${NetworkUtils.formatBytes(transferred)} / ${NetworkUtils.formatBytes(total)}"
        val etaStr = if (etaSeconds > 0) NetworkUtils.formatEta(etaSeconds) else "Calculating..."

        // Tapping opens Astron in Transfer Screen
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAV_TARGET_TRANSFER)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel Action
        val cancelIntent = Intent(context, TransferForegroundService::class.java).apply {
            action = TransferForegroundService.ACTION_CANCEL
        }
        val pendingCancel = PendingIntent.getService(
            context,
            11,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause / Resume Action
        val pauseResumeIntent = Intent(context, TransferForegroundService::class.java).apply {
            action = TransferForegroundService.ACTION_PAUSE_RESUME
        }
        val pendingPauseResume = PendingIntent.getService(
            context,
            12,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val brandLogo = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.astron_logo)
        } catch (_: Throwable) {
            null
        }

        val title = if (isPaused) {
            "Paused: $currentFile ($progress%)"
        } else {
            "$progress% • $currentFile"
        }

        val shortText = if (isPaused) {
            "Transfer paused with $peerName"
        } else {
            "$transferredStr • $speedStr • $etaStr"
        }

        val bigText = buildString {
            if (isPaused) {
                append("Transfer paused.\n")
            } else {
                append("Speed: ").append(speedStr).append(" • ").append(etaStr).append("\n")
            }
            append("Transferred: ").append(transferredStr).append(" (").append(progress).append("%)\n")
            append("Peer: ").append(peerName)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setContentTitle(title)
            .setContentText(shortText)
            .setSubText("Astron Hyper-Mesh")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (brandLogo != null) setLargeIcon(brandLogo)
            }
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, total <= 0)

        // Add Pause / Resume action button
        if (isPaused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                pendingPauseResume
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                pendingPauseResume
            )
        }

        // Add Cancel action button
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            pendingCancel
        )

        return builder.build()
    }

    /**
     * Shows a celebration notification in the notification panel when a transfer succeeds.
     */
    fun showTransferCompleteNotification(
        context: Context,
        peerName: String,
        fileCount: Int,
        totalBytes: Long,
        isSender: Boolean
    ) {
        initChannels(context)

        // Intent to view History
        val historyIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAV_TARGET_HISTORY)
        }
        val pendingHistory = PendingIntent.getActivity(
            context,
            20,
            historyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to open Downloads folder
        val openFolderIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_OPEN_DOWNLOADS
        }
        val pendingOpenFolder = PendingIntent.getBroadcast(
            context,
            21,
            openFolderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val brandLogo = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.astron_logo)
        } catch (_: Throwable) {
            null
        }

        val totalSizeStr = NetworkUtils.formatBytes(totalBytes)
        val fileWord = if (fileCount == 1) "1 file" else "$fileCount files"
        val actionText = if (isSender) "Sent to" else "Received from"

        val title = "Transfer Complete! 🎉"
        val contentText = "$actionText $peerName • $fileWord ($totalSizeStr)"
        val bigText = "All $fileWord ($totalSizeStr) successfully transferred with $peerName.\nTap to view in History or open downloads."

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification)
            .apply {
                if (brandLogo != null) setLargeIcon(brandLogo)
            }
            .setContentIntent(pendingHistory)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(android.R.drawable.ic_menu_recent_history, "View History", pendingHistory)
            .apply {
                if (!isSender) {
                    addAction(android.R.drawable.ic_menu_view, "Open Files", pendingOpenFolder)
                }
            }
            .build()

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID_COMPLETE, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot show complete notification", t)
        }
    }

    /**
     * Shows an alert notification when a transfer fails or gets interrupted.
     */
    fun showTransferFailedNotification(
        context: Context,
        peerName: String,
        reason: String
    ) {
        initChannels(context)

        val homeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAV_TARGET_HOME)
        }
        val pendingHome = PendingIntent.getActivity(
            context,
            30,
            homeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle("Transfer Interrupted")
            .setContentText("Transfer with $peerName stopped ($reason)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("The transfer session with $peerName was interrupted: $reason.\nTap to reopen Astron and resume.")
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingHome)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(android.R.drawable.ic_menu_rotate, "Open Astron", pendingHome)
            .build()

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID_FAILED, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot show failed notification", t)
        }
    }

    /**
     * Shows a heads-up notification for incoming transfer requests.
     */
    fun showIncomingTransferNotification(
        context: Context,
        senderName: String,
        fileCount: Int,
        totalBytes: Long
    ) {
        initChannels(context)

        val receiveIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAV_TARGET_RECEIVE)
        }
        val pendingReceive = PendingIntent.getActivity(
            context,
            40,
            receiveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fileWord = if (fileCount == 1) "1 file" else "$fileCount files"
        val totalSizeStr = NetworkUtils.formatBytes(totalBytes)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle("Incoming Transfer Request")
            .setContentText("$senderName wants to send $fileWord ($totalSizeStr)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$senderName is requesting to send $fileWord ($totalSizeStr) to your device.\nTap to accept or review.")
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingReceive)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(android.R.drawable.ic_input_add, "Review Request", pendingReceive)
            .build()

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID_INCOMING, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot show incoming request notification", t)
        }
    }

    fun dismissIncomingTransferNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID_INCOMING)
        } catch (_: Throwable) {}
    }

    /**
     * Shows a persistent notification in the notification panel while Web Drop is active.
     */
    fun showWebDropNotification(context: Context, webDropUrl: String) {
        initChannels(context)

        val openWebDropIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, NAV_TARGET_WEBDROP)
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            50,
            openWebDropIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_COPY_WEBDROP_URL
            putExtra(EXTRA_WEBDROP_URL, webDropUrl)
        }
        val pendingCopy = PendingIntent.getBroadcast(
            context,
            51,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP_WEBDROP
        }
        val pendingStop = PendingIntent.getBroadcast(
            context,
            52,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICES)
            .setContentTitle("Astron Web Drop Active 🌐")
            .setContentText("Sharing at $webDropUrl")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Local Web Server running at:\n$webDropUrl\nAny PC, iPhone, Mac, or Linux browser on your Wi-Fi can download or upload files.")
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_share, "Copy Link", pendingCopy)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .build()

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID_WEBDROP, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot show Web Drop notification", t)
        }
    }

    fun dismissWebDropNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID_WEBDROP)
        } catch (_: Throwable) {}
    }
}

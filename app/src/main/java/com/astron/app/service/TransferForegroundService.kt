package com.astron.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.astron.app.MainActivity
import com.astron.app.R
import com.astron.app.core.util.NetworkUtils
import com.astron.app.transfer.TransferEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TransferForegroundService : Service() {

    @Inject
    lateinit var transferEngine: TransferEngine

    companion object {
        const val TAG = "TransferService"
        const val CHANNEL_ID = "astron_transfer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.astron.app.action.START_TRANSFER"
        const val ACTION_UPDATE = "com.astron.app.action.UPDATE_PROGRESS"
        const val ACTION_STOP = "com.astron.app.action.STOP_TRANSFER"
        const val ACTION_CANCEL = "com.astron.app.action.CANCEL_TRANSFER"

        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_CURRENT_FILE = "extra_current_file"
        const val EXTRA_TRANSFERRED_BYTES = "extra_transferred_bytes"
        const val EXTRA_TOTAL_BYTES = "extra_total_bytes"

        @Volatile
        var activeInstance: TransferForegroundService? = null

        fun start(context: Context, peerName: String) {
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PEER_NAME, peerName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot start foreground service: ${e.message}")
            }
        }

        fun updateProgress(
            context: Context,
            peerName: String,
            transferred: Long,
            total: Long,
            speedBps: Long,
            currentFile: String
        ) {
            val instance = activeInstance
            if (instance != null) {
                instance.updateProgressDirect(peerName, transferred, total, speedBps, currentFile)
                return
            }
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_PEER_NAME, peerName)
                    putExtra(EXTRA_TRANSFERRED_BYTES, transferred)
                    putExtra(EXTRA_TOTAL_BYTES, total)
                    putExtra(EXTRA_SPEED, speedBps)
                    putExtra(EXTRA_CURRENT_FILE, currentFile)
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot update service progress: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, TransferForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Cannot stop foreground service: ${e.message}")
            }
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var peerName = "Nearby Device"
    private var isForegroundActive = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var brandLogo: android.graphics.Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        brandLogo = try {
            BitmapFactory.decodeResource(resources, R.drawable.astron_logo)
        } catch (_: Throwable) {
            null
        }
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "astron:transfer_wakelock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(6 * 60 * 60 * 1000L) // 6 hours safety timeout for large transfers
                }
            }
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifiManager?.createWifiLock(mode, "astron:transfer_wifilock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed acquiring transfer locks: ${t.message}")
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (_: Throwable) {}
        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (_: Throwable) {}
    }

    private fun ensureForegroundStarted() {
        if (!isForegroundActive) {
            acquireLocks()
            val notification = buildNotification(
                progressPercent = 0,
                contentTitle = "Preparing transfer with $peerName",
                contentText = "Connecting..."
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundActive = true
            } catch (t: Throwable) {
                Log.e(TAG, "startForeground error", t)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START -> {
                    peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Nearby Device"
                    ensureForegroundStarted()
                }
                ACTION_UPDATE -> {
                    ensureForegroundStarted()
                    peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: peerName
                    val transferred = intent.getLongExtra(EXTRA_TRANSFERRED_BYTES, 0L)
                    val total = intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L)
                    val speed = intent.getLongExtra(EXTRA_SPEED, 0L)
                    val currentFile = intent.getStringExtra(EXTRA_CURRENT_FILE) ?: ""

                    val progress = if (total > 0) ((transferred * 100) / total).toInt() else 0
                    val speedStr = NetworkUtils.formatSpeed(speed)
                    val transferredStr = "${NetworkUtils.formatBytes(transferred)} / ${NetworkUtils.formatBytes(total)}"

                    val notification = buildNotification(
                        progressPercent = progress,
                        contentTitle = "Transferring: $currentFile",
                        contentText = "$transferredStr • $speedStr • $progress%"
                    )
                    try {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Notification post failed", t)
                    }
                }
                ACTION_STOP -> {
                    safeStopForeground()
                    stopSelf()
                }
                ACTION_CANCEL -> {
                    try {
                        transferEngine.cancelTransfer()
                    } catch (_: Throwable) {}
                    safeStopForeground()
                    stopSelf()
                }
                else -> {
                    ensureForegroundStarted()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onStartCommand", t)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateProgressDirect(
        peerName: String,
        transferred: Long,
        total: Long,
        speed: Long,
        currentFile: String
    ) {
        this.peerName = peerName
        ensureForegroundStarted()
        val progress = if (total > 0) ((transferred * 100) / total).toInt() else 0
        val speedStr = NetworkUtils.formatSpeed(speed)
        val transferredStr = "${NetworkUtils.formatBytes(transferred)} / ${NetworkUtils.formatBytes(total)}"

        val notification = buildNotification(
            progressPercent = progress,
            contentTitle = "Transferring: $currentFile",
            contentText = "$transferredStr • $speedStr • $progress%"
        )
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "Notification post failed", t)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active file transfers"
                setShowBadge(false)
                enableVibration(false)
            }
            try {
                notificationManager.createNotificationChannel(channel)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed creating notification channel", t)
            }
        }
    }

    private fun buildNotification(
        progressPercent: Int,
        contentTitle: String,
        contentText: String
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, TransferForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(getString(R.string.notification_background_notice))
            .setSmallIcon(
                try {
                    R.drawable.ic_notification
                } catch (_: Throwable) {
                    android.R.drawable.ic_dialog_info
                }
            )
            .apply {
                if (brandLogo != null) {
                    setLargeIcon(brandLogo)
                }
            }
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, progressPercent == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .build()
    }

    private fun safeStopForeground() {
        releaseLocks()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
        isForegroundActive = false
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        releaseLocks()
        super.onDestroy()
    }
}

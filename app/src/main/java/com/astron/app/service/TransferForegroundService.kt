package com.astron.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.astron.app.transfer.TransferEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TransferForegroundService : Service() {

    @Inject
    lateinit var transferEngine: TransferEngine

    companion object {
        const val TAG = "TransferService"
        const val CHANNEL_ID = AstronNotificationHelper.CHANNEL_TRANSFERS
        const val NOTIFICATION_ID = AstronNotificationHelper.NOTIFICATION_ID_TRANSFER

        const val ACTION_START = "com.astron.app.action.START_TRANSFER"
        const val ACTION_UPDATE = "com.astron.app.action.UPDATE_PROGRESS"
        const val ACTION_STOP = "com.astron.app.action.STOP_TRANSFER"
        const val ACTION_CANCEL = "com.astron.app.action.CANCEL_TRANSFER"
        const val ACTION_PAUSE_RESUME = "com.astron.app.action.PAUSE_RESUME_TRANSFER"
        const val ACTION_TOGGLE_MUTE = "com.astron.app.action.TOGGLE_MUTE"

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

    private var lastTransferred = 0L
    private var lastTotal = 0L
    private var lastSpeed = 0L
    private var lastFile = "Preparing..."

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        AstronNotificationHelper.initChannels(this)
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
            val isPaused = try { transferEngine.isPaused.value } catch (_: Throwable) { false }
            val notification = AstronNotificationHelper.buildTransferNotification(
                context = this,
                peerName = peerName,
                currentFile = lastFile,
                transferred = lastTransferred,
                total = lastTotal,
                speedBps = lastSpeed,
                isPaused = isPaused
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
                    lastFile = "Connecting with $peerName..."
                    ensureForegroundStarted()
                }
                ACTION_UPDATE -> {
                    ensureForegroundStarted()
                    peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: peerName
                    lastTransferred = intent.getLongExtra(EXTRA_TRANSFERRED_BYTES, lastTransferred)
                    lastTotal = intent.getLongExtra(EXTRA_TOTAL_BYTES, lastTotal)
                    lastSpeed = intent.getLongExtra(EXTRA_SPEED, lastSpeed)
                    lastFile = intent.getStringExtra(EXTRA_CURRENT_FILE) ?: lastFile

                    val isPaused = try { transferEngine.isPaused.value } catch (_: Throwable) { false }
                    val notification = AstronNotificationHelper.buildTransferNotification(
                        context = this,
                        peerName = peerName,
                        currentFile = lastFile,
                        transferred = lastTransferred,
                        total = lastTotal,
                        speedBps = if (isPaused) 0L else lastSpeed,
                        isPaused = isPaused
                    )
                    try {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Notification post failed", t)
                    }
                }
                ACTION_PAUSE_RESUME -> {
                    try {
                        transferEngine.togglePause()
                        val isPaused = transferEngine.isPaused.value
                        val notification = AstronNotificationHelper.buildTransferNotification(
                            context = this,
                            peerName = peerName,
                            currentFile = lastFile,
                            transferred = lastTransferred,
                            total = lastTotal,
                            speedBps = if (isPaused) 0L else lastSpeed,
                            isPaused = isPaused
                        )
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error toggling pause from notification", t)
                    }
                }
                ACTION_TOGGLE_MUTE -> {
                    try {
                        val isMuted = com.astron.app.core.util.SoundFeedbackManager.toggleMute()
                        val isPaused = try { transferEngine.isPaused.value } catch (_: Throwable) { false }
                        val notification = AstronNotificationHelper.buildTransferNotification(
                            context = this,
                            peerName = peerName,
                            currentFile = lastFile,
                            transferred = lastTransferred,
                            total = lastTotal,
                            speedBps = if (isPaused) 0L else lastSpeed,
                            isPaused = isPaused,
                            isMuted = isMuted
                        )
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error toggling mute from notification", t)
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
        this.lastTransferred = transferred
        this.lastTotal = total
        this.lastSpeed = speed
        this.lastFile = currentFile

        ensureForegroundStarted()

        val isPaused = try { transferEngine.isPaused.value } catch (_: Throwable) { false }
        val notification = AstronNotificationHelper.buildTransferNotification(
            context = this,
            peerName = peerName,
            currentFile = currentFile,
            transferred = transferred,
            total = total,
            speedBps = if (isPaused) 0L else speed,
            isPaused = isPaused
        )
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "Notification post failed", t)
        }
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

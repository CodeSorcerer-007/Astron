package com.astron.app.core.util

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

object NetworkUtils {

    /**
     * Resolves the primary local IPv4 address on the active Wi-Fi or hotspot interface.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                // Prefer wlan, ap, or eth interfaces
                val name = intf.name.lowercase(Locale.ROOT)
                val isPreferred = name.contains("wlan") || name.contains("ap") || name.contains("eth") || name.contains("swlan")
                
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            if (isPreferred) return host
                        }
                    }
                }
            }

            // Fallback: any non-loopback IPv4
            val allInterfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in allInterfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Resolves human-friendly default device name (e.g. "Pixel 8 Pro", "Galaxy S24").
     */
    fun getDefaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * Formats bytes to human-readable string: e.g. "18.4 GB", "512 MB".
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return if (digitGroups == 0) {
            "%d %s".format(Locale.US, bytes, units[digitGroups])
        } else {
            "%.1f %s".format(Locale.US, value, units[digitGroups])
        }
    }

    /**
     * Formats throughput speed: e.g. "384 MB/s", "12.4 MB/s".
     */
    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 MB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    /**
     * Formats remaining time: e.g. "~19 seconds left", "~2 minutes left".
     */
    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "Calculating..."
        if (seconds < 60) return "~$seconds sec remaining"
        val minutes = seconds / 60
        val remainingSec = seconds % 60
        return if (minutes < 60) {
            "~$minutes min ${remainingSec}s remaining"
        } else {
            val hours = minutes / 60
            val remMin = minutes % 60
            "~$hours hr ${remMin}m remaining"
        }
    }
}

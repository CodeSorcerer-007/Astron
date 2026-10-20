package com.astron.app.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceCapabilities(
    val hasWifi: Boolean = false,
    val hasWifiDirect: Boolean = false,
    val hasWifiAware: Boolean = false,
    val hasBluetooth: Boolean = false,
    val hasBluetoothClassic: Boolean = false,
    val hasBle: Boolean = false,
    val hasNfc: Boolean = false,
    val hasCamera: Boolean = false,
    val hasInternet: Boolean = false,
    val isInternetMetered: Boolean = false,
    val hasLocalNetwork: Boolean = false,
    val isWifiConnected: Boolean = false,
    val isBatterySaverOn: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val networkType: NetworkType = NetworkType.NONE
)

enum class NetworkType { NONE, WIFI, CELLULAR_4G, CELLULAR_5G, ETHERNET }

/**
 * Detects available hardware capabilities at runtime.
 * Used by [TransportOrchestrator] to score and select the optimal transport.
 *
 * Never throws — all checks are guarded and fail gracefully.
 */
object CapabilityDetector {

    private const val TAG = "CapabilityDetector"

    private val _capabilities = MutableStateFlow(DeviceCapabilities())
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    /**
     * Refresh all capability flags. Call once on app start and again whenever
     * connectivity state changes (use ConnectivityManager.NetworkCallback).
     */
    fun refresh(context: Context): DeviceCapabilities {
        val caps = DeviceCapabilities(
            hasWifi = hasFeature(context, PackageManager.FEATURE_WIFI),
            hasWifiDirect = hasFeature(context, PackageManager.FEATURE_WIFI_DIRECT),
            hasWifiAware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hasFeature(context, PackageManager.FEATURE_WIFI_AWARE)
            } else false,
            hasBluetooth = hasFeature(context, PackageManager.FEATURE_BLUETOOTH),
            hasBluetoothClassic = hasFeature(context, PackageManager.FEATURE_BLUETOOTH),
            hasBle = hasFeature(context, PackageManager.FEATURE_BLUETOOTH_LE),
            hasNfc = hasFeature(context, PackageManager.FEATURE_NFC),
            hasCamera = hasFeature(context, PackageManager.FEATURE_CAMERA_ANY),
            hasInternet = hasActiveInternet(context),
            isInternetMetered = isInternetMetered(context),
            hasLocalNetwork = hasLocalNetworkReachable(context),
            isWifiConnected = isWifiCurrentlyConnected(context),
            isBatterySaverOn = isBatterySaverActive(context),
            isBluetoothEnabled = isBluetoothEnabled(context),
            isWifiEnabled = isWifiEnabled(context),
            networkType = detectNetworkType(context)
        )
        _capabilities.value = caps
        Log.d(TAG, "Capabilities refreshed: $caps")
        return caps
    }

    private fun hasFeature(context: Context, feature: String): Boolean = try {
        context.packageManager.hasSystemFeature(feature)
    } catch (e: Throwable) {
        Log.w(TAG, "Error checking feature $feature", e)
        false
    }

    private fun hasActiveInternet(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork ?: return false
        val netCaps = cm.getNetworkCapabilities(network) ?: return false
        netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Throwable) {
        false
    }

    private fun isInternetMetered(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.isActiveNetworkMetered ?: false
    } catch (e: Throwable) {
        false
    }

    private fun hasLocalNetworkReachable(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork ?: return false
        val netCaps = cm.getNetworkCapabilities(network) ?: return false
        netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } catch (e: Throwable) {
        false
    }

    private fun isWifiCurrentlyConnected(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val netCaps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
        netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    } catch (e: Throwable) {
        false
    }

    private fun isBatterySaverActive(context: Context): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isPowerSaveMode ?: false
    } catch (e: Throwable) {
        false
    }

    private fun isBluetoothEnabled(context: Context): Boolean = try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter?.isEnabled ?: false
    } catch (e: Throwable) {
        false
    }

    private fun isWifiEnabled(context: Context): Boolean = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wm?.isWifiEnabled ?: false
    } catch (e: Throwable) {
        false
    }

    private fun detectNetworkType(context: Context): NetworkType = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val netCaps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkType.NONE
        when {
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                    NetworkType.CELLULAR_5G
                } else {
                    NetworkType.CELLULAR_4G
                }
            }
            else -> NetworkType.NONE
        }
    } catch (e: Throwable) {
        NetworkType.NONE
    }
}

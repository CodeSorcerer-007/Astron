package com.astron.app.core.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import com.astron.app.core.model.PeerDevice
import com.astron.app.core.model.TransportType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

@Serializable
data class NfcPairingPayload(
    val deviceName: String,
    val hostIp: String,
    val port: Int,
    val sessionToken: String = "",
    val transportType: String = "LAN",
    val hotspotSsid: String? = null,
    val hotspotPass: String? = null
) {
    fun toPeerDevice(): PeerDevice {
        return PeerDevice(
            id = "$hostIp:$port",
            name = deviceName,
            hostAddress = hostIp,
            port = port,
            transportType = if (transportType == "P2P") TransportType.WIFI_DIRECT else TransportType.LOCAL_WIFI,
            model = "NFC Tap Paired",
            isTrusted = true,
            signalStrength = 100,
            hotspotSsid = hotspotSsid,
            hotspotPass = hotspotPass
        )
    }

    fun toJson(): String {
        return jsonConfig.encodeToString(this)
    }

    fun toNdefMessage(): NdefMessage {
        val mimeRecord = NdefRecord.createMime(
            MIME_TYPE,
            toJson().toByteArray(StandardCharsets.UTF_8)
        )
        return NdefMessage(arrayOf(mimeRecord))
    }

    companion object {
        const val MIME_TYPE = "application/vnd.com.astron.transfer"

        private val jsonConfig = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }

        fun fromJson(jsonStr: String): NfcPairingPayload? {
            return try {
                jsonConfig.decodeFromString<NfcPairingPayload>(jsonStr)
            } catch (_: Throwable) {
                null
            }
        }

        fun fromNdefMessage(message: NdefMessage): NfcPairingPayload? {
            val record = message.records?.firstOrNull {
                try {
                    it.toMimeType() == MIME_TYPE || String(it.type, StandardCharsets.UTF_8) == MIME_TYPE
                } catch (_: Throwable) {
                    false
                }
            } ?: return null

            return try {
                val jsonStr = String(record.payload, StandardCharsets.UTF_8)
                fromJson(jsonStr)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

object NfcManager {
    private const val TAG = "NfcManager"

    fun isNfcAvailable(context: Context): Boolean {
        return try {
            NfcAdapter.getDefaultAdapter(context) != null
        } catch (_: Throwable) {
            false
        }
    }

    fun isNfcEnabled(context: Context): Boolean {
        return try {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            adapter != null && adapter.isEnabled
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Extracts an NfcPairingPayload from an incoming Android NDEF intent.
     */
    fun parseIntent(intent: Intent?): NfcPairingPayload? {
        if (intent == null || intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
            return null
        }

        val rawMsgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, android.os.Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        } ?: return null

        for (msg in rawMsgs) {
            if (msg is NdefMessage) {
                val payload = NfcPairingPayload.fromNdefMessage(msg)
                if (payload != null) return payload
            }
        }
        return null
    }

    /**
     * Configures the activity to push an NDEF pairing message when tapped against another device.
     */
    fun setupNdefPush(activity: Activity, payload: NfcPairingPayload) {
        try {
            val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
            val method = adapter.javaClass.getMethod(
                "setNdefPushMessage",
                NdefMessage::class.java,
                Activity::class.java,
                Array<Activity>::class.java
            )
            method.invoke(adapter, payload.toNdefMessage(), activity, emptyArray<Activity>())
            Log.d(TAG, "NFC NDEF push payload configured for: ${payload.deviceName}")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed setting up NFC push: ${t.message}")
        }
    }

    /**
     * Clears any active NDEF push message.
     */
    fun clearNdefPush(activity: Activity) {
        try {
            val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
            val method = adapter.javaClass.getMethod(
                "setNdefPushMessage",
                NdefMessage::class.java,
                Activity::class.java,
                Array<Activity>::class.java
            )
            method.invoke(adapter, null, activity, emptyArray<Activity>())
        } catch (_: Throwable) {}
    }
}

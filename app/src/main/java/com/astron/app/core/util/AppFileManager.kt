package com.astron.app.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.astron.app.core.model.TransferItem
import java.io.File

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val apkFile: File,
    val size: Long,
    val icon: Drawable?
)

object AppFileManager {

    private const val TAG = "AppFileManager"

    fun formatFileSize(bytes: Long): String = NetworkUtils.formatBytes(bytes)

    /**
     * Lists all user-installed applications that can be shared as APK files.
     */
    fun getInstalledApps(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val result = mutableListOf<InstalledAppItem>()
        try {
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                // Include non-system apps, or updated system apps with a launch intent
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val hasLauncher = pm.getLaunchIntentForPackage(pkg.packageName) != null

                if ((!isSystem || isUpdatedSystem) && hasLauncher && pkg.packageName != context.packageName) {
                    val apkFile = File(appInfo.sourceDir)
                    if (apkFile.exists()) {
                        val appName = appInfo.loadLabel(pm).toString()
                        result.add(
                            InstalledAppItem(
                                packageName = pkg.packageName,
                                appName = appName,
                                apkFile = apkFile,
                                size = apkFile.length(),
                                icon = try { appInfo.loadIcon(pm) } catch (_: Throwable) { null }
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to query installed applications", e)
        }
        return result.sortedBy { it.appName.lowercase() }
    }

    /**
     * Converts an InstalledAppItem into a TransferItem for sending over socket.
     */
    fun appToTransferItem(app: InstalledAppItem): TransferItem {
        val safeName = app.appName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_") + ".apk"
        return TransferItem(
            id = "app-${app.packageName}",
            name = safeName,
            size = app.size,
            mimeType = "application/vnd.android.package-archive",
            uriString = Uri.fromFile(app.apkFile).toString(),
            relativePath = "Apps"
        )
    }

    /**
     * Safely opens a received file in the default Android viewer using FileProvider.
     */
    fun openFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val ext = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Cannot open file: ${file.name}", e)
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Safely shares a received file to external apps via the Android Sharesheet.
     */
    fun shareFile(context: Context, file: File) {
        if (!file.exists()) return
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val ext = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share ${file.name} via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Throwable) {
            Log.e(TAG, "Cannot share file: ${file.name}", e)
        }
    }

    /**
     * Returns total and free internal storage bytes on the primary volume.
     */
    fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val free = stat.availableBlocksLong * blockSize
            Pair(total, free)
        } catch (_: Throwable) {
            Pair(128L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024)
        }
    }

    /**
     * Opens the system Downloads folder where Astron saves received files.
     */
    fun openDownloadsFolder(context: Context) {
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val astronFolder = File(downloads, "Astron")
            if (!astronFolder.exists()) astronFolder.mkdirs()

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, astronFolder)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(context, "Downloads folder accessed via Files app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Finds the latest received file in the Astron downloads directory safely.
     */
    fun getLatestReceivedFile(): File? {
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val astronFolder = File(downloads, "Astron")
            if (!astronFolder.exists() || !astronFolder.isDirectory) return null
            astronFolder.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".partial") && !it.name.endsWith(".tmp") }
                ?.maxByOrNull { it.lastModified() }
        } catch (t: Throwable) {
            Log.w(TAG, "Storage access unavailable for latest file check: ${t.message}")
            null
        }
    }
}

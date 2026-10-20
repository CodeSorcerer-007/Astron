package com.astron.app.core.storage

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.astron.app.core.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

enum class FileCategory(val displayName: String) {
    RECENT("Recent"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCS("Documents"),
    APKS("Apps"),
    DOWNLOADS("Downloads"),
    LARGE("Large Files")
}

enum class FileSortOrder {
    DATE_DESC,
    DATE_ASC,
    SIZE_DESC,
    SIZE_ASC,
    NAME_ASC
}

data class BrowserFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri,
    val dateModified: Long,
    val category: FileCategory,
    val isAppPackage: Boolean = false,
    val packageName: String? = null,
    val appVersion: String? = null
) {
    fun toTransferItem(): TransferItem {
        return TransferItem(
            id = id,
            name = name,
            size = size,
            mimeType = mimeType,
            uri = uri,
            realFilePath = if (uri.scheme == "file") uri.path else null
        )
    }
}

@Singleton
class FileBrowserManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FileBrowserManager"
        private const val LARGE_FILE_THRESHOLD_BYTES = 100 * 1024 * 1024L // 100 MB
    }

    /**
     * Query files by category with optional search filter and sorting.
     */
    suspend fun loadCategoryFiles(
        category: FileCategory,
        searchQuery: String = "",
        sortOrder: FileSortOrder = FileSortOrder.DATE_DESC
    ): List<BrowserFileItem> = withContext(Dispatchers.IO) {
        val rawList = when (category) {
            FileCategory.RECENT -> queryRecentFiles()
            FileCategory.PHOTOS -> queryImages()
            FileCategory.VIDEOS -> queryVideos()
            FileCategory.AUDIO -> queryAudio()
            FileCategory.DOCS -> queryDocuments()
            FileCategory.APKS -> queryApksAndInstalledApps()
            FileCategory.DOWNLOADS -> queryDownloads()
            FileCategory.LARGE -> queryLargeFiles()
        }

        val filtered = if (searchQuery.isNotBlank()) {
            rawList.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            rawList
        }

        when (sortOrder) {
            FileSortOrder.DATE_DESC -> filtered.sortedByDescending { it.dateModified }
            FileSortOrder.DATE_ASC -> filtered.sortedBy { it.dateModified }
            FileSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.size }
            FileSortOrder.SIZE_ASC -> filtered.sortedBy { it.size }
            FileSortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    private fun queryImages(): List<BrowserFileItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        return queryMediaStore(
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection = projection,
            category = FileCategory.PHOTOS
        )
    }

    private fun queryVideos(): List<BrowserFileItem> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        return queryMediaStore(
            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection = projection,
            category = FileCategory.VIDEOS
        )
    }

    private fun queryAudio(): List<BrowserFileItem> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        return queryMediaStore(
            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection = projection,
            category = FileCategory.AUDIO
        )
    }

    private fun queryDocuments(): List<BrowserFileItem> {
        val docMimeTypes = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/zip"
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (${docMimeTypes.joinToString { "'$it'" }})"
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        return queryMediaStore(
            contentUri = MediaStore.Files.getContentUri("external"),
            projection = projection,
            selection = selection,
            category = FileCategory.DOCS
        )
    }

    private fun queryDownloads(): List<BrowserFileItem> {
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val items = mutableListOf<BrowserFileItem>()
        if (downloadFolder.exists() && downloadFolder.isDirectory) {
            downloadFolder.listFiles()?.forEach { file ->
                if (file.isFile) {
                    items.add(
                        BrowserFileItem(
                            id = file.absolutePath,
                            name = file.name,
                            size = file.length(),
                            mimeType = getMimeType(file.extension),
                            uri = Uri.fromFile(file),
                            dateModified = file.lastModified(),
                            category = FileCategory.DOWNLOADS
                        )
                    )
                }
            }
        }
        return items
    }

    private fun queryLargeFiles(): List<BrowserFileItem> {
        val selection = "${MediaStore.Files.FileColumns.SIZE} >= $LARGE_FILE_THRESHOLD_BYTES"
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        return queryMediaStore(
            contentUri = MediaStore.Files.getContentUri("external"),
            projection = projection,
            selection = selection,
            category = FileCategory.LARGE
        )
    }

    private fun queryRecentFiles(): List<BrowserFileItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        return queryMediaStore(
            contentUri = MediaStore.Files.getContentUri("external"),
            projection = projection,
            sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 50",
            category = FileCategory.RECENT
        )
    }

    private fun queryApksAndInstalledApps(): List<BrowserFileItem> {
        val list = mutableListOf<BrowserFileItem>()
        val pm = context.packageManager

        // 1. Installed non-system apps
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(0)
            } else {
                0
            }
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(flags as PackageManager.PackageInfoFlags)
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags as Int)
            }

            for (pkg in installedPackages) {
                val appInfo = pkg.applicationInfo ?: continue
                // Exclude system apps unless updated
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (isSystem) continue

                val sourceDir = appInfo.sourceDir ?: continue
                val apkFile = File(sourceDir)
                if (apkFile.exists()) {
                    val appLabel = pm.getApplicationLabel(appInfo).toString()
                    val versionName = pkg.versionName ?: "1.0"
                    list.add(
                        BrowserFileItem(
                            id = pkg.packageName,
                            name = "$appLabel-$versionName.apk",
                            size = apkFile.length(),
                            mimeType = "application/vnd.android.package-archive",
                            uri = Uri.fromFile(apkFile),
                            dateModified = apkFile.lastModified(),
                            category = FileCategory.APKS,
                            isAppPackage = true,
                            packageName = pkg.packageName,
                            appVersion = versionName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps", e)
        }

        return list
    }

    private fun queryMediaStore(
        contentUri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        category: FileCategory
    ): List<BrowserFileItem> {
        val results = mutableListOf<BrowserFileItem>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Unknown"
                    val size = it.getLong(sizeCol)
                    val mime = it.getString(mimeCol) ?: "application/octet-stream"
                    val date = it.getLong(dateCol) * 1000L // convert sec to ms
                    val itemUri = ContentUris.withAppendedId(contentUri, id)

                    if (size > 0) {
                        results.add(
                            BrowserFileItem(
                                id = id.toString(),
                                name = name,
                                size = size,
                                mimeType = mime,
                                uri = itemUri,
                                dateModified = date,
                                category = category
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying media store: $contentUri", e)
        }
        return results
    }

    private fun getMimeType(extension: String): String {
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
}

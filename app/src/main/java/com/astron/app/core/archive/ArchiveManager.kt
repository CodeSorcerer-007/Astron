package com.astron.app.core.archive

import android.content.Context
import android.net.Uri
import android.util.Log
import com.astron.app.core.model.TransferItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class ZipEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val time: Long
)

@Singleton
class ArchiveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ArchiveManager"
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_ZIP_BOMB_RATIO = 100 // 100x uncompressed size threshold
    }

    /**
     * Inspects contents of a ZIP file without extracting it.
     */
    suspend fun listZipEntries(zipFile: File): List<ZipEntryInfo> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ZipEntryInfo>()
        try {
            ZipFile(zipFile).use { zf ->
                val enumEntries = zf.entries()
                while (enumEntries.hasMoreElements()) {
                    val entry = enumEntries.nextElement()
                    entries.add(
                        ZipEntryInfo(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            isDirectory = entry.isDirectory,
                            time = entry.time
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading zip entries from $zipFile", e)
        }
        entries
    }

    /**
     * Creates a ZIP archive containing the provided items.
     */
    suspend fun createZip(
        outputZipFile: File,
        items: List<TransferItem>,
        onProgress: (bytesWritten: Long, currentFile: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var totalWritten = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            outputZipFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                zos.setLevel(6) // standard balance of speed & compression

                for (item in items) {
                    val entryName = if (item.relativePath.isNotBlank()) {
                        "${item.relativePath}/${item.name}".trimStart('/')
                    } else {
                        item.name
                    }

                    val entry = ZipEntry(entryName)
                    zos.putNextEntry(entry)

                    val inputStream = openItemInputStream(item) ?: continue
                    inputStream.use { inStream ->
                        var len: Int
                        while (inStream.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                            totalWritten += len
                            onProgress(totalWritten, item.name)
                        }
                    }
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating zip: ${outputZipFile.absolutePath}", e)
            outputZipFile.delete()
            false
        }
    }

    /**
     * Extracts a ZIP archive safely with path traversal protection and zip bomb detection.
     */
    suspend fun extractZip(
        zipInputStream: InputStream,
        targetDir: File,
        onProgress: (extractedBytes: Long, currentEntry: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalExtractedBytes = 0L
        var totalCompressedBytes = 0L

        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val canonicalTargetDirPath = targetDir.canonicalPath

            ZipInputStream(BufferedInputStream(zipInputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(targetDir, entry.name)
                    val canonicalEntryPath = entryFile.canonicalPath

                    // Path traversal (Zip Slip) security guard
                    if (!canonicalEntryPath.startsWith(canonicalTargetDirPath + File.separator) &&
                        canonicalEntryPath != canonicalTargetDirPath
                    ) {
                        Log.e(TAG, "Security Alert: Zip Slip path traversal attempt detected for: ${entry.name}")
                        throw SecurityException("Illegal zip entry path: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                                totalExtractedBytes += len
                                onProgress(totalExtractedBytes, entry.name)

                                // Zip Bomb guard: if ratio is absurdly high and size exceeds 500MB
                                if (entry.compressedSize > 0) {
                                    val ratio = totalExtractedBytes / entry.compressedSize
                                    if (ratio > MAX_ZIP_BOMB_RATIO && totalExtractedBytes > 500 * 1024 * 1024L) {
                                        throw IllegalStateException("Potential Zip Bomb detected. Extraction halted.")
                                    }
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting zip into $targetDir", e)
            false
        }
    }

    private fun openItemInputStream(item: TransferItem): InputStream? {
        return try {
            val uri = item.uri
            val path = item.realFilePath
            if (uri != null) {
                context.contentResolver.openInputStream(uri)
            } else if (path != null) {
                FileInputStream(File(path))
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open stream for ${item.name}", e)
            null
        }
    }
}

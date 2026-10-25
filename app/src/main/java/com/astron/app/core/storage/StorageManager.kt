package com.astron.app.core.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import com.astron.app.core.model.DuplicateAction
import com.astron.app.core.model.TransferItem
import com.astron.app.core.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

class StorageManager(private val context: Context) {

    private val contentResolver: ContentResolver? = try { context.contentResolver } catch (_: Exception) { null }

    var customDownloadDirectory: File? = null

    /**
     * Gets or creates the primary incoming storage directory (Downloads/Astron).
     */
    fun getAstronDownloadDirectory(): File {
        customDownloadDirectory?.let {
            if (!it.exists()) it.mkdirs()
            if (it.canWrite()) return it
        }
        val downloads = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (_: Throwable) { null }

        if (downloads != null) {
            val astronFolder = File(downloads, "Astron")
            if (!astronFolder.exists()) {
                try { astronFolder.mkdirs() } catch (_: Throwable) {}
            }
            if (astronFolder.exists() && astronFolder.canWrite()) {
                return astronFolder
            }
            if (downloads.exists() && downloads.canWrite()) {
                return downloads
            }
        }

        // Resilient Scoped Storage Fallback: App-specific external storage always has full read/write permission without system permissions
        val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appExternal != null) {
            val appAstron = File(appExternal, "Astron")
            if (!appAstron.exists()) {
                try { appAstron.mkdirs() } catch (_: Throwable) {}
            }
            if (appAstron.exists() && appAstron.canWrite()) {
                return appAstron
            }
            return appExternal
        }

        return context.filesDir
    }

    val downloadDirectory: File get() = getAstronDownloadDirectory()

    fun createDestinationFile(name: String, relativePath: String = ""): File {
        val baseDir = getAstronDownloadDirectory()
        val targetDir = if (relativePath.isNotBlank()) {
            val cleanRel = relativePath.trim('/').replace("..", "_")
            File(baseDir, cleanRel).also { it.mkdirs() }
        } else {
            baseDir
        }
        val sanitized = CryptoManager.sanitizeFilename(name)
        var file = File(targetDir, sanitized)
        var counter = 1
        val base = file.nameWithoutExtension
        val ext = file.extension.let { if (it.isNotBlank()) ".$it" else "" }
        while (file.exists()) {
            file = File(targetDir, "$base ($counter)$ext")
            counter++
        }
        return file
    }

    /**
     * Queries available free disk space on the target storage volume in bytes.
     */
    fun getAvailableStorageBytes(): Long {
        val path = getAstronDownloadDirectory()
        return try {
            val stat = StatFs(path.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            1024L * 1024L * 1024L * 10L // Fallback 10GB if unable to query
        }
    }

    /**
     * Resolves metadata for a picked URI (name, size, MIME type) safely.
     */
    fun queryTransferItem(uri: Uri): TransferItem {
        var name = "unknown_${System.currentTimeMillis()}"
        var size = 0L
        val mimeType = try { contentResolver?.getType(uri) } catch (_: Throwable) { null } ?: "application/octet-stream"

        try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            name = cursor.getString(nameIndex)
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    name = file.name
                    size = file.length()
                }
            }
        } catch (e: Throwable) {
            Log.w("StorageManager", "URI resolution fallback for $uri: ${e.message}")
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank()) {
                name = lastSegment.substringAfterLast('/')
            }
        }

        return TransferItem(
            id = UUID.randomUUID().toString(),
            name = CryptoManager.sanitizeFilename(name),
            size = size,
            mimeType = mimeType,
            uriString = uri.toString(),
            relativePath = ""
        )
    }

    /**
     * Opens a bounded InputStream for reading a file at a specific byte offset.
     * Essential for resuming interrupted transfers without retransmitting previous bytes.
     */
    fun openInputStreamAtOffset(uri: Uri, startOffset: Long): InputStream {
        val stream = try {
            when {
                uri.scheme == "file" -> File(uri.path ?: "").inputStream()
                uri.scheme == "content" -> contentResolver?.openInputStream(uri)
                uri.path != null && File(uri.path!!).exists() -> File(uri.path!!).inputStream()
                File(uri.toString()).exists() -> File(uri.toString()).inputStream()
                else -> contentResolver?.openInputStream(uri)
            }
        } catch (e: Throwable) {
            try {
                val path = uri.path ?: uri.toString()
                val f = File(path)
                if (f.exists()) f.inputStream() else null
            } catch (_: Throwable) {
                null
            }
        } ?: throw IllegalStateException("Cannot open input stream for $uri")

        if (startOffset > 0) {
            var skipped = 0L
            val scratch = ByteArray(8192)
            while (skipped < startOffset) {
                val toSkip = startOffset - skipped
                val n = stream.skip(toSkip)
                if (n > 0) {
                    skipped += n
                } else {
                    // Fallback to read when stream.skip returns 0
                    val bytesToRead = minOf(scratch.size.toLong(), toSkip).toInt()
                    val bytesRead = stream.read(scratch, 0, bytesToRead)
                    if (bytesRead == -1) break
                    skipped += bytesRead
                }
            }
            if (skipped < startOffset) {
                try { stream.close() } catch (_: Exception) {}
                throw java.io.EOFException("Cannot skip to requested offset: requested $startOffset, only reached $skipped")
            }
        }
        return stream
    }

    /**
     * Resolves the target destination File for an incoming file, handling relative folder hierarchy
     * and duplicate filename conflicts (REPLACE, KEEP_BOTH, SKIP).
     */
    suspend fun prepareDestinationFile(
        rawName: String,
        relativePath: String,
        duplicateAction: DuplicateAction,
        isResume: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        val baseDir = getAstronDownloadDirectory()
        val sanitizedName = CryptoManager.sanitizeFilename(rawName)
        
        val targetDir = if (relativePath.isNotBlank()) {
            val cleanRel = relativePath.trim('/').replace("..", "_")
            File(baseDir, cleanRel).also { it.mkdirs() }
        } else {
            baseDir
        }

        var candidate = File(targetDir, sanitizedName)

        if (candidate.exists()) {
            if (isResume) {
                // When resuming an interrupted file transfer, target the existing partial file directly
                return@withContext candidate
            }
            when (duplicateAction) {
                DuplicateAction.REPLACE -> {
                    // Ready to overwrite
                }
                DuplicateAction.KEEP_BOTH -> {
                    val baseName = candidate.nameWithoutExtension
                    val ext = candidate.extension.let { if (it.isNotBlank()) ".$it" else "" }
                    var counter = 1
                    while (candidate.exists()) {
                        candidate = File(targetDir, "$baseName ($counter)$ext")
                        counter++
                    }
                }
                DuplicateAction.SKIP -> {
                    // Marker: caller can check candidate.exists()
                }
            }
        }

        candidate
    }

    /**
     * Prepares an existing partial file for resumable write by returning its current length.
     */
    fun getExistingPartialLength(file: File): Long {
        return if (file.exists()) file.length() else 0L
    }

    /**
     * Scans the download directory for abandoned .partial or temporary transfer files older than maxAgeMillis
     * and safely deletes them to reclaim device storage.
     */
    fun cleanupOrphanedPartialFiles(maxAgeMillis: Long = 24 * 60 * 60 * 1000L): Int {
        val dir = getAstronDownloadDirectory()
        var cleaned = 0
        val now = System.currentTimeMillis()
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile && (file.name.endsWith(".partial") || file.name.endsWith(".tmp"))) {
                    if (now - file.lastModified() > maxAgeMillis) {
                        if (file.delete()) cleaned++
                    }
                }
            }
        } catch (_: Exception) {}
        return cleaned
    }

    /**
     * Appends bytes directly to a destination file using RandomAccessFile at a target offset.
     */
    suspend fun writeChunkAtOffset(file: File, offset: Long, data: ByteArray, length: Int) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data, 0, length)
        }
    }

    /**
     * Opens a high-performance write session for sequential chunk writing without reopening file handles.
     */
    fun openWriteSession(file: File, initialOffset: Long = 0L): FileWriteSession {
        file.parentFile?.mkdirs()
        val session = FileWriteSession(file)
        if (initialOffset > 0) {
            session.seekTo(initialOffset)
        }
        return session
    }

    class FileWriteSession(val file: File) : java.io.Closeable {
        init {
            file.parentFile?.mkdirs()
        }
        private val raf = RandomAccessFile(file, "rw")
        private var currentOffset = 0L

        @Synchronized
        fun writeChunk(data: ByteArray, length: Int) {
            raf.seek(currentOffset)
            raf.write(data, 0, length)
            currentOffset += length
        }

        @Synchronized
        fun seekTo(offset: Long) {
            currentOffset = offset
            raf.seek(offset)
        }

        override fun close() {
            try {
                raf.fd.sync()
            } catch (_: Exception) {}
            try {
                raf.close()
            } catch (_: Exception) {}
        }
    }
}

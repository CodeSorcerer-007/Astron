package com.astron.app.core.storage

import com.astron.app.core.model.DuplicateAction
import com.astron.app.core.model.ManifestItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class DuplicateDetectionResult(
    val hasDuplicate: Boolean,
    val existingFile: File?,
    val isExactHashMatch: Boolean
)

@Singleton
class DuplicateDetector @Inject constructor(
    private val storageManager: StorageManager
) {
    /**
     * Checks if a incoming manifest item already exists in the target storage directory.
     * Quick check compares name and file length. If lengths match, optionally does hash check.
     */
    suspend fun checkDuplicate(
        item: ManifestItem,
        deepHashCheck: Boolean = false
    ): DuplicateDetectionResult = withContext(Dispatchers.IO) {
        val destFile = File(storageManager.downloadDirectory, item.name)
        if (!destFile.exists()) {
            return@withContext DuplicateDetectionResult(
                hasDuplicate = false,
                existingFile = null,
                isExactHashMatch = false
            )
        }

        val sizeMatches = destFile.length() == item.size
        var hashMatches = false

        if (sizeMatches && deepHashCheck && item.checksumSha256 != null) {
            val localHash = computeSha256(destFile)
            hashMatches = localHash.equals(item.checksumSha256, ignoreCase = true)
        }

        DuplicateDetectionResult(
            hasDuplicate = true,
            existingFile = destFile,
            isExactHashMatch = hashMatches
        )
    }

    /**
     * Resolves the target destination file given a duplicate action policy.
     */
    fun resolveDestinationFile(
        originalName: String,
        action: DuplicateAction
    ): File? {
        val baseFile = File(storageManager.downloadDirectory, originalName)
        return when (action) {
            DuplicateAction.REPLACE -> {
                baseFile
            }
            DuplicateAction.KEEP_BOTH -> {
                storageManager.createDestinationFile(originalName)
            }
            DuplicateAction.SKIP -> {
                null
            }
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(32 * 1024)
            var len: Int
            while (stream.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

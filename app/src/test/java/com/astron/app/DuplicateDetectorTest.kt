package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.model.DuplicateAction
import com.astron.app.core.model.ManifestItem
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.storage.DuplicateDetector
import com.astron.app.core.storage.StorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DuplicateDetectorTest {

    private lateinit var tempDir: File
    private lateinit var storageManager: StorageManager
    private lateinit var duplicateDetector: DuplicateDetector

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): android.content.Context = this
        override fun getPackageName(): String = "com.astron.app"
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("astron_dup_test").toFile()
        storageManager = StorageManager(dummyContext).apply {
            customDownloadDirectory = tempDir
        }
        duplicateDetector = DuplicateDetector(storageManager)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCheckDuplicateReturnsFalseWhenFileDoesNotExist() = runBlocking {
        val item = ManifestItem(
            id = "item-1",
            name = "non_existent.txt",
            size = 100L,
            mimeType = "text/plain"
        )

        val result = duplicateDetector.checkDuplicate(item, deepHashCheck = true)
        assertFalse(result.hasDuplicate)
        assertNull(result.existingFile)
        assertFalse(result.isExactHashMatch)
    }

    @Test
    fun testCheckDuplicateReturnsTrueWhenFileExistsWithDifferentSize() = runBlocking {
        val file = File(tempDir, "existing.txt")
        file.writeText("Short content")

        val item = ManifestItem(
            id = "item-2",
            name = "existing.txt",
            size = 9999L,
            mimeType = "text/plain"
        )

        val result = duplicateDetector.checkDuplicate(item, deepHashCheck = true)
        assertTrue(result.hasDuplicate)
        assertNotNull(result.existingFile)
        assertFalse(result.isExactHashMatch)
    }

    @Test
    fun testCheckDuplicateMatchesExactHash() = runBlocking {
        val file = File(tempDir, "document.pdf")
        val content = "Astron God-Tier File Sharing Protocol Test Data"
        file.writeText(content)

        val sha256 = CryptoManager.computeSha256(file.inputStream())

        val matchingItem = ManifestItem(
            id = "item-3",
            name = "document.pdf",
            size = file.length(),
            mimeType = "application/pdf",
            checksumSha256 = sha256
        )

        val resultMatch = duplicateDetector.checkDuplicate(matchingItem, deepHashCheck = true)
        assertTrue(resultMatch.hasDuplicate)
        assertTrue(resultMatch.isExactHashMatch)

        val mismatchingItem = ManifestItem(
            id = "item-4",
            name = "document.pdf",
            size = file.length(),
            mimeType = "application/pdf",
            checksumSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
        )

        val resultMismatch = duplicateDetector.checkDuplicate(mismatchingItem, deepHashCheck = true)
        assertTrue(resultMismatch.hasDuplicate)
        assertFalse(resultMismatch.isExactHashMatch)
    }

    @Test
    fun testResolveDestinationFileActions() {
        val baseName = "photo.jpg"
        val existing = File(tempDir, baseName)
        existing.writeText("Existing image bytes")

        // 1. SKIP returns null
        val skipDest = duplicateDetector.resolveDestinationFile(baseName, DuplicateAction.SKIP)
        assertNull(skipDest)

        // 2. REPLACE returns baseFile
        val replaceDest = duplicateDetector.resolveDestinationFile(baseName, DuplicateAction.REPLACE)
        assertNotNull(replaceDest)
        assertEquals(existing.absolutePath, replaceDest?.absolutePath)

        // 3. KEEP_BOTH produces auto-numbered file
        val keepBothDest = duplicateDetector.resolveDestinationFile(baseName, DuplicateAction.KEEP_BOTH)
        assertNotNull(keepBothDest)
        assertEquals("photo (1).jpg", keepBothDest?.name)
    }
}

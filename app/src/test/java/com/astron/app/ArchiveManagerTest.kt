package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.archive.ArchiveManager
import com.astron.app.core.model.TransferItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveManagerTest {

    private lateinit var tempDir: File
    private lateinit var archiveManager: ArchiveManager

    private val dummyContext = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): android.content.Context = this
        override fun getPackageName(): String = "com.astron.app"
    }

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("astron_archive_test").toFile()
        archiveManager = ArchiveManager(dummyContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCreateAndListAndExtractZip() = runBlocking {
        val srcFile1 = File(tempDir, "file1.txt").apply { writeText("Hello Astron") }
        val srcFile2 = File(tempDir, "file2.txt").apply { writeText("God-Tier File Sharing") }

        val items = listOf(
            TransferItem(id = "1", name = "file1.txt", size = srcFile1.length(), mimeType = "text/plain", realFilePath = srcFile1.absolutePath),
            TransferItem(id = "2", name = "file2.txt", size = srcFile2.length(), mimeType = "text/plain", realFilePath = srcFile2.absolutePath)
        )

        val outputZip = File(tempDir, "bundle.zip")
        val created = archiveManager.createZip(outputZip, items) { _, _ -> }
        assertTrue(created)
        assertTrue(outputZip.exists())
        assertTrue(outputZip.length() > 0)

        val entries = archiveManager.listZipEntries(outputZip)
        assertEquals(2, entries.size)
        val entryNames = entries.map { it.name }
        assertTrue(entryNames.contains("file1.txt"))
        assertTrue(entryNames.contains("file2.txt"))

        val extractTarget = File(tempDir, "extracted")
        val extracted = archiveManager.extractZip(outputZip.inputStream(), extractTarget) { _, _ -> }
        assertTrue(extracted)

        val extractedFile1 = File(extractTarget, "file1.txt")
        val extractedFile2 = File(extractTarget, "file2.txt")
        assertTrue(extractedFile1.exists())
        assertTrue(extractedFile2.exists())
        assertEquals("Hello Astron", extractedFile1.readText())
        assertEquals("God-Tier File Sharing", extractedFile2.readText())
    }

    @Test
    fun testZipSlipPathTraversalBlocked() = runBlocking {
        // Construct a malicious in-memory zip with Zip Slip path traversal entry
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("../../malicious.txt")
            zos.putNextEntry(entry)
            zos.write("Malicious payload".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val maliciousZipBytes = baos.toByteArray()
        val extractTarget = File(tempDir, "safe_target")

        // ArchiveManager should catch the Zip Slip attempt and return false
        val result = archiveManager.extractZip(ByteArrayInputStream(maliciousZipBytes), extractTarget) { _, _ -> }
        assertFalse(result)

        val escapedFile = File(tempDir, "malicious.txt")
        assertFalse("Zip Slip escape file must NOT exist!", escapedFile.exists())
    }
}

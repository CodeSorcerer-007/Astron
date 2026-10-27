package com.astron.app

import com.astron.app.transport.webdrop.WebDropServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.astron.app.core.security.CryptoManager
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec

class WebDropSecurityTest {

    @Test
    fun testHtmlEscapeSpecialCharacters() {
        assertEquals("&amp;", WebDropServer.htmlEscape("&"))
        assertEquals("&lt;", WebDropServer.htmlEscape("<"))
        assertEquals("&gt;", WebDropServer.htmlEscape(">"))
        assertEquals("&quot;", WebDropServer.htmlEscape("\""))
        assertEquals("&#39;", WebDropServer.htmlEscape("'"))
    }

    @Test
    fun testHtmlEscapeScriptTagInjection() {
        val payload = "<script>alert('XSS')</script>"
        val sanitized = WebDropServer.htmlEscape(payload)

        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertFalse(sanitized.contains("'"))
        assertEquals("&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;", sanitized)
    }

    @Test
    fun testHtmlEscapeAttributeBreakoutVector() {
        val payload = "\"><img src=x onerror=\"alert('PWNED')\">"
        val sanitized = WebDropServer.htmlEscape(payload)

        assertFalse(sanitized.contains("\""))
        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertTrue(sanitized.startsWith("&quot;&gt;&lt;img"))
    }

    @Test
    fun testHtmlEscapePreservesSafeText() {
        val safeText = "Astron-Ultra-Device-2026_v1.0"
        assertEquals(safeText, WebDropServer.htmlEscape(safeText))

        val safeFilename = "document_backup.tar.gz"
        assertEquals(safeFilename, WebDropServer.htmlEscape(safeFilename))
    }

    @Test
    fun testHtmlEscapeEmptyAndWhitespace() {
        assertEquals("", WebDropServer.htmlEscape(""))
        assertEquals("   ", WebDropServer.htmlEscape("   "))
    }

    @Test
    fun testWebDropSessionTokenEntropy() {
        val token = CryptoManager.generateSessionToken(16)
        assertEquals(32, token.length) // 16 bytes = 32 hex chars = 128 bits
        assertTrue(token.matches(Regex("^[0-9a-f]{32}$")))
    }

    @Test
    fun testStreamingAesGcmDecryption() {
        val originalText = "Astron 100/100 Streaming AES-256-GCM verification payload across chunks"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)
        val token = CryptoManager.generateSessionToken(16)
        val secretKey = CryptoManager.deriveKeyFromToken(token)

        val encryptedPayload = CryptoManager.encryptAesGcm(plainBytes, secretKey)
        assertTrue(encryptedPayload.size >= 12 + 16)

        // Stream decrypt using CipherInputStream
        val bais = ByteArrayInputStream(encryptedPayload)
        val iv = ByteArray(12)
        val ivRead = bais.read(iv)
        assertEquals(12, ivRead)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        val cis = CipherInputStream(bais, cipher)
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(16) // Test with tiny buffer to ensure chunking works
        var r: Int
        while (cis.read(buf).also { r = it } != -1) {
            baos.write(buf, 0, r)
        }
        cis.close()

        val decryptedText = String(baos.toByteArray(), Charsets.UTF_8)
        assertEquals(originalText, decryptedText)
    }
}

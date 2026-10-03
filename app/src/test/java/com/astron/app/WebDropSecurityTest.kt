package com.astron.app

import com.astron.app.transport.webdrop.WebDropServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}

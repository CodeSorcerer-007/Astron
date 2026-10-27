package com.astron.app

import com.astron.app.transport.upnp.UpnpPortMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class UpnpPortMapperTest {

    @Test
    fun testBuildSoapAddPortMapping() {
        val xml = UpnpPortMapper.buildSoapAddPortMapping(
            internalIp = "192.168.1.100",
            internalPort = 52737,
            externalPort = 52737,
            description = "Astron-Unit-Test"
        )

        assertTrue(xml.contains("<s:Envelope"))
        assertTrue(xml.contains("<u:AddPortMapping"))
        assertTrue(xml.contains("<NewExternalPort>52737</NewExternalPort>"))
        assertTrue(xml.contains("<NewInternalPort>52737</NewInternalPort>"))
        assertTrue(xml.contains("<NewInternalClient>192.168.1.100</NewInternalClient>"))
        assertTrue(xml.contains("<NewProtocol>TCP</NewProtocol>"))
        assertTrue(xml.contains("<NewPortMappingDescription>Astron-Unit-Test</NewPortMappingDescription>"))
    }

    @Test
    fun testBuildSoapGetExternalIp() {
        val xml = UpnpPortMapper.buildSoapGetExternalIp()
        assertTrue(xml.contains("<s:Envelope"))
        assertTrue(xml.contains("<u:GetExternalIPAddress"))
    }

    @Test
    fun testExtractXmlTag() {
        val sampleSoapResponse = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                <s:Body>
                    <u:GetExternalIPAddressResponse xmlns:u="urn:schemas-upnp-org:service:WANIPConnection:1">
                        <NewExternalIPAddress>198.51.100.42</NewExternalIPAddress>
                    </u:GetExternalIPAddressResponse>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val extractedIp = UpnpPortMapper.extractXmlTag(sampleSoapResponse, "NewExternalIPAddress")
        assertEquals("198.51.100.42", extractedIp)

        val nonExistent = UpnpPortMapper.extractXmlTag(sampleSoapResponse, "NonExistentTag")
        assertNull(nonExistent)
    }

    @Test
    fun testExtractXmlTagWithWhitespace() {
        val xml = "<controlURL>  /ctl/IPConn  </controlURL>"
        val extracted = UpnpPortMapper.extractXmlTag(xml, "controlURL")
        assertEquals("/ctl/IPConn", extracted)
    }

    @Test
    fun testResolveControlUrlWithDefaultPort() {
        val baseUrl = URL("http://192.168.1.1/rootDesc.xml")
        assertEquals(-1, baseUrl.port)

        val resolved = UpnpPortMapper.resolveControlUrl(baseUrl, "/ctl/IPConn")
        assertEquals("http://192.168.1.1/ctl/IPConn", resolved)
    }

    @Test
    fun testResolveControlUrlWithExplicitPort() {
        val baseUrl = URL("http://192.168.1.1:49152/desc.xml")
        assertEquals(49152, baseUrl.port)

        val resolved = UpnpPortMapper.resolveControlUrl(baseUrl, "/ctl/IPConn")
        assertEquals("http://192.168.1.1:49152/ctl/IPConn", resolved)
    }

    @Test
    fun testResolveControlUrlWithAbsoluteControlUrl() {
        val baseUrl = URL("http://192.168.1.1:49152/desc.xml")
        val resolved = UpnpPortMapper.resolveControlUrl(baseUrl, "http://192.168.1.254:5000/ctl")
        assertEquals("http://192.168.1.254:5000/ctl", resolved)
    }
}

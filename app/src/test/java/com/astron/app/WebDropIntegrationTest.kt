package com.astron.app

import android.content.ContextWrapper
import com.astron.app.core.model.TransferItem
import com.astron.app.core.storage.StorageManager
import com.astron.app.transport.webdrop.WebDropServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class WebDropIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWebDropServerHttpEndpoints() = runBlocking {
        val dummyContext = object : ContextWrapper(null) {
            override fun getSystemService(name: String): Any? = null
            override fun getApplicationContext(): android.content.Context = this
            override fun getPackageName(): String = "com.astron.app"
        }
        val storageManager = StorageManager(dummyContext).apply {
            customDownloadDirectory = tempFolder.newFolder("downloads")
        }
        val server = WebDropServer(dummyContext, storageManager)

        // Find available port
        val testPort = 18090
        val serverReady = CompletableDeferred<Unit>()

        val serverJob = launch(Dispatchers.IO) {
            serverReady.complete(Unit)
            server.startServer(
                deviceName = "Astron-Test-Device",
                filesToShare = listOf(
                    TransferItem(
                        id = "test-item-1",
                        name = "sample.pdf",
                        size = 1024,
                        mimeType = "application/pdf",
                        uriString = "file:///sample.pdf"
                    )
                ),
                port = testPort,
                onFileUploaded = {}
            )
        }

        serverReady.await()
        delay(150) // Allow ServerSocket.bind() to settle

        try {
            withTimeout(10_000) {
                // 1. Test GET /
                Socket("127.0.0.1", testPort).use { socket ->
                    val out = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    out.write("GET / HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\n\r\n")
                    out.flush()

                    val statusLine = reader.readLine()
                    assertTrue("Status must be 200 OK: $statusLine", statusLine?.contains("200 OK") == true)

                    val responseLines = mutableListOf<String>()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        responseLines.add(line!!)
                    }
                    val body = responseLines.joinToString("\n")
                    assertTrue("Response must contain device name", body.contains("Astron-Test-Device"))
                    assertTrue("Response must contain CORS header", body.contains("Access-Control-Allow-Origin: *"))
                    assertTrue("Response must contain sample.pdf", body.contains("sample.pdf"))
                }

                // 2. Test GET /api/status
                Socket("127.0.0.1", testPort).use { socket ->
                    val out = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    out.write("GET /api/status HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\n\r\n")
                    out.flush()

                    val statusLine = reader.readLine()
                    assertTrue("Status must be 200 OK: $statusLine", statusLine?.contains("200 OK") == true)

                    val responseLines = mutableListOf<String>()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        responseLines.add(line!!)
                    }
                    val body = responseLines.joinToString("\n")
                    assertTrue("JSON must contain deviceName", body.contains("\"deviceName\""))
                    assertTrue("JSON must contain Astron-Test-Device", body.contains("Astron-Test-Device"))
                }

                // 3. Test OPTIONS /api/upload (CORS preflight)
                Socket("127.0.0.1", testPort).use { socket ->
                    val out = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    out.write("OPTIONS /api/upload HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\n\r\n")
                    out.flush()

                    val statusLine = reader.readLine()
                    assertTrue("Status must be 204 No Content: $statusLine", statusLine?.contains("204 No Content") == true)

                    val responseLines = mutableListOf<String>()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        responseLines.add(line!!)
                    }
                    val body = responseLines.joinToString("\n")
                    assertTrue("Must include Access-Control-Allow-Origin", body.contains("Access-Control-Allow-Origin: *"))
                    assertTrue("Must include Access-Control-Allow-Methods", body.contains("Access-Control-Allow-Methods"))
                }

                // 4. Test POST /api/upload with oversized Content-Length (Rejection with 413)
                Socket("127.0.0.1", testPort).use { socket ->
                    val out = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    // 12 GB exceeds MAX_UPLOAD_BYTES (10 GB)
                    val hugeLength = 12L * 1024 * 1024 * 1024
                    out.write("POST /api/upload HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\nContent-Length: $hugeLength\r\nX-Filename: large_iso.bin\r\n\r\n")
                    out.flush()

                    val statusLine = reader.readLine()
                    assertTrue("Status must be 413 Payload Too Large: $statusLine", statusLine?.contains("413 Payload Too Large") == true)
                }

                // 5. Test 404 for unknown path
                Socket("127.0.0.1", testPort).use { socket ->
                    val out = OutputStreamWriter(socket.getOutputStream())
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    out.write("GET /unknown/path HTTP/1.1\r\nHost: 127.0.0.1:$testPort\r\n\r\n")
                    out.flush()

                    val statusLine = reader.readLine()
                    assertTrue("Status must be 404 Not Found: $statusLine", statusLine?.contains("404 Not Found") == true)
                }
            }
        } finally {
            server.stopServer()
            serverJob.cancel()
        }
    }
}

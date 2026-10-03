package com.astron.app.transport.webdrop

import android.content.Context
import android.net.Uri
import android.util.Log
import com.astron.app.core.model.TransferItem
import com.astron.app.core.model.TransferManifest
import com.astron.app.core.security.CryptoManager
import com.astron.app.core.storage.StorageManager
import com.astron.app.core.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID

class WebDropServer(
    private val context: Context,
    private val storageManager: StorageManager
) {
    companion object {
        const val TAG = "WebDropServer"
        const val DEFAULT_PORT = 8080
        const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB

        fun htmlEscape(s: String): String =
            s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var executor: java.util.concurrent.ExecutorService? = null

    private var sharedFiles: List<TransferItem> = emptyList()
    private var sessionToken: String = ""
    private var deviceName: String = ""

    val webDropUrl: String?
        get() {
            val ip = NetworkUtils.getLocalIpAddress() ?: return null
            val port = serverSocket?.localPort ?: DEFAULT_PORT
            return "http://$ip:$port?token=$sessionToken"
        }

    val displayUrl: String?
        get() {
            val ip = NetworkUtils.getLocalIpAddress() ?: return null
            val port = serverSocket?.localPort ?: DEFAULT_PORT
            return "http://$ip:$port"
        }

    /**
     * Starts the embedded Web Drop HTTP server.
     */
    suspend fun startServer(
        deviceName: String,
        filesToShare: List<TransferItem>,
        port: Int = DEFAULT_PORT,
        onFileUploaded: (uploadedFile: File) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (isRunning) stopServer()

        this@WebDropServer.deviceName = deviceName
        this@WebDropServer.sharedFiles = filesToShare
        this@WebDropServer.sessionToken = CryptoManager.generateSessionToken(8)
        this@WebDropServer.executor = java.util.concurrent.Executors.newFixedThreadPool(4)

        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            isRunning = true
            Log.d(TAG, "WebDrop server running at: $webDropUrl")

            while (isRunning) {
                val client = try {
                    serverSocket?.accept() ?: break
                } catch (_: Exception) {
                    break
                }

                // Handle HTTP request with bounded thread pool
                executor?.execute {
                    handleClientRequest(client, onFileUploaded)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebDrop server error", e)
        } finally {
            stopServer()
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {}
        executor = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClientRequest(client: Socket, onFileUploaded: (File) -> Unit) {
        try {
            client.soTimeout = 30_000
            val inStream = BufferedInputStream(client.getInputStream())
            val outStream = BufferedOutputStream(client.getOutputStream())

            val reader = BufferedReader(InputStreamReader(inStream, Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8")

            // Read HTTP headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (!reader.readLine().also { line = it }.isNullOrBlank()) {
                val currentLine = line ?: break
                val separator = currentLine.indexOf(':')
                if (separator > 0) {
                    val key = currentLine.substring(0, separator).trim().lowercase()
                    val value = currentLine.substring(separator + 1).trim()
                    headers[key] = value
                }
            }

            if (method == "OPTIONS") {
                send204NoContent(outStream)
                return
            }

            if (method == "GET") {
                when {
                    path == "/" -> serveWebClientHtml(outStream)
                    path == "/api/status" -> {
                        val safeDevice = htmlEscape(deviceName)
                        val json = "{\"deviceName\":\"$safeDevice\",\"filesCount\":${sharedFiles.size}}"
                        send200Json(outStream, json)
                    }
                    path.startsWith("/download/") -> {
                        val fileId = path.removePrefix("/download/")
                        val item = sharedFiles.find { it.id == fileId }
                        if (item != null) {
                            serveFileDownload(item, outStream)
                        } else {
                            send404(outStream)
                        }
                    }
                    else -> send404(outStream)
                }
            } else if (method == "POST" && (path == "/upload" || path == "/api/upload")) {
                val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                if (contentLength > MAX_UPLOAD_BYTES) {
                    send413(outStream, "File exceeds 10 GB limit")
                    return
                }

                val rawFileName = headers["x-filename"] ?: "upload_${System.currentTimeMillis()}"
                val fileName = CryptoManager.sanitizeFilename(URLDecoder.decode(rawFileName, "UTF-8"))

                val targetFile = File(storageManager.getAstronDownloadDirectory(), fileName)
                targetFile.parentFile?.mkdirs()
                targetFile.outputStream().use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRemaining = contentLength
                    while (bytesRemaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                        val read = inStream.read(buffer, 0, toRead)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        bytesRemaining -= read
                    }
                }

                onFileUploaded(targetFile)
                send200Json(outStream, "{\"status\":\"ok\",\"file\":\"${targetFile.name}\"}")
            } else {
                send404(outStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WebDrop HTTP request", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun corsHeaders(): String =
        "Access-Control-Allow-Origin: *\r\n" +
        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
        "Access-Control-Allow-Headers: X-Filename, Content-Type, Content-Length\r\n"


    private fun serveWebClientHtml(out: OutputStream) {
        val safeDeviceName = htmlEscape(deviceName)
        val fileCards = sharedFiles.joinToString("\n") { file ->
            val safeFileName = htmlEscape(file.name)
            val safeMime = htmlEscape(file.mimeType)
            """
            <div class="card">
                <div class="file-info">
                    <div class="file-name">$safeFileName</div>
                    <div class="file-meta">${NetworkUtils.formatBytes(file.size)} • $safeMime</div>
                </div>
                <a href="/download/${file.id}" class="btn-download" download="$safeFileName">Download</a>
            </div>
            """.trimIndent()
        }

        val html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Astron Web Drop — $safeDeviceName</title>
            <style>
                :root {
                    --bg-dark: #070A12;
                    --surface: #0E1424;
                    --surface-hover: #162038;
                    --cyan: #00E5FF;
                    --blue: #3B82F6;
                    --text: #F1F5F9;
                    --text-muted: #94A3B8;
                    --border: rgba(0, 229, 255, 0.15);
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background: var(--bg-dark); color: var(--text); padding: 24px 16px; min-height: 100vh; display: flex; flex-direction: column; align-items: center; }
                .container { width: 100%; max-width: 680px; }
                header { text-align: center; margin-bottom: 32px; }
                .logo-ring { width: 64px; height: 64px; border-radius: 20px; background: linear-gradient(135deg, var(--cyan), var(--blue)); display: inline-flex; align-items: center; justify-content: center; font-size: 28px; font-weight: 800; color: #070A12; margin-bottom: 12px; box-shadow: 0 0 30px rgba(0,229,255,0.4); }
                h1 { font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }
                p.sub { color: var(--text-muted); font-size: 14px; margin-top: 4px; }
                .badge { display: inline-block; padding: 4px 12px; border-radius: 999px; background: rgba(0,229,255,0.1); border: 1px solid var(--border); color: var(--cyan); font-size: 12px; font-weight: 600; margin-top: 8px; }
                .section-title { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; color: var(--cyan); margin: 24px 0 12px 0; }
                .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 16px 20px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; transition: transform 0.15s ease, border-color 0.15s ease; }
                .card:hover { border-color: rgba(0,229,255,0.4); transform: translateY(-1px); }
                .file-info { flex: 1; min-width: 0; padding-right: 16px; }
                .file-name { font-weight: 600; font-size: 15px; word-break: break-all; }
                .file-meta { color: var(--text-muted); font-size: 13px; margin-top: 4px; }
                .btn-download { background: linear-gradient(135deg, var(--cyan), var(--blue)); color: #070A12; text-decoration: none; padding: 10px 18px; border-radius: 12px; font-weight: 700; font-size: 13px; white-space: nowrap; transition: opacity 0.15s ease; }
                .btn-download:hover { opacity: 0.9; }
                .upload-box { background: var(--surface); border: 2px dashed var(--border); border-radius: 20px; padding: 36px 20px; text-align: center; cursor: pointer; transition: all 0.2s ease; margin-top: 12px; }
                .upload-box:hover { border-color: var(--cyan); background: var(--surface-hover); }
                .upload-icon { font-size: 36px; margin-bottom: 8px; color: var(--cyan); }
                .upload-text { font-size: 15px; font-weight: 600; }
                .upload-sub { color: var(--text-muted); font-size: 13px; margin-top: 4px; }
                #fileInput { display: none; }
                .progress-bar { width: 100%; height: 6px; background: rgba(255,255,255,0.1); border-radius: 3px; overflow: hidden; margin-top: 12px; display: none; }
                .progress-fill { width: 0%; height: 100%; background: var(--cyan); transition: width 0.1s linear; }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <div class="logo-ring">A</div>
                    <h1>Astron Web Drop</h1>
                    <p class="sub">Connected to <strong>$safeDeviceName</strong></p>
                    <div class="badge">Direct P2P Encrypted Session</div>
                </header>

                <div class="section-title">Files Shared by Sender</div>
                ${if (sharedFiles.isEmpty()) "<p style='color:var(--text-muted);font-size:14px;'>No files queued for download right now. You can send files to the phone below.</p>" else fileCards}

                <div class="section-title">Send Files to Phone</div>
                <div class="upload-box" id="uploadBox" onclick="document.getElementById('fileInput').click()">
                    <div class="upload-icon">↑</div>
                    <div class="upload-text">Click or drag files here to send</div>
                    <div class="upload-sub">Files will be saved directly to $safeDeviceName</div>
                    <input type="file" id="fileInput" multiple onchange="handleFiles(this.files)">
                    <div class="progress-bar" id="progressBar"><div class="progress-fill" id="progressFill"></div></div>
                </div>
            </div>

            <script>
                const uploadBox = document.getElementById('uploadBox');
                if (uploadBox) {
                    ['dragenter', 'dragover'].forEach(eventName => {
                        uploadBox.addEventListener(eventName, (e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            uploadBox.style.borderColor = 'var(--cyan)';
                            uploadBox.style.background = 'var(--surface-hover)';
                        }, false);
                    });
                    ['dragleave', 'drop'].forEach(eventName => {
                        uploadBox.addEventListener(eventName, (e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            uploadBox.style.borderColor = 'var(--border)';
                            uploadBox.style.background = 'var(--surface)';
                        }, false);
                    });
                    uploadBox.addEventListener('drop', (e) => {
                        const dt = e.dataTransfer;
                        if (dt && dt.files && dt.files.length > 0) {
                            handleFiles(dt.files);
                        }
                    }, false);
                }

                function handleFiles(files) {
                    if (!files || files.length === 0) return;
                    const bar = document.getElementById('progressBar');
                    const fill = document.getElementById('progressFill');
                    bar.style.display = 'block';

                    let uploaded = 0;
                    Array.from(files).forEach((file, index) => {
                        const xhr = new XMLHttpRequest();
                        xhr.open('POST', '/upload', true);
                        xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
                        xhr.upload.onprogress = (e) => {
                            if (e.lengthComputable) {
                                const percent = Math.round((e.loaded / e.total) * 100);
                                fill.style.width = percent + '%';
                            }
                        };
                        xhr.onload = () => {
                            uploaded++;
                            if (uploaded === files.length) {
                                alert('All files uploaded successfully!');
                                bar.style.display = 'none';
                                fill.style.width = '0%';
                            }
                        };
                        xhr.send(file);
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                corsHeaders() +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun serveFileDownload(item: TransferItem, out: OutputStream) {
        try {
            val uri = Uri.parse(item.uriString)
            val stream = try {
                storageManager.openInputStreamAtOffset(uri, 0L)
            } catch (t: Throwable) {
                send404(out)
                return
            }
            val header = "HTTP/1.1 200 OK\r\n" +
                    corsHeaders() +
                    "Content-Type: ${item.mimeType}\r\n" +
                    "Content-Length: ${item.size}\r\n" +
                    "Content-Disposition: attachment; filename=\"${item.name}\"\r\n" +
                    "Connection: close\r\n\r\n"
            out.write(header.toByteArray(Charsets.UTF_8))

            val buffer = ByteArray(64 * 1024)
            var read: Int
            stream.use { input ->
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
            }
            out.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming file download", e)
        }
    }

    private fun send204NoContent(out: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                corsHeaders() +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun send413(out: OutputStream, message: String) {
        val body = "413 Payload Too Large: $message"
        val header = "HTTP/1.1 413 Payload Too Large\r\n" +
                corsHeaders() +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n"
        out.write((header + body).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun send404(out: OutputStream) {
        val body = "404 Not Found"
        val header = "HTTP/1.1 404 Not Found\r\n" +
                corsHeaders() +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n"
        out.write((header + body).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun send200Json(out: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                corsHeaders() +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}

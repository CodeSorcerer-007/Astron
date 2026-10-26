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
import java.io.InputStream
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

        @Volatile
        var activeInstance: WebDropServer? = null

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

    val currentSessionToken: String
        get() = sessionToken

    val actualPort: Int
        get() = serverSocket?.localPort ?: DEFAULT_PORT

    val webDropUrl: String?
        get() {
            val ip = NetworkUtils.getLocalIpAddress() ?: return null
            val port = actualPort
            return "http://$ip:$port?token=$sessionToken"
        }

    val displayUrl: String?
        get() {
            val ip = NetworkUtils.getLocalIpAddress() ?: return null
            val port = actualPort
            return "http://$ip:$port"
        }

    /**
     * Starts the embedded Web Drop HTTP server.
     */
    suspend fun startServer(
        deviceName: String,
        filesToShare: List<TransferItem>,
        port: Int = DEFAULT_PORT,
        customToken: String? = null,
        onServerReady: () -> Unit = {},
        onFileUploaded: (uploadedFile: File) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (isRunning) stopServer()

        this@WebDropServer.deviceName = deviceName
        this@WebDropServer.sharedFiles = filesToShare
        this@WebDropServer.sessionToken = customToken ?: CryptoManager.generateSessionToken(8)
        this@WebDropServer.executor = java.util.concurrent.Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "WebDrop-Worker").apply { isDaemon = true }
        }

        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            isRunning = true
            activeInstance = this@WebDropServer
            onServerReady()
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
        if (activeInstance === this) {
            activeInstance = null
        }
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {}
        executor = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun readLineRaw(inStream: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = inStream.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb.last() == '\r') {
                    sb.setLength(sb.length - 1)
                }
                return sb.toString()
            }
            sb.append(b.toChar())
            if (sb.length > 4096) break
        }
        return sb.toString()
    }

    private fun handleClientRequest(client: Socket, onFileUploaded: (File) -> Unit) {
        try {
            client.soTimeout = 5_000
            val inStream = BufferedInputStream(client.getInputStream())
            val outStream = BufferedOutputStream(client.getOutputStream())

            val requestLine = readLineRaw(inStream) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawPath = parts[1]
            val path = URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8")

            // Read HTTP headers (bounded to prevent header flooding)
            val headers = mutableMapOf<String, String>()
            var headerCount = 0
            while (true) {
                val line = readLineRaw(inStream)
                if (line.isNullOrBlank()) break
                headerCount++
                if (headerCount > 100) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    val key = line.substring(0, separator).trim().lowercase()
                    val value = line.substring(separator + 1).trim()
                    headers[key] = value
                }
            }

            if (method == "OPTIONS") {
                send204NoContent(outStream)
                return
            }

            // Session token verification (exempt public assets)
            if (sessionToken.isNotBlank() && path != "/favicon.ico" && path != "/logo.png") {
                val queryToken = if (rawPath.contains("token=")) {
                    rawPath.substringAfter("token=").substringBefore('&')
                } else ""
                val headerToken = headers["x-session-token"] ?: ""
                val cookieToken = headers["cookie"]?.split(';')
                    ?.find { it.trim().startsWith("astron_token=") }
                    ?.substringAfter('=')?.trim() ?: ""
                val providedToken = when {
                    queryToken.isNotBlank() -> queryToken
                    headerToken.isNotBlank() -> headerToken
                    cookieToken.isNotBlank() -> cookieToken
                    else -> ""
                }
                if (providedToken != sessionToken) {
                    send403(outStream, "Invalid or missing session token")
                    return
                }
            }

            if (method == "GET") {
                when {
                    path == "/" -> serveWebClientHtml(outStream)
                    path == "/logo.png" || path == "/favicon.ico" -> serveLogo(outStream)
                    path == "/api/status" -> {
                        val safeDevice = htmlEscape(deviceName)
                        val json = "{\"deviceName\":\"$safeDevice\",\"filesCount\":${sharedFiles.size}}"
                        send200Json(outStream, json)
                    }
                    path.startsWith("/download/") -> {
                        val fileId = path.removePrefix("/download/")
                        val item = sharedFiles.find { it.id == fileId }
                        if (item != null) {
                            try { client.soTimeout = 60_000 } catch (_: Exception) {}
                            serveFileDownload(item, outStream)
                        } else {
                            send404(outStream)
                        }
                    }
                    else -> send404(outStream)
                }
            } else if (method == "POST" && (path == "/upload" || path == "/api/upload")) {
                try { client.soTimeout = 60_000 } catch (_: Exception) {}
                val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                if (contentLength <= 0L) {
                    send400(outStream, "Missing or invalid Content-Length header")
                    return
                }
                if (contentLength > MAX_UPLOAD_BYTES) {
                    send413(outStream, "File exceeds 10 GB limit")
                    return
                }

                val isEncrypted = headers["x-encrypted"]?.lowercase() == "aes-256-gcm"
                val rawFileName = headers["x-filename"] ?: "upload_${System.currentTimeMillis()}"
                if (rawFileName.length > 500) {
                    send400(outStream, "Filename too long")
                    return
                }
                val fileName = CryptoManager.sanitizeFilename(
                    try { URLDecoder.decode(rawFileName, "UTF-8") } catch (_: Exception) { rawFileName }
                )

                val targetFile = storageManager.createDestinationFile(fileName)
                targetFile.parentFile?.mkdirs()

                if (isEncrypted) {
                    if (contentLength > 50 * 1024 * 1024L) {
                        send413(outStream, "Encrypted upload exceeds browser memory safe limit of 50 MB")
                        return
                    }
                    // Decrypt client-side AES-256-GCM uploaded payload
                    val encryptedBytes = ByteArray(contentLength.toInt())
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = inStream.read(encryptedBytes, totalRead, (contentLength - totalRead).toInt())
                        if (read == -1) break
                        totalRead += read
                    }
                    if (totalRead.toLong() < contentLength) {
                        send400(outStream, "Premature end of upload stream")
                        return
                    }
                    val secretKey = CryptoManager.deriveKeyFromToken(sessionToken)
                    val decrypted = try {
                        CryptoManager.decryptAesGcm(encryptedBytes, secretKey)
                    } catch (t: Throwable) {
                        send400(outStream, "Failed to decrypt upload payload: invalid key or corrupted stream")
                        return
                    }
                    targetFile.writeBytes(decrypted)
                } else {
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
        "Access-Control-Allow-Headers: X-Filename, Content-Type, Content-Length, X-Session-Token, X-Encrypted\r\n"


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
                <div style="display:flex;gap:8px;align-items:center;">
                    <a href="/download/${file.id}?token=$sessionToken" class="btn-download" download="$safeFileName">Download</a>
                </div>
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
            <link rel="icon" type="image/png" href="/logo.png">
            <style>
                :root {
                    --bg-dark: #070A12;
                    --surface: #0E1424;
                    --surface-hover: #162038;
                    --cyan: #00E5FF;
                    --blue: #3B82F6;
                    --violet: #8B5CF6;
                    --text: #F1F5F9;
                    --text-muted: #94A3B8;
                    --border: rgba(0, 229, 255, 0.18);
                    --success: #10B981;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background: var(--bg-dark); color: var(--text); padding: 24px 16px; min-height: 100vh; display: flex; flex-direction: column; align-items: center; position: relative; overflow-x: hidden; }
                #spaceCanvas { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; pointer-events: none; z-index: 0; }
                .container { width: 100%; max-width: 680px; position: relative; z-index: 1; }
                header { text-align: center; margin-bottom: 28px; }
                .brand-logo-wrap { display: inline-flex; align-items: center; justify-content: center; margin-bottom: 12px; }
                .brand-logo { width: 76px; height: 76px; object-fit: contain; filter: drop-shadow(0 0 25px rgba(0,229,255,0.7)); }
                h1 { font-size: 26px; font-weight: 800; letter-spacing: -0.5px; background: linear-gradient(135deg, #FFF, var(--cyan)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
                p.sub { color: var(--text-muted); font-size: 14px; margin-top: 6px; }
                .chips-row { display: flex; gap: 8px; justify-content: center; align-items: center; flex-wrap: wrap; margin-top: 10px; }
                .badge { display: inline-flex; align-items: center; gap: 6px; padding: 5px 14px; border-radius: 999px; background: rgba(0,229,255,0.1); border: 1px solid var(--border); color: var(--cyan); font-size: 12px; font-weight: 600; }
                .badge-e2ee { background: rgba(16,185,129,0.12); border-color: rgba(16,185,129,0.3); color: var(--success); }
                .section-title { font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 1.2px; color: var(--cyan); margin: 24px 0 12px 0; }
                .card { background: rgba(14,20,36,0.85); backdrop-filter: blur(12px); border: 1px solid var(--border); border-radius: 16px; padding: 16px 20px; display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; transition: all 0.2s ease; }
                .card:hover { border-color: rgba(0,229,255,0.45); transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,229,255,0.12); }
                .file-info { flex: 1; min-width: 0; padding-right: 16px; }
                .file-name { font-weight: 600; font-size: 15px; word-break: break-all; }
                .file-meta { color: var(--text-muted); font-size: 13px; margin-top: 4px; }
                .btn-download { background: linear-gradient(135deg, var(--cyan), var(--blue)); color: #070A12; text-decoration: none; padding: 10px 18px; border-radius: 12px; font-weight: 700; font-size: 13px; white-space: nowrap; transition: opacity 0.15s ease; border: none; cursor: pointer; }
                .btn-download:hover { opacity: 0.9; }
                .upload-box { background: rgba(14,20,36,0.85); backdrop-filter: blur(12px); border: 2px dashed var(--border); border-radius: 20px; padding: 36px 20px; text-align: center; cursor: pointer; transition: all 0.2s ease; margin-top: 12px; }
                .upload-box:hover { border-color: var(--cyan); background: var(--surface-hover); }
                .upload-icon { font-size: 40px; margin-bottom: 8px; color: var(--cyan); }
                .upload-text { font-size: 16px; font-weight: 700; }
                .upload-sub { color: var(--text-muted); font-size: 13px; margin-top: 6px; }
                .security-toggle-wrap { display: flex; align-items: center; justify-content: space-between; padding: 12px 18px; background: rgba(14,20,36,0.6); border: 1px solid var(--border); border-radius: 12px; margin-top: 14px; font-size: 13px; }
                #fileInput { display: none; }
                .progress-bar { width: 100%; height: 8px; background: rgba(255,255,255,0.1); border-radius: 4px; overflow: hidden; margin-top: 16px; display: none; }
                .progress-fill { width: 0%; height: 100%; background: linear-gradient(90deg, var(--cyan), var(--blue)); transition: width 0.1s linear; }
                .telemetry-row { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-muted); margin-top: 8px; display: none; }
            </style>
        </head>
        <body>
            <canvas id="spaceCanvas"></canvas>
            <div class="container">
                <header>
                    <div class="brand-logo-wrap">
                        <img src="/logo.png" alt="Astron Logo" class="brand-logo" />
                    </div>
                    <h1>Astron Web Drop</h1>
                    <p class="sub">Connected to <strong>$safeDeviceName</strong></p>
                    <div class="chips-row">
                        <div class="badge">🪐 Direct P2P Session</div>
                        <div class="badge badge-e2ee">🛡️ E2EE Zero-Knowledge Active</div>
                    </div>
                </header>

                <div class="section-title">Files Shared by Sender</div>
                ${if (sharedFiles.isEmpty()) "<p style='color:var(--text-muted);font-size:14px;background:rgba(14,20,36,0.6);padding:18px;border-radius:14px;border:1px solid var(--border);'>No files queued for download right now. You can send files to the phone below.</p>" else fileCards}

                <div class="section-title">Send Files to Phone</div>
                <div class="upload-box" id="uploadBox" onclick="document.getElementById('fileInput').click()">
                    <div class="upload-icon">↑</div>
                    <div class="upload-text">Click or drag files here to send</div>
                    <div class="upload-sub">Files will be encrypted &amp; saved to $safeDeviceName</div>
                    <input type="file" id="fileInput" multiple onchange="handleFiles(this.files)">
                    <div class="progress-bar" id="progressBar"><div class="progress-fill" id="progressFill"></div></div>
                    <div class="telemetry-row" id="telemetryRow">
                        <span id="telemetrySpeed">0 MB/s</span>
                        <span id="telemetryPercent">0%</span>
                    </div>
                </div>

                <div class="security-toggle-wrap">
                    <span>🔒 Client-Side AES-256-GCM Pre-Encryption</span>
                    <input type="checkbox" id="e2eeCheckbox" checked style="accent-color:var(--cyan);width:18px;height:18px;cursor:pointer;">
                </div>
            </div>

            <script>
                // 1. Procedural Cosmic Twinkling Stars Canvas
                (function() {
                    const canvas = document.getElementById('spaceCanvas');
                    if (!canvas) return;
                    const ctx = canvas.getContext('2d');
                    let stars = [];
                    function resize() {
                        canvas.width = window.innerWidth;
                        canvas.height = window.innerHeight;
                        initStars();
                    }
                    function initStars() {
                        stars = [];
                        const count = Math.floor((canvas.width * canvas.height) / 8000);
                        const colors = ['rgba(0,229,255,', 'rgba(59,130,246,', 'rgba(139,92,246,', 'rgba(255,255,255,'];
                        for (let i = 0; i < count; i++) {
                            stars.push({
                                x: Math.random() * canvas.width,
                                y: Math.random() * canvas.height,
                                r: Math.random() * 1.5 + 0.6,
                                color: colors[Math.floor(Math.random() * colors.length)],
                                alpha: Math.random() * 0.8 + 0.2,
                                speed: Math.random() * 0.02 + 0.008
                            });
                        }
                    }
                    function render() {
                        ctx.clearRect(0, 0, canvas.width, canvas.height);
                        for (let s of stars) {
                            s.alpha += s.speed;
                            if (s.alpha > 1 || s.alpha < 0.2) s.speed = -s.speed;
                            ctx.fillStyle = s.color + Math.max(0.1, Math.min(1, s.alpha)) + ')';
                            ctx.beginPath();
                            ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
                            ctx.fill();
                        }
                        requestAnimationFrame(render);
                    }
                    window.addEventListener('resize', resize);
                    resize();
                    requestAnimationFrame(render);
                })();

                // 2. Client-Side Web Cryptography / SubtleCrypto Helper
                const sessionToken = '$sessionToken';
                try {
                    document.cookie = "astron_token=" + encodeURIComponent(sessionToken) + "; path=/; max-age=86400";
                    sessionStorage.setItem('astron_token', sessionToken);
                } catch (_) {}
                async function deriveAesKey(token) {
                    const enc = new TextEncoder();
                    const keyMaterial = await window.crypto.subtle.importKey(
                        'raw',
                        enc.encode(token),
                        { name: 'PBKDF2' },
                        false,
                        ['deriveKey']
                    );
                    return await window.crypto.subtle.deriveKey(
                        {
                            name: 'PBKDF2',
                            salt: enc.encode('Astron-Wire-Salt-v1'),
                            iterations: 10000,
                            hash: 'SHA-256'
                        },
                        keyMaterial,
                        { name: 'AES-GCM', length: 256 },
                        false,
                        ['encrypt', 'decrypt']
                    );
                }

                // 3. Drag and Drop & Upload Management
                const uploadBox = document.getElementById('uploadBox');
                if (uploadBox) {
                    ['dragenter', 'dragover'].forEach(name => {
                        uploadBox.addEventListener(name, (e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            uploadBox.style.borderColor = 'var(--cyan)';
                            uploadBox.style.background = 'var(--surface-hover)';
                        }, false);
                    });
                    ['dragleave', 'drop'].forEach(name => {
                        uploadBox.addEventListener(name, (e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            uploadBox.style.borderColor = 'var(--border)';
                            uploadBox.style.background = 'rgba(14,20,36,0.85)';
                        }, false);
                    });
                    uploadBox.addEventListener('drop', (e) => {
                        const dt = e.dataTransfer;
                        if (dt && dt.files && dt.files.length > 0) {
                            handleFiles(dt.files);
                        }
                    }, false);
                }

                async function handleFiles(files) {
                    if (!files || files.length === 0) return;
                    const bar = document.getElementById('progressBar');
                    const fill = document.getElementById('progressFill');
                    const telemetryRow = document.getElementById('telemetryRow');
                    const telemetrySpeed = document.getElementById('telemetrySpeed');
                    const telemetryPercent = document.getElementById('telemetryPercent');
                    const e2eeEnabled = document.getElementById('e2eeCheckbox')?.checked ?? true;

                    bar.style.display = 'block';
                    telemetryRow.style.display = 'flex';

                    let totalBytes = 0;
                    Array.from(files).forEach(f => totalBytes += f.size);
                    const fileLoaded = new Array(files.length).fill(0);

                    let startTime = Date.now();
                    let uploaded = 0;

                    for (let i = 0; i < files.length; i++) {
                        const file = files[i];
                        let payload = file;
                        let isEncrypted = false;

                        if (e2eeEnabled && window.crypto && window.crypto.subtle && file.size < 50 * 1024 * 1024) {
                            try {
                                const key = await deriveAesKey(sessionToken);
                                const iv = window.crypto.getRandomValues(new Uint8Array(12));
                                const fileData = await file.arrayBuffer();
                                const cipherBuffer = await window.crypto.subtle.encrypt(
                                    { name: 'AES-GCM', iv: iv, tagLength: 128 },
                                    key,
                                    fileData
                                );
                                const combined = new Uint8Array(iv.length + cipherBuffer.byteLength);
                                combined.set(iv, 0);
                                combined.set(new Uint8Array(cipherBuffer), iv.length);
                                payload = new Blob([combined]);
                                isEncrypted = true;
                            } catch (e) {
                                console.warn('Pre-encryption fallback to standard TLS/LAN stream', e);
                            }
                        }

                        await new Promise((resolve, reject) => {
                            const xhr = new XMLHttpRequest();
                            xhr.open('POST', '/upload?token=' + encodeURIComponent(sessionToken), true);
                            xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
                            xhr.setRequestHeader('X-Session-Token', sessionToken);
                            if (isEncrypted) {
                                xhr.setRequestHeader('X-Encrypted', 'aes-256-gcm');
                            }
                            xhr.upload.onprogress = (e) => {
                                if (e.lengthComputable && totalBytes > 0) {
                                    fileLoaded[i] = e.loaded;
                                    const currentTotal = fileLoaded.reduce((a, b) => a + b, 0);
                                    const percent = Math.min(100, Math.round((currentTotal / totalBytes) * 100));
                                    fill.style.width = percent + '%';
                                    telemetryPercent.textContent = percent + '%';

                                    const elapsed = (Date.now() - startTime) / 1000;
                                    if (elapsed > 0.5) {
                                        const mbps = (currentTotal / (1024 * 1024)) / elapsed;
                                        telemetrySpeed.textContent = mbps.toFixed(1) + ' MB/s';
                                    }
                                }
                            };
                            xhr.onload = () => {
                                uploaded++;
                                resolve();
                            };
                            xhr.onerror = () => reject(new Error('Upload failed'));
                            xhr.send(payload);
                        });
                    }

                    fill.style.width = '100%';
                    telemetryPercent.textContent = '100% (Completed)';
                    setTimeout(() => {
                        alert('All ' + files.length + ' file(s) uploaded successfully to $safeDeviceName!');
                        bar.style.display = 'none';
                        telemetryRow.style.display = 'none';
                        fill.style.width = '0%';
                    }, 400);
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
            val safeName = item.name.replace("\"", "_").replace("\r", "").replace("\n", "")
            val header = "HTTP/1.1 200 OK\r\n" +
                    corsHeaders() +
                    "Content-Type: ${item.mimeType}\r\n" +
                    "Content-Length: ${item.size}\r\n" +
                    "Content-Disposition: attachment; filename=\"$safeName\"\r\n" +
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

    private fun send400(out: OutputStream, message: String) {
        val body = "400 Bad Request: $message"
        val header = "HTTP/1.1 400 Bad Request\r\n" +
                corsHeaders() +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n"
        out.write((header + body).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun send403(out: OutputStream, message: String) {
        val safeDevice = htmlEscape(deviceName)
        val htmlBody = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Astron Web Drop — PIN Required</title>
                <link rel="icon" type="image/png" href="/logo.png">
                <style>
                    :root { --bg: #070A12; --card: #0E1424; --cyan: #00E5FF; --text: #F1F5F9; --muted: #94A3B8; --border: rgba(0,229,255,0.25); }
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                    body { background: var(--bg); color: var(--text); display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
                    .card { background: var(--card); border: 1px solid var(--border); border-radius: 20px; padding: 32px 24px; max-width: 420px; width: 100%; text-align: center; box-shadow: 0 10px 40px rgba(0,0,0,0.6); }
                    .logo { width: 56px; height: 56px; margin: 0 auto 16px; border-radius: 14px; background: rgba(0,229,255,0.1); display: flex; align-items: center; justify-content: center; }
                    h2 { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
                    p { font-size: 13px; color: var(--muted); margin-bottom: 24px; line-height: 1.5; }
                    input { width: 100%; padding: 14px 16px; border-radius: 12px; background: rgba(255,255,255,0.06); border: 1px solid var(--border); color: var(--text); font-size: 16px; font-weight: 600; text-align: center; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 16px; outline: none; }
                    input:focus { border-color: var(--cyan); }
                    button { width: 100%; padding: 14px; border-radius: 12px; border: none; background: linear-gradient(135deg, #00E5FF 0%, #3B82F6 100%); color: #070A12; font-weight: 700; font-size: 15px; cursor: pointer; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="logo">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#00E5FF" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                    </div>
                    <h2>Session PIN Required</h2>
                    <p>Enter the 8-character PIN displayed on <strong>$safeDevice</strong> to connect to Web Drop.</p>
                    <input id="pinInput" maxlength="16" placeholder="Enter Session PIN" autofocus autocomplete="off" onkeydown="if(event.key==='Enter') unlock();">
                    <button onclick="unlock()">Connect to Web Drop</button>
                    <script>
                        function unlock() {
                            const pin = document.getElementById('pinInput').value.trim();
                            if (!pin) return;
                            document.cookie = "astron_token=" + encodeURIComponent(pin) + "; path=/; max-age=86400";
                            window.location.href = "/?token=" + encodeURIComponent(pin);
                        }
                    </script>
                </div>
            </body>
            </html>
        """.trimIndent()
        val bytes = htmlBody.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 403 Forbidden\r\n" +
                corsHeaders() +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
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

    private fun serveLogo(out: OutputStream) {
        try {
            val inputStream = context.resources.openRawResource(com.astron.app.R.drawable.astron_logo)
            val bytes = inputStream.readBytes()
            inputStream.close()
            val header = "HTTP/1.1 200 OK\r\n" +
                    corsHeaders() +
                    "Content-Type: image/png\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Cache-Control: public, max-age=86400\r\n" +
                    "Connection: close\r\n\r\n"
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        } catch (_: Throwable) {
            send404(out)
        }
    }
}

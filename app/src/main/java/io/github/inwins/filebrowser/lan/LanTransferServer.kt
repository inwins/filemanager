package io.github.inwins.filebrowser.lan

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import io.github.inwins.filebrowser.data.FileOps
import io.github.inwins.filebrowser.util.MimeTypes
import io.github.inwins.filebrowser.util.Paths
import io.github.inwins.filebrowser.util.StreamRangeParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object LanTransferServer {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var discoveryThread: Thread? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private var appContext: Context? = null
    private var shareRoot: File? = null

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var token: String = ""
        private set

    @Volatile
    var deviceId: String = ""
        private set

    fun defaultShareDir(context: Context): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "LanTransfer")
    }

    fun shareRoot(): File? = shareRoot

    @Synchronized
    fun start(context: Context, root: File): Boolean {
        stop()
        val canonical = runCatching { root.canonicalFile }.getOrElse { root.absoluteFile }
        if (!canonical.exists() && !canonical.mkdirs()) return false
        if (!canonical.isDirectory) return false
        appContext = context.applicationContext
        shareRoot = canonical
        val newToken = randomToken()
        var socket: ServerSocket? = null
        for (candidate in PORT_START..PORT_END) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(candidate), 50)
                socket = s
                break
            } catch (_: IOException) {
            }
        }
        val server = socket ?: return false
        token = newToken
        deviceId = UUID.randomUUID().toString()
        port = server.localPort
        serverSocket = server
        running = true
        startDiscovery()
        acceptThread = Thread({
            while (!server.isClosed) {
                try {
                    val client = server.accept()
                    clients.add(client)
                    Thread {
                        try {
                            handle(client)
                        } catch (_: Exception) {
                        } finally {
                            clients.remove(client)
                            runCatching { client.close() }
                        }
                    }.apply {
                        isDaemon = true
                        start()
                    }
                } catch (_: IOException) {
                    break
                }
            }
        }, "lan-transfer-accept").apply {
            isDaemon = true
            start()
        }
        return true
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        port = 0
        token = ""
        shareRoot = null
        appContext = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        acceptThread = null
        discoveryThread?.interrupt()
        discoveryThread = null
        deviceId = ""
    }


    private fun startDiscovery() {
        discoveryThread = Thread({
            val socket = runCatching {
                DatagramSocket().apply { broadcast = true }
            }.getOrNull() ?: return@Thread
            try {
                while (running) {
                    val payload = JSONObject()
                        .put("app", LanDiscovery.APP_ID)
                        .put("id", deviceId)
                        .put("name", Build.MODEL ?: "Android")
                        .put("port", port)
                        .put("token", token)
                        .toString()
                        .toByteArray(StandardCharsets.UTF_8)
                    val targets = linkedSetOf<InetAddress>()
                    runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
                    runCatching {
                        for (network in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                            if (!network.isUp || network.isLoopback) continue
                            for (address in network.interfaceAddresses) {
                                val broadcast = address.broadcast ?: continue
                                if (broadcast is Inet4Address) targets.add(broadcast)
                            }
                        }
                    }
                    targets.forEach { target ->
                        runCatching {
                            socket.send(
                                DatagramPacket(payload, payload.size, target, LanDiscovery.DISCOVERY_PORT)
                            )
                        }
                    }
                    Thread.sleep(2_000)
                }
            } catch (_: InterruptedException) {
            } finally {
                runCatching { socket.close() }
            }
        }, "lan-transfer-discovery").apply {
            isDaemon = true
            start()
        }
    }

    fun urls(): List<String> {
        val currentPort = port
        val currentToken = token
        if (!running || currentPort <= 0 || currentToken.isBlank()) return emptyList()
        return localAddresses().map { "http://$it:$currentPort/$currentToken/" }
    }

    fun localAddresses(): List<String> {
        val result = mutableListOf<String>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return result
        for (network in Collections.list(interfaces)) {
            if (!network.isUp || network.isLoopback) continue
            for (address in Collections.list(network.inetAddresses)) {
                if (address !is Inet4Address) continue
                if (address.isLoopbackAddress || address.isLinkLocalAddress) continue
                if (!address.isSiteLocalAddress) continue
                val host = address.hostAddress ?: continue
                if (host !in result) result.add(host)
            }
        }
        return result
    }

    private fun randomToken(): String =
        (1..8).map { TOKEN_ALPHABET[Random.nextInt(TOKEN_ALPHABET.length)] }.joinToString("")

    private fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        socket.tcpNoDelay = true
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val requestLine = readAsciiLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val target = parts[1]
        var contentLength = 0L
        var rangeHeader: String? = null
        var expectContinue = false
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (name) {
                "content-length" -> contentLength = value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                "range" -> rangeHeader = value
                "expect" -> if (value.contains("100-continue", ignoreCase = true)) expectContinue = true
            }
        }
        if (expectContinue) {
            output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()
        }

        val root = shareRoot
        if (root == null || !running) {
            respondError(output, 503, "Service unavailable")
            return
        }
        val decodedTarget = String(
            target.toByteArray(StandardCharsets.ISO_8859_1),
            StandardCharsets.UTF_8,
        )
        val pathPart = decodedTarget.substringBefore('?')
        val query = parseQuery(decodedTarget.substringAfter('?', ""))
        val segments = pathPart.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments.first() != token) {
            respondError(output, 404, "Not found")
            return
        }
        val route = "/" + segments.drop(1).joinToString("/")
        when {
            method == "GET" || method == "HEAD" -> when (route) {
                "/" -> respondHtml(output, method == "HEAD", root)
                "/api/list" -> handleList(output, root, query, method == "HEAD")
                "/api/download" -> handleDownload(output, root, query, rangeHeader, method == "HEAD")
                "/favicon.ico" -> respondEmpty(output, 204)
                else -> respondError(output, 404, "Not found")
            }
            method == "PUT" && route == "/api/upload" ->
                handleUpload(input, output, root, query, contentLength)
            method == "POST" && route == "/api/mkdir" ->
                handleMkdir(output, root, query)
            else -> respondError(output, 405, "Method not allowed")
        }
    }

    private fun handleList(output: OutputStream, root: File, query: Map<String, String>, headOnly: Boolean) {
        val dir = LanTransferPaths.resolve(root, query["path"].orEmpty()) ?: run {
            respondError(output, 403, "Forbidden")
            return
        }
        if (!dir.isDirectory) {
            respondError(output, 404, "Not found")
            return
        }
        val files = dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() },
        ) ?: emptyList()
        val entries = JSONArray()
        files.forEach { file ->
            entries.put(
                JSONObject()
                    .put("name", file.name)
                    .put("path", LanTransferPaths.relative(root, file))
                    .put("dir", file.isDirectory)
                    .put("size", if (file.isDirectory) 0L else file.length())
                    .put("mtime", file.lastModified()),
            )
        }
        val body = JSONObject()
            .put("path", LanTransferPaths.relative(root, dir))
            .put("entries", entries)
            .toString()
        respondJson(output, 200, body, headOnly)
    }

    private fun handleDownload(
        output: OutputStream,
        root: File,
        query: Map<String, String>,
        rangeHeader: String?,
        headOnly: Boolean,
    ) {
        val file = LanTransferPaths.resolve(root, query["path"].orEmpty()) ?: run {
            respondError(output, 403, "Forbidden")
            return
        }
        if (!file.isFile) {
            respondError(output, 404, "Not found")
            return
        }
        val total = file.length()
        val range = if (total > 0) {
            StreamRangeParser.parse(rangeHeader, total) ?: run {
                val header = buildString {
                    append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                    append("Content-Range: bytes */").append(total).append("\r\n")
                    append("Content-Length: 0\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
                output.flush()
                return
            }
        } else {
            null
        }
        val start = range?.start ?: 0L
        val length = range?.length ?: total
        val status = if (rangeHeader != null && range != null) "206 Partial Content" else "200 OK"
        val mime = MimeTypes.fromName(file.name) ?: "application/octet-stream"
        val headers = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(mime).append("\r\n")
            append("Content-Length: ").append(length).append("\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (rangeHeader != null && range != null) {
                append("Content-Range: bytes ").append(range.start).append('-')
                    .append(range.end).append('/').append(total).append("\r\n")
            }
            append("Content-Disposition: ").append(contentDisposition(file.name)).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
        output.flush()
        if (headOnly) return
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
            }
        } catch (_: IOException) {
        }
    }

    private fun handleUpload(
        input: InputStream,
        output: OutputStream,
        root: File,
        query: Map<String, String>,
        contentLength: Long,
    ) {
        val name = query["name"].orEmpty()
        if (!Paths.safeName(name)) {
            respondError(output, 400, "Invalid file name")
            return
        }
        if (contentLength <= 0) {
            respondError(output, 411, "Content-Length required")
            return
        }
        val dir = LanTransferPaths.resolve(root, query["path"].orEmpty()) ?: run {
            respondError(output, 403, "Forbidden")
            return
        }
        if (!dir.isDirectory) {
            respondError(output, 404, "Folder not found")
            return
        }
        val destination = FileOps.uniqueFile(File(dir, name))
        try {
            destination.outputStream().use { out ->
                copyExactly(input, out, contentLength)
            }
        } catch (error: Exception) {
            runCatching { destination.delete() }
            respondError(output, 500, error.message ?: "Upload failed")
            return
        }
        appContext?.let { context ->
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(destination.path), null, null)
            }
        }
        val body = JSONObject()
            .put("name", destination.name)
            .put("size", destination.length())
            .put("path", LanTransferPaths.relative(root, destination))
            .toString()
        respondJson(output, 200, body, false)
    }

    private fun handleMkdir(output: OutputStream, root: File, query: Map<String, String>) {
        val name = query["name"].orEmpty()
        if (!Paths.safeName(name)) {
            respondError(output, 400, "Invalid folder name")
            return
        }
        val dir = LanTransferPaths.resolve(root, query["path"].orEmpty()) ?: run {
            respondError(output, 403, "Forbidden")
            return
        }
        if (!dir.isDirectory) {
            respondError(output, 404, "Folder not found")
            return
        }
        val target = File(dir, name)
        if (target.exists()) {
            respondError(output, 409, "Already exists")
            return
        }
        if (!target.mkdir()) {
            respondError(output, 500, "Cannot create folder")
            return
        }
        val body = JSONObject()
            .put("name", target.name)
            .put("path", LanTransferPaths.relative(root, target))
            .toString()
        respondJson(output, 200, body, false)
    }

    private fun copyExactly(input: InputStream, output: OutputStream, length: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) throw IOException("Unexpected end of upload")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun readAsciiLine(input: InputStream): String? {
        val bytes = ArrayList<Byte>(128)
        while (true) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.add(value.toByte())
            if (bytes.size > 8192) return null
        }
        return String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        raw.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val index = pair.indexOf('=')
            val key = if (index < 0) pair else pair.substring(0, index)
            val value = if (index < 0) "" else pair.substring(index + 1)
            result[decode(key)] = decode(value)
        }
        return result
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    private fun contentDisposition(name: String): String {
        val fallback = name.map { ch ->
            if (ch.code in 32..126 && ch != '"' && ch != '\\') ch else '_'
        }.joinToString("")
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "attachment; filename=\"$fallback\"; filename*=UTF-8''$encoded"
    }

    private fun respondHtml(output: OutputStream, headOnly: Boolean, root: File) {
        val html = HTML_TEMPLATE
            .replace("__TOKEN__", token)
            .replace("__DEVICE__", escapeHtml(Build.MODEL ?: "Android"))
            .replace("__FOLDER__", escapeHtml(root.name))
        respond(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8), headOnly)
    }

    private fun respondJson(output: OutputStream, code: Int, body: String, headOnly: Boolean) {
        respond(output, code, statusText(code), "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8), headOnly)
    }

    private fun respondEmpty(output: OutputStream, code: Int) {
        respond(output, code, statusText(code), "text/plain; charset=utf-8", ByteArray(0), false)
    }

    private fun respond(
        output: OutputStream,
        code: Int,
        status: String,
        contentType: String,
        body: ByteArray,
        headOnly: Boolean,
    ) {
        val headers = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
        if (!headOnly && body.isNotEmpty()) output.write(body)
        output.flush()
    }

    private fun respondError(output: OutputStream, code: Int, message: String) {
        val body = message.toByteArray(StandardCharsets.UTF_8)
        respond(output, code, statusText(code), "text/plain; charset=utf-8", body, false)
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        204 -> "No Content"
        206 -> "Partial Content"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        411 -> "Length Required"
        416 -> "Range Not Satisfiable"
        500 -> "Internal Server Error"
        503 -> "Service Unavailable"
        else -> "Error"
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private const val PORT_START = 18800
    private const val PORT_END = 18830
    private const val TOKEN_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

    private const val HTML_TEMPLATE = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>文件浏览器 · 局域网互传</title>
<style>
:root{color-scheme:light dark}
*{box-sizing:border-box}
body{margin:0;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;background:#f5f6fa;color:#202431}
header{padding:22px 20px 16px;background:linear-gradient(135deg,#2563eb,#0ea5e9);color:#fff}
header h1{margin:0;font-size:22px}
header p{margin:6px 0 0;opacity:.9;font-size:13px}
main{max-width:960px;margin:0 auto;padding:16px}
.toolbar{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px}
button,.upload{border:0;border-radius:10px;padding:9px 14px;font-size:14px;background:#e8edf7;color:#1e3a8a;cursor:pointer}
button:hover,.upload:hover{background:#dbe6fb}
.upload{background:#2563eb;color:#fff;position:relative;overflow:hidden}
.upload input{position:absolute;inset:0;opacity:0;cursor:pointer}
.path{font-size:13px;color:#64748b;word-break:break-all;margin-bottom:10px}
.drop{border:2px dashed #b9c6dd;border-radius:14px;padding:16px;text-align:center;color:#64748b;margin-bottom:12px;background:#fff}
.drop.over{border-color:#2563eb;background:#eef4ff;color:#1d4ed8}
.progress{height:8px;border-radius:99px;background:#e2e8f0;overflow:hidden;margin-bottom:10px}
.progress div{height:100%;width:0;background:#2563eb;transition:width .15s}
.status{min-height:20px;font-size:13px;color:#475569;margin-bottom:8px}
table{width:100%;border-collapse:collapse;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 1px 3px rgba(15,23,42,.08)}
th,td{text-align:left;padding:12px 14px;border-bottom:1px solid #eef2f7;font-size:14px}
th{font-size:12px;color:#64748b;font-weight:600}
tr:last-child td{border-bottom:0}
td.name{word-break:break-all}
td.name a{color:#1d4ed8;text-decoration:none}
td.name a:hover{text-decoration:underline}
.icon{display:inline-block;width:22px;height:22px;line-height:22px;margin-right:8px;text-align:center;border-radius:6px;background:#e8edf7;color:#1e3a8a;font-size:11px;font-weight:700;vertical-align:middle}
.muted{color:#64748b;font-size:12px}
.empty{padding:28px;text-align:center;color:#64748b;background:#fff;border-radius:14px}
footer{max-width:960px;margin:0 auto;padding:0 16px 28px;color:#64748b;font-size:12px;line-height:1.6}
.hidden{display:none}
@media (max-width:560px){th:nth-child(3),td:nth-child(3){display:none}main{padding:12px}header{padding:18px 16px 14px}}
</style>
</head>
<body>
<header>
<h1>文件浏览器 · 局域网互传</h1>
<p>__DEVICE__ · 共享目录：__FOLDER__</p>
</header>
<main>
<div class="toolbar">
<button id="up">上一级</button>
<button id="refresh">刷新</button>
<label class="upload">上传文件<input id="file" type="file" multiple></label>
<button id="mkdir">新建文件夹</button>
</div>
<div id="path" class="path"></div>
<div id="drop" class="drop">把文件拖到这里，或点击“上传文件”</div>
<div id="progress" class="progress hidden"><div id="bar"></div></div>
<div id="status" class="status"></div>
<div id="list"></div>
</main>
<footer>仅同一 Wi-Fi 下的设备可以访问。传输完成后请在手机上停止共享。</footer>
<script>
const token = "__TOKEN__";
const api = "/" + token + "/api";
let cwd = "";
const el = function (id) { return document.getElementById(id); };
function enc(value) { return encodeURIComponent(value || ""); }
function formatSize(bytes) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) { value = value / 1024; index++; }
  return (index === 0 ? Math.round(value) : value.toFixed(value >= 10 ? 0 : 1)) + " " + units[index];
}
function formatDate(ms) {
  if (!ms) return "";
  try { return new Date(ms).toLocaleString(); } catch (e) { return ""; }
}
function setStatus(text) { el("status").textContent = text || ""; }
function setProgress(value) {
  const box = el("progress");
  if (value === null) { box.classList.add("hidden"); el("bar").style.width = "0"; return; }
  box.classList.remove("hidden");
  el("bar").style.width = Math.max(0, Math.min(100, value)) + "%";
}
async function load(path) {
  setStatus("正在加载…");
  try {
    const response = await fetch(api + "/list?path=" + enc(path), { cache: "no-store" });
    if (!response.ok) throw new Error(await response.text());
    const data = await response.json();
    cwd = data.path || "";
    el("path").textContent = "/" + (cwd || "");
    render(data.entries || []);
    setStatus("");
  } catch (error) {
    setStatus("加载失败：" + (error.message || error));
  }
}
function render(entries) {
  const box = el("list");
  box.textContent = "";
  if (!entries.length) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = "这个文件夹还是空的";
    box.appendChild(empty);
    return;
  }
  const table = document.createElement("table");
  const head = document.createElement("tr");
  ["名称", "大小", "修改时间"].forEach(function (label) {
    const th = document.createElement("th");
    th.textContent = label;
    head.appendChild(th);
  });
  table.appendChild(head);
  entries.forEach(function (entry) {
    const row = document.createElement("tr");
    const nameCell = document.createElement("td");
    nameCell.className = "name";
    const icon = document.createElement("span");
    icon.className = "icon";
    icon.textContent = entry.dir ? "D" : "F";
    nameCell.appendChild(icon);
    if (entry.dir) {
      const button = document.createElement("a");
      button.href = "#";
      button.textContent = entry.name;
      button.onclick = function (event) { event.preventDefault(); load(entry.path); };
      nameCell.appendChild(button);
    } else {
      const link = document.createElement("a");
      link.href = api + "/download?path=" + enc(entry.path);
      link.textContent = entry.name;
      link.download = entry.name;
      nameCell.appendChild(link);
    }
    const sizeCell = document.createElement("td");
    sizeCell.className = "muted";
    sizeCell.textContent = entry.dir ? "文件夹" : formatSize(entry.size);
    const dateCell = document.createElement("td");
    dateCell.className = "muted";
    dateCell.textContent = formatDate(entry.mtime);
    row.appendChild(nameCell);
    row.appendChild(sizeCell);
    row.appendChild(dateCell);
    table.appendChild(row);
  });
  box.appendChild(table);
}
function uploadFile(file) {
  return new Promise(function (resolve, reject) {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", api + "/upload?path=" + enc(cwd) + "&name=" + enc(file.name));
    xhr.upload.onprogress = function (event) {
      if (event.lengthComputable) setProgress(event.loaded / event.total * 100);
    };
    xhr.onload = function () {
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(new Error(xhr.responseText || ("HTTP " + xhr.status)));
    };
    xhr.onerror = function () { reject(new Error("网络错误")); };
    xhr.send(file);
  });
}
async function uploadFiles(files) {
  if (!files || !files.length) return;
  try {
    for (let i = 0; i < files.length; i++) {
      setStatus("正在上传 " + (i + 1) + "/" + files.length + "：" + files[i].name);
      await uploadFile(files[i]);
    }
    setStatus("上传完成");
    await load(cwd);
  } catch (error) {
    setStatus("上传失败：" + (error.message || error));
  } finally {
    setProgress(null);
    el("file").value = "";
  }
}
el("file").addEventListener("change", function (event) { uploadFiles(event.target.files); });
el("refresh").onclick = function () { load(cwd); };
el("up").onclick = function () {
  const parts = cwd.split("/").filter(Boolean);
  parts.pop();
  load(parts.join("/"));
};
el("mkdir").onclick = async function () {
  const name = prompt("文件夹名称");
  if (!name) return;
  try {
    const response = await fetch(api + "/mkdir?path=" + enc(cwd) + "&name=" + enc(name), { method: "POST" });
    if (!response.ok) throw new Error(await response.text());
    await load(cwd);
  } catch (error) {
    setStatus("创建失败：" + (error.message || error));
  }
};
const drop = el("drop");
["dragenter", "dragover"].forEach(function (name) {
  drop.addEventListener(name, function (event) { event.preventDefault(); drop.classList.add("over"); });
});
["dragleave", "drop"].forEach(function (name) {
  drop.addEventListener(name, function (event) { event.preventDefault(); drop.classList.remove("over"); });
});
drop.addEventListener("drop", function (event) {
  if (event.dataTransfer && event.dataTransfer.files) uploadFiles(event.dataTransfer.files);
});
load("");
</script>
</body>
</html>"""
}

package io.github.inwins.filebrowser.lan

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

object LanDiscovery {
    const val DISCOVERY_PORT = 18855
    const val APP_ID = "io.github.inwins.filebrowser"

    fun scan(timeoutMs: Long = 2600L): List<LanPeer> {
        val peers = linkedMapOf<String, LanPeer>()
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
                soTimeout = 350
            }
        }.getOrNull() ?: return emptyList()
        val buffer = ByteArray(4096)
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                val peer = parse(text, packet.address.hostAddress ?: continue) ?: continue
                if (peer.id == LanTransferServer.deviceId) continue
                peers[peer.id] = peer
            }
        } finally {
            runCatching { socket.close() }
        }
        return peers.values.toList()
    }

    fun parse(text: String, host: String): LanPeer? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (json.optString("app") != APP_ID) return null
        val id = json.optString("id")
        val name = json.optString("name").ifBlank { host }
        val port = json.optInt("port", 0)
        val token = json.optString("token")
        if (id.isBlank() || port !in 1..65535 || token.isBlank()) return null
        return LanPeer(id = id, name = name, host = host, port = port, token = token)
    }
}

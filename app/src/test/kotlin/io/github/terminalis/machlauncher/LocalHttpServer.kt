package io.github.terminalis.machlauncher

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.Collections
import kotlin.concurrent.thread

class LocalHttpServer(private val status: Int, private val body: ByteArray) : Closeable {
    private val socket = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
    val url = "http://127.0.0.1:${socket.localPort}/"
    val requestLines: MutableList<String> = Collections.synchronizedList(ArrayList())

    init {
        thread(isDaemon = true) {
            try {
                while (true) {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine()
                            if (line.isNullOrEmpty()) break
                            requestLines.add(line)
                        }
                        val output = client.getOutputStream()
                        output.write("HTTP/1.1 $status Status\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(body)
                        output.flush()
                    }
                }
            } catch (_: SocketException) {
            }
        }
    }

    override fun close() = socket.close()
}

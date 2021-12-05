package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class Request(private val uri: Uri) {
    private val port get() = if (uri.port > 0) uri.port else 1965

    fun connect(): SSLSocket {
        Log.d(TAG, "connect")
        val context = SSLContext.getInstance("TLSv1.2")
        context.init(null, arrayOf(TrustManager()), null)
        val socket = context.socketFactory.createSocket(uri.host, port) as SSLSocket
        socket.soTimeout = 10000
        socket.startHandshake()
        return socket
    }

    fun proceed(socket: SSLSocket, scope: CoroutineScope): Channel<ByteArray> {
        Log.d(TAG, "proceed")
        socket.outputStream.write("$uri\r\n".toByteArray())

        val channel = Channel<ByteArray>()
        scope.launch {
            val buffer = ByteArray(1024)
            var numRead: Int
            socket.inputStream.use { socket_input_stream ->
                BufferedInputStream(socket_input_stream).use { bis ->
                    try {
                        @Suppress("BlockingMethodInNonBlockingContext")  // what u gonna do
                        while ((bis.read(buffer).also { numRead = it }) >= 0) {
                            Log.d(TAG, "proceed coroutine: received $numRead bytes")
                            val received = buffer.sliceArray(0 until numRead)
                            channel.send(received)
                        }
                    } catch (e: SocketTimeoutException) {
                        Log.i(TAG, "Socket timeout.")
                        channel.cancel()
                    }
                }
            }
            Log.d(TAG, "proceed coroutine: reading completed")
        }
        return channel
    }

    @SuppressLint("CustomX509TrustManager")
    class TrustManager: X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            Log.d(TAG, "cool cert, please continue")
        }

        override fun getAcceptedIssuers(): Array<out X509Certificate> = arrayOf()
    }

    companion object {
        const val TAG = "Request"
    }
}
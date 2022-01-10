package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class Request(private val uri: Uri) {
    private val port get() = if (uri.port > 0) uri.port else 1965

    fun connect(connectionTimeout: Int, readTimeout: Int): SSLSocket {
        Log.d(TAG, "connect")
        val context = SSLContext.getInstance("TLSv1.2")
        context.init(null, arrayOf(TrustManager()), null)
        val socket = context.socketFactory.createSocket() as SSLSocket
        socket.soTimeout = readTimeout * 1000
        socket.connect(InetSocketAddress(uri.host, port), connectionTimeout * 1000)
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
                            val received = buffer.sliceArray(0 until numRead)
                            channel.send(received)
                        }
                    } catch (e: SocketTimeoutException) {
                        Log.i(TAG, "proceed coroutine: socket timeout.")
                    }
                }
            }
            Log.d(TAG, "proceed coroutine: reading completed")
            channel.close()
        }
        return channel
    }

    @SuppressLint("CustomX509TrustManager")
    class TrustManager : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            Log.d(TAG, "cool cert, please continue")
        }

        override fun getAcceptedIssuers(): Array<out X509Certificate> = arrayOf()
    }

    companion object {
        private const val TAG = "Request"
        const val DEFAULT_CONNECTION_TIMEOUT_SEC = 10
        const val DEFAULT_READ_TIMEOUT_SEC = 10
    }
}
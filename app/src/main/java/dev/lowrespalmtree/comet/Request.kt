package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.*

class Request(private val uri: Uri, private val keyManager: KeyManager? = null) {
    private val port get() = if (uri.port > 0) uri.port else 1965

    fun connect(protocol: String, connectionTimeout: Int, readTimeout: Int): SSLSocket {
        Log.d(
            TAG,
            "connect: $protocol, conn. timeout $connectionTimeout," +
            " read timeout $readTimeout, key manager $keyManager"
        )
        val context = SSLContext.getInstance(protocol)
        context.init(arrayOf(keyManager), arrayOf(TrustManager()), null)
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
                    } catch (e: SSLProtocolException) {
                        Log.e(TAG, "proceed coroutine: SSL protocol exception: ${e.message}")
                    }
                }
            }
            Log.d(TAG, "proceed coroutine: reading completed")
            channel.close()
        }
        return channel
    }

    class KeyManager(
        private val alias: String,
        private val cert: X509Certificate,
        private val privateKey: PrivateKey
    ) : X509ExtendedKeyManager() {
        companion object {
            fun fromAlias(alias: String): KeyManager? {
                val cert = Identities.keyStore.getCertificate(alias) as X509Certificate?
                    ?: return null.also { Log.e(TAG, "fromAlias: cert is null") }
                val key = Identities.keyStore.getEntry(alias, null)?.let { entry ->
                    (entry as KeyStore.PrivateKeyEntry).privateKey
                } ?: return null.also { Log.e(TAG, "fromAlias: private key is null") }
                return KeyManager(alias, cert, key)
            }
        }

        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: Socket?
        ): String = alias

        override fun getCertificateChain(alias: String?): Array<out X509Certificate> = arrayOf(cert)

        override fun getPrivateKey(alias: String?): PrivateKey = privateKey

        override fun getServerAliases(
            keyType: String?, issuers: Array<out Principal>?
        ): Array<String> = throw UnsupportedOperationException()

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out Principal>?,
            socket: Socket?
        ): String = throw UnsupportedOperationException()

        override fun getClientAliases(
            keyType: String?,
            issuers: Array<out Principal>?
        ): Array<String> = throw UnsupportedOperationException()
    }

    /** TODO An X509TrustManager implementation for TOFU validation. */
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
        const val DEFAULT_TLS_VERSION = "TLSv1.3"
        const val DEFAULT_CONNECTION_TIMEOUT_SEC = 10
        const val DEFAULT_READ_TIMEOUT_SEC = 10
    }
}
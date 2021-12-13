package dev.lowrespalmtree.comet

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.charset.Charset

class Response(val code: Code, val meta: String, val data: Channel<ByteArray>) {
    @Suppress("unused")
    enum class Code(val value: Int) {
        UNKNOWN(0),
        INPUT(10),
        SENSITIVE_INPUT(11),
        SUCCESS(20),
        REDIRECT_TEMPORARY(30),
        REDIRECT_PERMANENT(31),
        TEMPORARY_FAILURE(40),
        SERVER_UNAVAILABLE(41),
        CGI_ERROR(42),
        PROXY_ERROR(43),
        SLOW_DOWN(44),
        PERMANENT_FAILURE(50),
        NOT_FOUND(51),
        GONE(52),
        PROXY_REQUEST_REFUSED(53),
        BAD_REQUEST(59),
        CLIENT_CERTIFICATE_REQUIRED(60),
        CERTIFICATE_NOT_AUTHORISED(61),
        CERTIFICATE_NOT_VALID(62);

        enum class Category(val value: Int) {
            UNKNOWN(0),
            INPUT(1),
            SUCCESS(2),
            REDIRECT(3),
            SERVER_ERROR(4),
            CLIENT_ERROR(5),
            CERTIFICATE(6);
        }

        fun getCategory(): Category? = Category.values().associateBy(Category::value)[value / 10]

        companion object {
            private val MAP = values().associateBy(Code::value)
            fun fromInt(type: Int) = MAP[type]
        }
    }

    companion object {
        private const val TAG = "Response"

        /** Return a response object from the incoming server data, served through the channel. */
        suspend fun from(channel: Channel<ByteArray>, scope: CoroutineScope): Response? {
            var received = 0
            val headerBuffer = ByteBuffer.allocate(1024)
            var lfIndex: Int
            // While we don't have a response object (i.e. no header parsed), keep reading.
            while (true) {
                val data = try {
                    channel.receive()
                } catch (e: ClosedReceiveChannelException) {
                    Log.d(TAG, "companion from: channel closed during initial receive")
                    return null
                }
                // Push some data into our buffer.
                headerBuffer.put(data)
                received += data.size
                // Check if there is enough data to parse a Gemini header from it (e.g. has \r\n).
                lfIndex = headerBuffer.array().indexOf(0x0D)  // \r
                if (lfIndex == -1)
                    continue
                if (headerBuffer.array()[lfIndex + 1] != (0x0A.toByte()))  // \n
                    continue
                break
            }
            // We have our header! Parse it to create our Response object.
            val bytes = headerBuffer.array()
            val headerData = bytes.sliceArray(0 until lfIndex)
            val (code, meta) = parseHeader(headerData)
                ?: return null.also { Log.e(TAG, "companion from: can't parse header") }
            val response = Response(code, meta, Channel())
            scope.launch {
                // If we got too much data from the channel: push the trailing data first.
                val trailingIndex = lfIndex + 2
                if (trailingIndex < received) {
                    val trailingData = bytes.sliceArray(trailingIndex until received)
                    response.data.send(trailingData)
                }
                // Forward all incoming data to the Response channel.
                for (data in channel)
                    response.data.send(data)
                response.data.close()
            }
            return response
        }

        /** Return the code and meta from this header if it could be parsed correctly. */
        private fun parseHeader(data: ByteArray): Pair<Code, String>? {
            val string = data.toString(Charset.defaultCharset())
            val parts = string.split(" ", limit = 2)
            if (parts.size != 2)
                return null
            val code = parts[0].toIntOrNull()?.let { Code.fromInt(it) } ?: return null
            return Pair(code, parts[1])
        }
    }
}
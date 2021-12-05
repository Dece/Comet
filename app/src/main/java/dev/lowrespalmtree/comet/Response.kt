package dev.lowrespalmtree.comet

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
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
            for (data in channel) {
                // Push some data into our buffer.
                headerBuffer.put(data)
                received += data.size
                // Check if there is enough data to parse a Gemini header from it (e.g. has \r\n).
                val lfIndex = headerBuffer.array().indexOf(0x0D)  // \r
                if (lfIndex == -1)
                    continue
                if (headerBuffer.array()[lfIndex + 1] != (0x0A.toByte()))  // \n
                    continue
                // We have our header! Parse it to create our Response object.
                val bytes = headerBuffer.array()
                val headerData = bytes.sliceArray(0 until lfIndex)
                val (code, meta) = parseHeader(headerData)
                    ?: return null.also { Log.e(TAG, "Failed to parse header") }
                val responseChannel = Channel<ByteArray>()
                val response = Response(code, meta, responseChannel)
                scope.launch {
                    // If we got too much data from the channel: push the trailing data first.
                    val trailingIndex = lfIndex + 2
                    if (trailingIndex < received) {
                        val trailingData = bytes.sliceArray(trailingIndex until received)
                        responseChannel.send(trailingData)
                    }
                    // Forward all incoming data to the Response channel.
                    channel.consumeEach { responseChannel.send(it) }
                }
                // Return the response here; this stops consuming the channel from this for-loop so
                // that the coroutine above can take care of it.
                return response
            }
            return null
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
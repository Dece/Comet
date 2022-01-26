package dev.lowrespalmtree.comet

import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onSuccess
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset

@ExperimentalCoroutinesApi
class PageViewModel(@Suppress("unused") private val savedStateHandle: SavedStateHandle) :
    ViewModel() {
    /** Currently viewed page URL. */
    var currentUrl: String = ""
    /** Latest Uri requested using `sendGeminiRequest`. */
    var loadingUrl: Uri? = null
    /** Observable page viewer state. */
    val state: MutableLiveData<State> by lazy { MutableLiveData<State>(State.IDLE) }
    /** Observable page viewer lines (backed up by `linesList` but updated less often). */
    val lines: MutableLiveData<List<Line>> by lazy { MutableLiveData<List<Line>>() }
    /** Observable page viewer latest event. */
    val event: MutableLiveData<Event> by lazy { MutableLiveData<Event>() }
    /** A non-saved list of visited URLs. Not an history, just used for going back. */
    val visitedUrls = mutableListOf<String>()
    /** Latest request job created, stored to cancel it if needed. */
    private var requestJob: Job? = null
    /** Lines for the current page. */
    private var linesList = ArrayList<Line>()

    enum class State {
        IDLE, CONNECTING, RECEIVING
    }

    abstract class Event(var handled: Boolean = false)
    data class InputEvent(val uri: Uri, val prompt: String) : Event()
    data class SuccessEvent(val uri: String) : Event()
    data class RedirectEvent(val uri: String, val redirects: Int) : Event()
    data class FailureEvent(
        val short: String,
        val details: String,
        val serverDetails: String? = null
    ) : Event()

    /**
     * Perform a request against this URI.
     *
     * The URI must be valid, absolute and with a gemini scheme.
     */
    fun sendGeminiRequest(uri: Uri, connectionTimeout: Int, readTimeout: Int, redirects: Int = 0) {
        Log.d(TAG, "sendRequest: URI \"$uri\"")
        loadingUrl = uri
        state.postValue(State.CONNECTING)
        requestJob?.apply { if (isActive) cancel() }
        requestJob = viewModelScope.launch(Dispatchers.IO) {
            val response = try {
                val request = Request(uri)
                val socket = request.connect(connectionTimeout, readTimeout)
                val channel = request.proceed(socket, this)
                Response.from(channel, viewModelScope)
            } catch (e: Exception) {
                Log.e(TAG, "sendGeminiRequest coroutine: ${e.stackTraceToString()}")
                // If we got cancelled, die silently.
                if (!isActive)
                    return@launch
                signalError(
                    when (e) {
                        is UnknownHostException -> "Unknown host \"${uri.authority}\"."
                        is ConnectException -> "Can't connect to this server: ${e.localizedMessage}."
                        is SocketTimeoutException -> "Connection timed out."
                        is CancellationException -> "Connection cancelled: ${e.message}."
                        else -> "Oops, something failed!"
                    }
                )
                return@launch
            }
            if (!isActive)
                return@launch
            if (response == null) {
                signalError("Can't parse server response.")
                return@launch
            }

            Log.i(TAG, "sendRequest: got ${response.code} with meta \"${response.meta}\"")
            when (response.code.getCategory()) {
                Response.Code.Category.INPUT ->
                    handleInputResponse(response, uri)
                Response.Code.Category.SUCCESS ->
                    handleSuccessResponse(response, uri)
                Response.Code.Category.REDIRECT ->
                    handleRedirectResponse(response, redirects = redirects + 1)
                Response.Code.Category.SERVER_ERROR, Response.Code.Category.CLIENT_ERROR ->
                    handleErrorResponse(response)
                else ->
                    signalError("Can't handle code ${response.code}.")
            }
        }
    }

    private fun signalError(message: String) {
        event.postValue(FailureEvent("Error", message))
    }

    private fun handleInputResponse(response: Response, uri: Uri) {
        event.postValue(InputEvent(uri, response.meta))
    }

    private suspend fun handleSuccessResponse(response: Response, uri: Uri) {
        state.postValue(State.RECEIVING)

        linesList.clear()
        lines.postValue(linesList)
        val charset = Charset.defaultCharset()
        var mainTitle: String? = null
        var lastUpdate = System.currentTimeMillis()
        var lastNumLines = 0
        Log.d(TAG, "handleRequestSuccess: start parsing line data")
        try {
            val lineChannel = parseData(response.data, charset, viewModelScope)
            while (!lineChannel.isClosedForReceive) {
                val lineChannelResult = withTimeout(100) { lineChannel.tryReceive() }
                lineChannelResult.onSuccess { line ->
                    linesList.add(line)
                    if (mainTitle == null && line is TitleLine && line.level == 1)
                        mainTitle = line.text
                }

                // Throttle the recycler view updates to 100ms and new content only.
                if (linesList.size > lastNumLines) {
                    val time = System.currentTimeMillis()
                    if (time - lastUpdate >= 100) {
                        lines.postValue(linesList)
                        lastUpdate = time
                        lastNumLines = linesList.size
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "handleRequestSuccess: coroutine cancelled: ${e.message}")
            state.postValue(State.IDLE)
            return
        }
        Log.d(TAG, "handleRequestSuccess: done parsing line data")
        lines.postValue(linesList)

        // We record the history entry here: it's nice because we have the main title available
        // and we're already in a coroutine for database access.
        History.record(uri.toString(), mainTitle)
        event.postValue(SuccessEvent(uri.toString()))
        state.postValue(State.IDLE)
    }

    private fun handleRedirectResponse(response: Response, redirects: Int) {
        event.postValue(RedirectEvent(response.meta, redirects))
    }

    private fun handleErrorResponse(response: Response) {
        val briefMessage = when (response.code) {
            Response.Code.TEMPORARY_FAILURE -> "40 Temporary failure"
            Response.Code.SERVER_UNAVAILABLE -> "41 Server unavailable"
            Response.Code.CGI_ERROR -> "42 CGI error"
            Response.Code.PROXY_ERROR -> "43 Proxy error"
            Response.Code.SLOW_DOWN -> "44 Slow down"
            Response.Code.PERMANENT_FAILURE -> "50 Permanent failure"
            Response.Code.NOT_FOUND -> "51 Not found"
            Response.Code.GONE -> "52 Gone"
            Response.Code.PROXY_REQUEST_REFUSED -> "53 Proxy request refused"
            Response.Code.BAD_REQUEST -> "59 Bad request"
            else -> "${response.code} (unknown)"
        }
        val longMessage: String
        var serverMessage: String? = null
        if (response.code == Response.Code.SLOW_DOWN) {
            longMessage =
                "You should wait ${response.meta.toIntOrNull() ?: "a few"} seconds before retrying."
        } else {
            longMessage = when (response.code) {
                Response.Code.TEMPORARY_FAILURE -> "The server encountered a temporary failure."
                Response.Code.SERVER_UNAVAILABLE -> "The server is currently unavailable."
                Response.Code.CGI_ERROR -> "A CGI script encountered an error."
                Response.Code.PROXY_ERROR -> "The server failed to proxy the request."
                Response.Code.PERMANENT_FAILURE -> "This request failed and similar requests will likely fail as well."
                Response.Code.NOT_FOUND -> "This page can't be found."
                Response.Code.GONE -> "This page is gone."
                Response.Code.PROXY_REQUEST_REFUSED -> "The server refused to proxy the request."
                Response.Code.BAD_REQUEST -> "Bad request."
                else -> "Unknown error code."
            }
            if (response.meta.isNotEmpty())
                serverMessage = response.meta
        }
        event.postValue(FailureEvent(briefMessage, longMessage, serverMessage))
    }

    companion object {
        private const val TAG = "PageViewModel"
    }
}
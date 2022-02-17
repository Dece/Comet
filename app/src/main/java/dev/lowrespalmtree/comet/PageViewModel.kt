package dev.lowrespalmtree.comet

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import dev.lowrespalmtree.comet.utils.downloadMedia
import dev.lowrespalmtree.comet.utils.resolveLinkUri
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onSuccess
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset

class PageViewModel(
    @Suppress("unused") private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    /** Currently viewed page URL. */
    var currentUrl: String = ""

    /** Latest Uri requested using `sendGeminiRequest`. */
    var loadingUrl: Uri? = null

    /** Observable page viewer state. */
    val state: MutableLiveData<State> by lazy { MutableLiveData<State>(State.IDLE) }

    /** Observable page viewer lines (backed up by `linesList` but updated less often). Left element is associated URL. */
    val lines: MutableLiveData<Pair<String, List<Line>>> by lazy { MutableLiveData<Pair<String, List<Line>>>() }

    /** Observable page viewer latest event. */
    val event: MutableLiveData<Event> by lazy { MutableLiveData<Event>() }

    /** A non-saved list of visited URLs. Not an history, just used for going back. */
    val visitedUrls = mutableListOf<String>()

    /** Latest request job created, stored to cancel it if needed. */
    private var requestJob: Job? = null

    /** Lines for the current page. */
    private var linesList = ArrayList<Line>()

    /** Page state to be reflected on the UI (e.g. loading bar). */
    enum class State {
        IDLE, CONNECTING, RECEIVING
    }

    /** Generic event class to notify observers with. The handled flag avoids repeated usage. */
    abstract class Event(var handled: Boolean = false)

    /** An user input has been requested from the URI, with this prompt. */
    data class InputEvent(val uri: Uri, val prompt: String) : Event()

    /** The server responded with a success code and *has finished* its response. */
    data class SuccessEvent(val uri: String) : Event()

    /** The server responded with a success code and a binary MIME type (not delivered yet). */
    data class BinaryEvent(
        val uri: Uri,
        val response: Response,
        val mimeType: MimeType
    ) : Event()

    /** A file has been completely downloaded. */
    data class DownloadCompletedEvent(
        val uri: Uri,
        val mimeType: MimeType
    ) : Event()

    /** The server is redirecting us. */
    data class RedirectEvent(val uri: String, val sourceUri: String, val redirects: Int) : Event()

    /** The server responded with a failure code or we encountered a local issue. */
    data class FailureEvent(
        val short: String,
        val details: String,
        val serverDetails: String? = null
    ) : Event()

    /**
     * Perform a request against this URI.
     *
     * @param uri URI to open; must be valid, absolute and with a gemini scheme
     * @param context Context used to retrieve user preferences, not stored
     * @param redirects current number of redirections operated
     */
    @ExperimentalCoroutinesApi
    fun sendGeminiRequest(
        uri: Uri,
        context: Context,
        redirects: Int = 0
    ) {
        Log.i(TAG, "sendGeminiRequest: URI \"$uri\"")
        loadingUrl = uri

        // Retrieve various request parameters from user preferences.
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val protocol =
            prefs.getString("tls_version", Request.DEFAULT_TLS_VERSION)!!
        val connectionTimeout =
            prefs.getInt("connection_timeout", Request.DEFAULT_CONNECTION_TIMEOUT_SEC)
        val readTimeout =
            prefs.getInt("read_timeout", Request.DEFAULT_READ_TIMEOUT_SEC)

        state.postValue(State.CONNECTING)

        requestJob?.apply { if (isActive) cancel() }
        requestJob = viewModelScope.launch(Dispatchers.IO) {
            // Look for a suitable identity to use with this URL.
            val keyManager = Identities.getForUrl(uri.toString())?.let {
                Log.d(TAG, "sendGeminiRequest coroutine: using identity with key ${it.key}")
                Request.KeyManager.fromAlias(it.key)
            }

            // Connect to the server and proceed (no TOFU validation yet).
            val response = try {
                val request = Request(uri, keyManager = keyManager)
                val socket = request.connect(protocol, connectionTimeout, readTimeout)
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
                        is ConnectException -> "Can't connect to this server: ${e.message}."
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
                    handleRedirectResponse(response, uri, redirects = redirects + 1)
                Response.Code.Category.SERVER_ERROR, Response.Code.Category.CLIENT_ERROR ->
                    handleErrorResponse(response)
                else ->
                    signalError("Can't handle code ${response.code}.")
            }
        }
    }

    /** Notify observers that an error happened, with a generic short message. Set state to idle. */
    private fun signalError(message: String) {
        event.postValue(FailureEvent("Error", message))
        state.postValue(State.IDLE)
    }

    /** Notify observers that user input has been requested. */
    private fun handleInputResponse(response: Response, uri: Uri) {
        event.postValue(InputEvent(uri, response.meta))
        state.postValue(State.IDLE)
    }

    /** Continue processing a successful response by looking at the provided MIME type. */
    @ExperimentalCoroutinesApi
    private suspend fun handleSuccessResponse(response: Response, uri: Uri) {
        val mimeType = MimeType.from(response.meta) ?: MimeType.DEFAULT  // Spec. section 3.3 last §
        when (mimeType.main) {
            "text" -> {
                if (mimeType.sub == "gemini")
                    handleSuccessGemtextResponse(response, uri)
                else
                    handleSuccessGenericTextResponse(response, uri)
            }
            else -> event.postValue(BinaryEvent(uri, response, mimeType))
        }
    }

    /** Receive Gemtext data, parse it and send the lines to observers. */
    @ExperimentalCoroutinesApi
    private suspend fun handleSuccessGemtextResponse(response: Response, uri: Uri) {
        state.postValue(State.RECEIVING)
        val uriString = uri.toString()

        linesList.clear()
        lines.postValue(Pair(uriString, linesList))
        val charset = Charset.defaultCharset()
        var mainTitle: String? = null
        var lastUpdate = System.currentTimeMillis()
        var lastNumLines = 0
        Log.d(TAG, "handleSuccessResponse: start parsing line data")
        try {
            val lineChannel = parseData(response.data, charset, viewModelScope)
            while (!lineChannel.isClosedForReceive) {
                val lineChannelResult = withTimeout(100) { lineChannel.tryReceive() }
                lineChannelResult.onSuccess { line ->
                    if (line is LinkLine) {
                        // Mark visited links here as we have a access to the history.
                        val fullUrl = resolveLinkUri(line.url, uriString).toString()
                        if (History.contains(fullUrl))
                            line.visited = true
                    }
                    linesList.add(line)
                    // Get the first level 1 header as the page main title.
                    if (mainTitle == null && line is TitleLine && line.level == 1)
                        mainTitle = line.text
                }

                // Throttle the recycler view updates to 100ms and new content only.
                if (linesList.size > lastNumLines) {
                    val time = System.currentTimeMillis()
                    if (time - lastUpdate >= 100) {
                        lines.postValue(Pair(uriString, linesList))
                        lastUpdate = time
                        lastNumLines = linesList.size
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.e(TAG, "handleSuccessResponse: coroutine cancelled: ${e.message}")
            state.postValue(State.IDLE)
            return
        }
        Log.d(TAG, "handleSuccessResponse: done parsing line data")
        lines.postValue(Pair(uriString, linesList))

        // We record the history entry here: it's nice because we have the main title available
        // and we're already in a coroutine for database access.
        History.record(uriString, mainTitle)
        event.postValue(SuccessEvent(uriString))
        state.postValue(State.IDLE)
    }

    /** Receive generic text data (e.g. text/plain) and send it to observers. */
    @ExperimentalCoroutinesApi
    private suspend fun handleSuccessGenericTextResponse(response: Response, uri: Uri) =
        handleSuccessGemtextResponse(response, uri)  // TODO render plain text as... something else?

    /** Notify observers that a redirect has been returned. */
    private fun handleRedirectResponse(response: Response, uri: Uri, redirects: Int) {
        event.postValue(RedirectEvent(response.meta, uri.toString(), redirects))
    }

    /**
     * Provide an error message to the user corresponding to the error code.
     *
     * TODO This requires a lot of localisation.
     */
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
        val longMessage: String = when (response.code) {
            Response.Code.TEMPORARY_FAILURE -> "The server encountered a temporary failure."
            Response.Code.SERVER_UNAVAILABLE -> "The server is currently unavailable."
            Response.Code.CGI_ERROR -> "A CGI script encountered an error."
            Response.Code.PROXY_ERROR -> "The server failed to proxy the request."
            Response.Code.SLOW_DOWN -> "You should wait ${response.meta.toIntOrNull() ?: "a few"} seconds before retrying."
            Response.Code.PERMANENT_FAILURE -> "This request failed and similar requests will likely fail as well."
            Response.Code.NOT_FOUND -> "This page can't be found."
            Response.Code.GONE -> "This page is gone."
            Response.Code.PROXY_REQUEST_REFUSED -> "The server refused to proxy the request."
            Response.Code.BAD_REQUEST -> "Bad request."
            else -> "Unknown error code."
        }
        var serverMessage: String? = null
        if (response.code != Response.Code.SLOW_DOWN && response.meta.isNotEmpty())
            serverMessage = response.meta
        event.postValue(FailureEvent(briefMessage, longMessage, serverMessage))
        state.postValue(State.IDLE)
    }

    /** Download response content as a file. */
    @ExperimentalCoroutinesApi
    fun downloadResponse(
        channel: Channel<ByteArray>,
        uri: Uri,
        mimeType: MimeType,
        contentResolver: ContentResolver
    ) {
        when (mimeType.main) {
            "image", "audio", "video" -> {
                downloadMedia(
                    channel, uri, mimeType, viewModelScope, contentResolver,
                    onSuccess = { mediaUri ->
                        event.postValue(DownloadCompletedEvent(mediaUri, mimeType))
                        state.postValue(State.IDLE)
                        viewModelScope.launch(Dispatchers.IO) { History.record(uri.toString()) }
                    },
                    onError = { msg -> signalError("Download failed: $msg") }
                )
            }
            else -> {
                // TODO use SAF
                signalError("MIME type unsupported yet: ${mimeType.main} (\"${mimeType.short}\")")
            }
        }
    }

    companion object {
        private const val TAG = "PageViewModel"
    }
}
package dev.lowrespalmtree.comet

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.lowrespalmtree.comet.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.onSuccess
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset

@ExperimentalCoroutinesApi
class MainActivity : AppCompatActivity(), ContentAdapter.ContentAdapterListen {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pageViewModel: PageViewModel
    private lateinit var adapter: ContentAdapter

    /** Property to access and set the current address bar URL value. */
    private var currentUrl
        get() = binding.addressBar.text.toString()
        set(value) = binding.addressBar.setText(value)

    /** A non-saved list of visited URLs. Not an history, just used for going back. */
    private val visitedUrls = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Database.init(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pageViewModel = ViewModelProvider(this)[PageViewModel::class.java]
        adapter = ContentAdapter(listOf(), this)
        binding.contentRecycler.layoutManager = LinearLayoutManager(this)
        binding.contentRecycler.adapter = adapter

        binding.addressBar.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                openUrl(view.text.toString())
                val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            } else {
                false
            }
        }

        binding.contentSwipeLayout.setOnRefreshListener { openUrl(currentUrl) }

        pageViewModel.state.observe(this, { updateState(it) })
        pageViewModel.lines.observe(this, { updateLines(it) })
        pageViewModel.event.observe(this, { handleEvent(it) })
    }

    override fun onBackPressed() {
        visitedUrls.removeLastOrNull()  // Always remove current page first.
        val previousUrl = visitedUrls.removeLastOrNull()
        if (previousUrl != null)
            openUrl(previousUrl)
        else
            super.onBackPressed()
    }

    override fun onLinkClick(url: String) {
        openUrl(url, base = if (currentUrl.isNotEmpty()) currentUrl else null)
    }

    private fun openUrl(url: String, base: String? = null, redirections: Int = 0) {
        if (redirections >= 5) {
            alert("Too many redirections.")
            return
        }

        var uri = Uri.parse(url)
        if (!uri.isAbsolute) {
            uri = if (!base.isNullOrEmpty()) joinUrls(base, url) else toGeminiUri(uri)
        }

        when (uri.scheme) {
            "gemini" -> pageViewModel.sendGeminiRequest(uri)
            else -> openUnknownScheme(uri)
        }
    }

    private fun updateState(state: PageViewModel.State) {
        Log.d(TAG, "updateState: $state")
        when (state) {
            PageViewModel.State.IDLE -> {
                binding.contentProgressBar.hide()
                binding.contentSwipeLayout.isRefreshing = false
            }
            PageViewModel.State.CONNECTING -> {
                binding.contentProgressBar.show()
            }
            PageViewModel.State.RECEIVING -> {
                binding.contentSwipeLayout.isRefreshing = false
            }
        }
    }

    private fun updateLines(lines: List<Line>) {
        Log.d(TAG, "updateLines: ${lines.size} lines")
        adapter.setContent(lines)
    }

    private fun handleEvent(event: PageViewModel.Event) {
        Log.d(TAG, "handleEvent: $event")
        if (!event.handled) {
            when (event) {
                is PageViewModel.SuccessEvent -> {
                    currentUrl = event.uri
                    visitedUrls.add(event.uri)
                }
                is PageViewModel.RedirectEvent -> openUrl(event.uri, redirections = event.redirects)
                is PageViewModel.FailureEvent -> alert(event.message)
            }
            event.handled = true
        }
    }

    private fun alert(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_alert_title)
            .setMessage(message)
            .create()
            .show()
    }

    private fun openUnknownScheme(uri: Uri) {
        try {
            startActivity(Intent(ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            alert("Can't open this URL.")
        }
    }

    class PageViewModel : ViewModel() {
        private var requestJob: Job? = null
        val state: MutableLiveData<State> by lazy { MutableLiveData<State>(State.IDLE) }
        private var linesList = ArrayList<Line>()
        val lines: MutableLiveData<List<Line>> by lazy { MutableLiveData<List<Line>>() }
        val event: MutableLiveData<Event> by lazy { MutableLiveData<Event>() }

        enum class State {
            IDLE, CONNECTING, RECEIVING
        }

        abstract class Event(var handled: Boolean = false)
        data class SuccessEvent(val uri: String) : Event()
        data class RedirectEvent(val uri: String, val redirects: Int) : Event()
        data class FailureEvent(val message: String) : Event()

        /**
         * Perform a request against this URI.
         *
         * The URI must be valid, absolute and with a gemini scheme.
         */
        fun sendGeminiRequest(uri: Uri, redirects: Int = 0) {
            Log.d(TAG, "sendRequest: URI \"$uri\"")
            state.postValue(State.CONNECTING)
            requestJob?.apply { if (isActive) cancel() }
            requestJob = viewModelScope.launch(Dispatchers.IO) {
                val response = try {
                    val request = Request(uri)
                    val socket = request.connect()
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
                    Response.Code.Category.SUCCESS -> handleRequestSuccess(response, uri)
                    Response.Code.Category.REDIRECT -> handleRedirect(
                        response,
                        redirects = redirects + 1
                    )
                    Response.Code.Category.SERVER_ERROR -> handleError(response)
                    else -> signalError("Can't handle code ${response.code}.")
                }
            }
        }

        private fun signalError(message: String) {
            event.postValue(FailureEvent(message))
            state.postValue(State.IDLE)
        }

        private suspend fun handleRequestSuccess(response: Response, uri: Uri) {
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

        private fun handleRedirect(response: Response, redirects: Int) {
            event.postValue(RedirectEvent(response.meta, redirects))
        }

        private fun handleError(response: Response) {
            event.postValue(
                FailureEvent(
                    when (response.code) {
                        Response.Code.TEMPORARY_FAILURE -> "40: the server encountered a temporary failure."
                        Response.Code.SERVER_UNAVAILABLE -> "41: the server is currently unavailable."
                        Response.Code.CGI_ERROR -> "42: a CGI script encountered an error."
                        Response.Code.PROXY_ERROR -> "43: the server failed to proxy the request."
                        Response.Code.SLOW_DOWN -> "44: slow down!"
                        Response.Code.PERMANENT_FAILURE -> "50: this request failed and similar requests will likely fail as well."
                        Response.Code.NOT_FOUND -> "51: this page can't be found."
                        Response.Code.GONE -> "52: this page is gone."
                        Response.Code.PROXY_REQUEST_REFUSED -> "53: the server refused to proxy the request."
                        Response.Code.BAD_REQUEST -> "59: bad request."
                        else -> "${response.code}: unknown error code."
                    }
                )
            )
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
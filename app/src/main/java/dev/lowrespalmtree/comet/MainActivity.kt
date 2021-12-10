package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.nio.charset.Charset

class MainActivity : AppCompatActivity(), ContentAdapter.ContentAdapterListen {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pageViewModel: PageViewModel
    private lateinit var adapter: ContentAdapter

    private val currentUrl get() = binding.addressBar.text

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        binding.contentSwipeLayout.setOnRefreshListener { openUrl(currentUrl.toString()) }

        pageViewModel.state.observe(this, { updateState(it) })
        pageViewModel.lines.observe(this, { updateLines(it) })
        pageViewModel.alert.observe(this, { alert(it) })
    }

    override fun onLinkClick(url: String) {
        val base = binding.addressBar.text.toString()
        openUrl(url, base = if (base.isNotEmpty()) base else null)
    }

    private fun openUrl(url: String, base: String? = null) {
        var uri = Uri.parse(url)
        if (!uri.isAbsolute) {
            uri = if (!base.isNullOrEmpty()) joinUrls(base, url) else toGeminiUri(uri)
        }

        when (uri.scheme) {
            "gemini" -> {
                binding.addressBar.setText(uri.toString())
                pageViewModel.sendGeminiRequest(uri)
            }
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

    private fun alert(message: String, title: String? = null) {
        val builder = AlertDialog.Builder(this)
        if (title != null)
            builder.setTitle(title)
        else
            builder.setTitle(title ?: R.string.alert_title)
        builder
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
        val alert: MutableLiveData<String> by lazy { MutableLiveData<String>() }

        enum class State {
            IDLE, CONNECTING, RECEIVING
        }

        /**
         * Perform a request against this URI.
         *
         * The URI must be valid, absolute and with a gemini scheme.
         */
        fun sendGeminiRequest(uri: Uri) {
            Log.d(TAG, "sendRequest: URI \"$uri\"")
            state.postValue(State.CONNECTING)
            requestJob?.apply { if (isActive) cancel() }
            requestJob = viewModelScope.launch(Dispatchers.IO) {
                val response = try {
                    val request = Request(uri)
                    val socket = request.connect()
                    val channel = request.proceed(socket, this)
                    Response.from(channel, viewModelScope)
                } catch (e: UnknownHostException) {
                    signalError("Unknown host \"${uri.authority}\".")
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "sendGeminiRequest coroutine: ${e.stackTraceToString()}")
                    signalError("Oops! Whatever we tried to do failed!")
                    return@launch
                }
                if (response == null) {
                    signalError("Can't parse server response.")
                    return@launch
                }

                Log.i(TAG, "sendRequest: got ${response.code} with meta \"${response.meta}\"")
                when (response.code) {
                    Response.Code.SUCCESS -> handleRequestSuccess(response)
                    else -> signalError("Can't handle code ${response.code}.")
                }
            }
        }

        private fun signalError(message: String) {
            alert.postValue(message)
            state.postValue(State.IDLE)
        }

        private suspend fun handleRequestSuccess(response: Response) {
            state.postValue(State.RECEIVING)
            linesList.clear()
            lines.postValue(linesList)
            val charset = Charset.defaultCharset()
            var lastUpdate = System.currentTimeMillis()
            for (line in parseData(response.data, charset, viewModelScope)) {
                linesList.add(line)
                val time = System.currentTimeMillis()
                if (time - lastUpdate >= 100) {  // Throttle to 100ms the recycler view updates…
                    lines.postValue(linesList)
                    lastUpdate = time
                }
            }
            lines.postValue(linesList)
            state.postValue(State.IDLE)
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
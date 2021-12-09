package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.app.Activity
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
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.nio.charset.Charset

class MainActivity : AppCompatActivity(), ContentAdapter.ContentAdapterListen {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pageViewModel: PageViewModel
    private lateinit var adapter: ContentAdapter

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

        pageViewModel.linesLiveData.observe(this, { adapter.setContent(it) })
        pageViewModel.alertLiveData.observe(this, { alert(it) })
    }

    override fun onLinkClick(url: String) {
        val base = binding.addressBar.text.toString()
        openUrl(url, base = if (base.isNotEmpty()) base else null)
    }

    private fun openUrl(url: String, base: String? = null) {
        var uri = Uri.parse(url)
        if (!uri.isAbsolute) {
            uri = if (!base.isNullOrEmpty()) joinUrls(base, url) else toGeminiUri(uri)
            Log.d(TAG, "openUrl: '$url' - '$base' - '$uri'")
            Log.d(TAG, "openUrl: ${uri.authority} - ${uri.path} - ${uri.query}")
            binding.addressBar.setText(uri.toString())
        }

        when (uri.scheme) {
            "gemini" -> pageViewModel.sendGeminiRequest(uri)
            else -> startActivity(Intent(ACTION_VIEW, uri))
        }
    }

    private fun alert(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.alert_title)
            .setMessage(message)
            .create()
            .show()
    }

    class PageViewModel : ViewModel() {
        private var lines = ArrayList<Line>()
        val linesLiveData: MutableLiveData<List<Line>> by lazy { MutableLiveData<List<Line>>() }
        val alertLiveData: MutableLiveData<String> by lazy { MutableLiveData<String>() }

        /**
         * Perform a request against this URI.
         *
         * The URI must be valid, absolute and with a gemini scheme.
         */
        fun sendGeminiRequest(uri: Uri) {
            Log.d(TAG, "sendRequest: $uri")
            viewModelScope.launch(Dispatchers.IO) {
                val response = try {
                    val request = Request(uri)
                    val socket = request.connect()
                    val channel = request.proceed(socket, this)
                    Response.from(channel, viewModelScope)
                } catch (e: UnknownHostException) {
                    alertLiveData.postValue("Unknown host \"${uri.authority}\".")
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "sendGeminiRequest coroutine: ${e.stackTraceToString()}")
                    alertLiveData.postValue("Oops! Whatever we tried to do failed!")
                    return@launch
                }
                if (response == null) {
                    alertLiveData.postValue("Can't parse server response.")
                    return@launch
                }

                Log.i(TAG, "sendRequest: got ${response.code} with meta \"${response.meta}\"")
                when (response.code) {
                    Response.Code.SUCCESS -> handleRequestSuccess(response)
                    else -> alertLiveData.postValue("Can't handle code ${response.code}.")
                }
            }

        }

        private suspend fun handleRequestSuccess(response: Response) {
            lines.clear()
            val charset = Charset.defaultCharset()
            for (line in parseData(response.data, charset, viewModelScope)) {
                lines.add(line)
                linesLiveData.postValue(lines)
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
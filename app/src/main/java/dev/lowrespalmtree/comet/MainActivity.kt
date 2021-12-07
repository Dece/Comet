package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.app.Activity
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
import dev.lowrespalmtree.comet.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pageViewModel: PageViewModel

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pageViewModel = ViewModelProvider(this)[PageViewModel::class.java]

        binding.addressBar.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                pageViewModel.sendRequest(view.text.toString())
                val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            } else {
                false
            }
        }

        pageViewModel.sourceLiveData.observe(this, {
            binding.sourceBlock.text = it
        })
        pageViewModel.alertLiveData.observe(this, {
            AlertDialog.Builder(this)
                .setTitle(R.string.alert_title)
                .setMessage(it)
                .create()
                .show()
        })
    }

    class PageViewModel : ViewModel() {
        var source = ""
        val sourceLiveData: MutableLiveData<String> by lazy { MutableLiveData<String>() }
        val alertLiveData: MutableLiveData<String> by lazy { MutableLiveData<String>() }

        fun sendRequest(url: String) {
            Log.d(TAG, "sendRequest: $url")
            source = ""
            viewModelScope.launch(Dispatchers.IO) {
                val uri = Uri.parse(url)
                if (uri.scheme != "gemini") {
                    alertLiveData.postValue("Can't process scheme \"${uri.scheme}\".")
                    return@launch
                }

                val request = Request(uri)
                val socket = request.connect()
                val channel = request.proceed(socket, this)
                val response = Response.from(channel, viewModelScope)
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
            val charset = Charset.defaultCharset()
            for (line in parseData(response.data, charset, viewModelScope)) {
                when (line) {
                    is EmptyLine -> { source += "\n" }
                    is ParagraphLine -> { source += line.text + "\n" }
                    is TitleLine -> { source += "TTL-${line.level} ${line.text}\n" }
                    is LinkLine -> { source += "LNK ${line.url} + ${line.label}\n" }
                    is PreFenceLine -> { source += "PRE ${line.caption}\n" }
                    is PreTextLine -> { source += line.text + "\n" }
                    is BlockquoteLine -> { source += "QUO ${line.text}\n" }
                    is ListItemLine -> { source += "LST ${line.text}\n" }
                }
                sourceLiveData.postValue(source)
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
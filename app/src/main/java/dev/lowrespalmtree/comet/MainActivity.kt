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
                    alertLiveData.postValue("Unknown scheme.")
                    return@launch
                }
                val request = Request(uri)
                val socket = request.connect()
                val channel = request.proceed(socket, this)
                val charset = Charset.defaultCharset()
                for (data in channel) {
                    val decoded = charset.decode(ByteBuffer.wrap(data)).toString()
                    source += decoded
                    sourceLiveData.postValue(source)
                }
            }
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
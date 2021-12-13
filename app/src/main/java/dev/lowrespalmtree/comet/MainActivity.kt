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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import dev.lowrespalmtree.comet.databinding.ActivityMainBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
                is PageViewModel.RedirectEvent -> {
                    openUrl(event.uri, base = currentUrl, redirections = event.redirects)
                }
                is PageViewModel.FailureEvent -> {
                    var message = event.details
                    if (!event.serverDetails.isNullOrEmpty())
                        message += "\n\n" + "Server details: ${event.serverDetails}"
                    alert(message, title = event.short)
                }
            }
            event.handled = true
        }
    }

    private fun alert(message: String, title: String? = null) {
        AlertDialog.Builder(this)
            .setTitle(title ?: getString(R.string.error_alert_title))
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

    companion object {
        const val TAG = "MainActivity"
    }
}
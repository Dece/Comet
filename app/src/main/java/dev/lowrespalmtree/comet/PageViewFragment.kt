package dev.lowrespalmtree.comet

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import dev.lowrespalmtree.comet.databinding.FragmentPageViewBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class PageViewFragment : Fragment(), ContentAdapter.ContentAdapterListener {
    private lateinit var binding: FragmentPageViewBinding
    private lateinit var pageViewModel: PageViewModel
    private lateinit var adapter: ContentAdapter

    /** Property to access and set the current address bar URL value. */
    private var currentUrl
        get() = binding.addressBar.text.toString()
        set(value) = binding.addressBar.setText(value)

    /** A non-saved list of visited URLs. Not an history, just used for going back. */
    private val visitedUrls = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPageViewBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pageViewModel = ViewModelProvider(this)[PageViewModel::class.java]
        adapter = ContentAdapter(this)
        binding.contentRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.contentRecycler.adapter = adapter

        binding.addressBar.setOnEditorActionListener { v, id, _ -> onAddressBarAction(v, id) }

        binding.contentSwipeLayout.setOnRefreshListener { openUrl(currentUrl) }

        pageViewModel.state.observe(viewLifecycleOwner, { updateState(it) })
        pageViewModel.lines.observe(viewLifecycleOwner, { updateLines(it) })
        pageViewModel.event.observe(viewLifecycleOwner, { handleEvent(it) })

        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) { onBackPressed() }
    }

    override fun onLinkClick(url: String) {
        openUrl(url, base = if (currentUrl.isNotEmpty()) currentUrl else null)
    }

    private fun onBackPressed() {
        if (visitedUrls.size >= 2) {
            visitedUrls.removeLastOrNull()  // Always remove current page first.
            openUrl(visitedUrls.removeLastOrNull()!!)
        }
    }

    private fun onAddressBarAction(addressBar: TextView, actionId: Int): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            openUrl(addressBar.text.toString())
            activity?.run {
                val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(addressBar.windowToken, 0)
            }
            addressBar.clearFocus()
            return true
        }
        return false
    }

    /**
     * Open an URL.
     *
     * This function can be called after the user entered an URL in the app bar, clicked on a link,
     * whatever. To make the user's life a bit easier, this function also makes a few guesses:
     * - If the URL is not absolute, make it so from a base URL (e.g. the current URL) or assume
     *   the user only typed a hostname without scheme and use a utility function to make it
     *   absolute.
     * - If it's an absolute Gemini URL with an empty path, use "/" instead as per the spec.
     */
    private fun openUrl(url: String, base: String? = null, redirections: Int = 0) {
        if (redirections >= 5) {
            alert("Too many redirections.")
            return
        }

        var uri = Uri.parse(url)
        if (!uri.isAbsolute) {
            uri = if (!base.isNullOrEmpty()) joinUrls(base, url) else toGeminiUri(uri)
        } else if (uri.scheme == "gemini" && uri.path.isNullOrEmpty()) {
            uri = uri.buildUpon().path("/").build()
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
                binding.appBarLayout.setExpanded(true, true)
                binding.contentSwipeLayout.isRefreshing = false
            }
        }
    }

    private fun updateLines(lines: List<Line>) {
        Log.d(TAG, "updateLines: ${lines.size} lines")
        adapter.setLines(lines)
    }

    private fun handleEvent(event: PageViewModel.Event) {
        Log.d(TAG, "handleEvent: $event")
        if (!event.handled) {
            when (event) {
                is PageViewModel.InputEvent -> {
                    val editText = EditText(requireContext())
                    editText.inputType = InputType.TYPE_CLASS_TEXT
                    val inputView = FrameLayout(requireContext()).apply {
                        addView(FrameLayout(requireContext()).apply {
                            addView(editText)
                            val params = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                            params.setMargins(resources.getDimensionPixelSize(R.dimen.text_margin))
                            layoutParams = params
                        })
                    }
                    AlertDialog.Builder(requireContext())
                        .setMessage(if (event.prompt.isNotEmpty()) event.prompt else "Input required")
                        .setView(inputView)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val newUri =
                                event.uri.buildUpon().query(editText.text.toString()).build()
                            openUrl(newUri.toString(), base = currentUrl)
                        }
                        .setOnDismissListener { updateState(PageViewModel.State.IDLE) }
                        .create()
                        .show()
                }
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
                        message += "\n\nServer details: ${event.serverDetails}"
                    if (!isConnectedToNetwork(requireContext()))
                        message += "\n\nInternet may be inaccessible…"
                    alert(message, title = event.short)
                    updateState(PageViewModel.State.IDLE)
                }
            }
            event.handled = true
        }
    }

    private fun alert(message: String, title: String? = null) {
        AlertDialog.Builder(requireContext())
            .setTitle(title ?: getString(R.string.error_alert_title))
            .setMessage(message)
            .create()
            .show()
    }

    private fun openUnknownScheme(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            alert("Can't find an app to open \"${uri.scheme}\" URLs.")
        }
    }

    companion object {
        private const val TAG = "PageViewFragment"
    }
}
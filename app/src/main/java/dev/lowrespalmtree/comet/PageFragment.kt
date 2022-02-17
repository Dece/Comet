package dev.lowrespalmtree.comet

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dev.lowrespalmtree.comet.databinding.FragmentPageViewBinding
import dev.lowrespalmtree.comet.utils.isConnectedToNetwork
import dev.lowrespalmtree.comet.utils.resolveLinkUri
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class PageFragment : Fragment(), PageAdapter.Listener {
    private val vm: PageViewModel by viewModels()
    private lateinit var binding: FragmentPageViewBinding
    private lateinit var adapter: PageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        binding = FragmentPageViewBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated")
        binding.contentRecycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = PageAdapter(this)
        binding.contentRecycler.adapter = adapter

        binding.addressBar.setOnEditorActionListener { v, id, _ -> onAddressBarAction(v, id) }

        binding.contentSwipeLayout.setOnRefreshListener { openUrl(vm.currentUrl) }

        vm.state.observe(viewLifecycleOwner) { updateState(it) }
        vm.lines.observe(viewLifecycleOwner) { updateLines(it.second, it.first) }
        vm.event.observe(viewLifecycleOwner) { handleEvent(it) }

        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) { onBackPressed() }

        val url = arguments?.getString("url")
        if (!url.isNullOrEmpty()) {
            Log.d(TAG, "onViewCreated: open \"$url\"")
            openUrl(url)
        } else if (vm.currentUrl.isNotEmpty()) {
            Log.d(TAG, "onViewCreated: reuse current URL, probably fragment recreation")
        } else if (vm.visitedUrls.isEmpty()) {
            Log.d(TAG, "onViewCreated: no current URL, open home if configured")
            Preferences.getHomeUrl(requireContext())?.let { if (it.isNotBlank()) openUrl(it) }
        }
    }

    override fun onLinkClick(url: String) {
        openUrl(url, base = vm.currentUrl.ifEmpty { null })
    }

    private fun onBackPressed() {
        if (vm.visitedUrls.size >= 2) {
            vm.visitedUrls.removeLastOrNull()  // Always remove current page first.
            vm.visitedUrls.removeLastOrNull()?.also { openUrl(it) }
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

        val uri = resolveLinkUri(url, base)
        when (uri.scheme) {
            "gemini" -> vm.sendGeminiRequest(uri, requireContext())
            else -> openUnknownScheme(uri)
        }
    }

    private fun updateState(state: PageViewModel.State) {
        Log.d(TAG, "updateState: $state")
        when (state) {
            PageViewModel.State.IDLE -> {
                binding.contentProgressBar.hide()
                binding.contentSwipeLayout.isRefreshing = false
                binding.addressBar.setText(vm.currentUrl)
                binding.addressBar.setTextColor(resources.getColor(R.color.url_bar, null))
            }
            PageViewModel.State.CONNECTING -> {
                binding.appBarLayout.setExpanded(true, true)
                binding.contentProgressBar.show()
                binding.addressBar.setText(vm.loadingUrl?.toString() ?: "")
                binding.addressBar.setTextColor(resources.getColor(R.color.url_bar_loading, null))
            }
            PageViewModel.State.RECEIVING -> {
                binding.contentSwipeLayout.isRefreshing = false
            }
        }
    }

    private fun updateLines(lines: List<Line>, url: String) {
        Log.d(TAG, "updateLines: ${lines.size} lines from $url")
        adapter.setLines(lines)
    }

    private fun handleEvent(event: PageViewModel.Event) {
        Log.d(TAG, "handleEvent: $event")
        if (event.handled)
            return
        when (event) {
            is PageViewModel.InputEvent -> {
                InputDialog(requireContext(), event.prompt.ifEmpty { "Input required" })
                    .show(
                        onOk = { text ->
                            val newUri = event.uri.buildUpon().query(text).build()
                            openUrl(newUri.toString(), base = vm.currentUrl)
                        },
                        onDismiss = {}
                    )
            }
            is PageViewModel.SuccessEvent -> {
                vm.currentUrl = event.uri
                vm.visitedUrls.add(event.uri)
                binding.addressBar.setText(event.uri)
            }
            is PageViewModel.BinaryEvent -> {
                // TODO this should present the user with options on what to do according to the
                // MIME type: show inline, save in the media store, save as generic download, etc.
                vm.downloadResponse(
                    event.response.data,
                    event.uri,
                    event.mimeType,
                    requireContext().contentResolver
                )
            }
            is PageViewModel.DownloadCompletedEvent -> {
                val message = when (event.mimeType.main) {
                    "image" -> R.string.image_download_completed
                    else -> R.string.download_completed
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.open) {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(event.uri, event.mimeType.short)
                        })
                    }
                    .show()
            }
            is PageViewModel.RedirectEvent -> {
                openUrl(
                    event.uri,
                    base = vm.currentUrl.ifEmpty { event.sourceUri },
                    redirections = event.redirects
                )
            }
            is PageViewModel.FailureEvent -> {
                var message = event.details
                if (!event.serverDetails.isNullOrEmpty())
                    message += "\n\nServer details: ${event.serverDetails}"
                if (!isConnectedToNetwork(requireContext()))
                    message += "\n\nInternet may be inaccessible…"
                alert(message, title = event.short)
            }
        }
        event.handled = true
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
        private const val TAG = "PageFragment"
    }
}
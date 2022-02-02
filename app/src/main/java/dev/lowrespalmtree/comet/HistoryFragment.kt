package dev.lowrespalmtree.comet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dev.lowrespalmtree.comet.History.HistoryEntry
import dev.lowrespalmtree.comet.HistoryAdapter.HistoryItemAdapterListener
import dev.lowrespalmtree.comet.databinding.FragmentHistoryListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
class HistoryFragment : Fragment(), HistoryItemAdapterListener {
    private val vm: HistoryViewModel by viewModels()
    private lateinit var binding: FragmentHistoryListBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryListBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HistoryAdapter(this)
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        vm.items.observe(viewLifecycleOwner) { adapter.setItems(it) }

        vm.refreshHistory()
    }

    override fun onItemClick(url: String) {
        val bundle = bundleOf("url" to url)
        findNavController().navigate(R.id.action_global_pageFragment, bundle)
    }

    @ExperimentalCoroutinesApi
    class HistoryViewModel(
        @Suppress("unused") private val savedStateHandle: SavedStateHandle
    ) : ViewModel() {
        val items: MutableLiveData<List<HistoryEntry>>
                by lazy { MutableLiveData<List<HistoryEntry>>() }

        fun refreshHistory() {
            viewModelScope.launch(Dispatchers.IO) {
                items.postValue(History.getAll())
            }
        }
    }
}
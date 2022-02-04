package dev.lowrespalmtree.comet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dev.lowrespalmtree.comet.Identities.Identity
import dev.lowrespalmtree.comet.databinding.FragmentIdentitiesBinding
import dev.lowrespalmtree.comet.utils.confirm
import dev.lowrespalmtree.comet.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class IdentitiesFragment : Fragment(), IdentitiesAdapter.Listener, IdentityEditDialog.Listener {
    private val vm: IdentitiesViewModel by viewModels()
    private lateinit var binding: FragmentIdentitiesBinding
    private lateinit var adapter: IdentitiesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentIdentitiesBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val lm = LinearLayoutManager(requireContext())
        binding.list.layoutManager = lm
        binding.list.addItemDecoration(DividerItemDecoration(context, lm.orientation))
        adapter = IdentitiesAdapter(this)
        binding.list.adapter = adapter

        binding.floatingActionButton.setOnClickListener { openIdentityWizard() }

        vm.identities.observe(viewLifecycleOwner) { adapter.setIdentities(it) }

        vm.refreshIdentities()
    }

    override fun onIdentityClick(identity: Identity) {
        IdentityEditDialog(requireContext(), identity, this).show()
    }

    override fun onIdentityLongClick(identity: Identity, view: View) {
        PopupMenu(requireContext(), view)
            .apply {
                menuInflater.inflate(R.menu.identity, this.menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.item_edit -> {
                            IdentityEditDialog(
                                requireContext(),
                                identity,
                                this@IdentitiesFragment
                            ).show()
                        }
                        R.id.item_delete -> {
                            confirm(requireContext(), R.string.confirm_identity_delete) {
                                vm.deleteIdentity(identity)
                            }
                        }
                        else -> {}
                    }
                    true
                }
            }
            .show()
    }

    override fun onSaveIdentity(identity: Identity) {
        vm.saveIdentity(identity)
    }

    /**
     * Open the new identity wizard.
     *
     * There is a first dialog to ask the user about the desired subject common name,
     * then the certificate is generated and the edition dialog is opened.
     */
    private fun openIdentityWizard() {
        InputDialog(requireContext(), getString(R.string.input_common_name))
            .show(
                onOk = { text ->
                    toast(requireContext(), R.string.generating_keypair)
                    vm.newIdentity.observe(viewLifecycleOwner) { identity ->
                        if (identity == null)
                            return@observe
                        vm.newIdentity.removeObservers(viewLifecycleOwner)
                        vm.newIdentity.value = null
                        IdentityEditDialog(requireContext(), identity, this).show()
                    }
                    vm.createNewIdentity(text)
                },
                onDismiss = {}
            )
    }

    class IdentitiesViewModel : ViewModel() {
        val identities: MutableLiveData<List<Identity>> by lazy { MutableLiveData<List<Identity>>() }
        val newIdentity: MutableLiveData<Identity> by lazy { MutableLiveData<Identity>() }

        fun createNewIdentity(commonName: String) {
            viewModelScope.launch(Dispatchers.IO) {
                val alias = "identity-${UUID.randomUUID()}"
                Identities.generateClientCert(alias, commonName)
                val newIdentityId = Identities.insert(alias, commonName)
                newIdentity.postValue(Identities.get(newIdentityId))
            }
                .invokeOnCompletion { refreshIdentities() }
        }

        fun refreshIdentities() {
            viewModelScope.launch(Dispatchers.IO) { identities.postValue(Identities.getAll()) }
        }

        fun saveIdentity(identity: Identity) {
            viewModelScope.launch(Dispatchers.IO) { Identities.update(identity) }
                .invokeOnCompletion { refreshIdentities() }
        }

        fun deleteIdentity(identity: Identity) {
            viewModelScope.launch(Dispatchers.IO) { Identities.delete(identity) }
                .invokeOnCompletion { refreshIdentities() }
        }
    }

    companion object {
        private const val TAG = "IdentitiesFragment"
    }
}
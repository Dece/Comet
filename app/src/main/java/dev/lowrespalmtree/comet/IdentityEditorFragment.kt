package dev.lowrespalmtree.comet

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import dev.lowrespalmtree.comet.Identities.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class IdentityEditorFragment : PreferenceFragmentCompat() {
    private val vm: IdentityEditorViewModel by viewModels()
    private lateinit var namePref: EditTextPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.identity_preferences, rootKey)
        namePref = findPreference<EditTextPreference>("name")!!

//        vm.identity.observe(viewLifecycleOwner) {
//            namePref.apply {
//                // TODO
//            }
//        }
//
//        arguments?.getLong("id")?.also { vm.loadIdentity(it) }
    }

    class IdentityEditorViewModel : ViewModel() {
        val identity: MutableLiveData<Identity> by lazy { MutableLiveData<Identity>() }

        fun loadIdentity(id: Long) {
            viewModelScope.launch(Dispatchers.IO) {
                identity.postValue(Identities.get(id))
            }
        }
    }
}
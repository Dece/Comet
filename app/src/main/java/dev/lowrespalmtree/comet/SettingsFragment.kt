package dev.lowrespalmtree.comet

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dev.lowrespalmtree.comet.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        findPreference<Preference>("home_set")?.setOnPreferenceClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val lastEntry = History.getLast()
                launch(Dispatchers.Main) {
                    if (lastEntry != null)
                        findPreference<EditTextPreference>("home")?.text = lastEntry.uri
                    else
                        toast(requireContext(), R.string.no_current_url)
                }
            }
            true
        }
    }
}
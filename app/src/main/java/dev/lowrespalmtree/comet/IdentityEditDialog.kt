package dev.lowrespalmtree.comet

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import dev.lowrespalmtree.comet.databinding.DialogIdentityBinding

class IdentityEditDialog(
    private val context: Context,
    private val identity: Identities.Identity,
    private val listener: Listener
) {
    private lateinit var binding: DialogIdentityBinding

    interface Listener {
        fun onSaveIdentity(identity: Identities.Identity)
    }

    fun show() {
        binding = DialogIdentityBinding.inflate(LayoutInflater.from(context))
        binding.labelInput.setText(identity.name.orEmpty())
        binding.urlInput.setText(identity.urls.getOrNull(0).orEmpty())
        binding.aliasText.text = identity.key
        AlertDialog.Builder(context)
            .setTitle(R.string.edit_identity)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                identity.name = binding.labelInput.text.toString()
                identity.urls = arrayListOf(binding.urlInput.text.toString())
                listener.onSaveIdentity(identity)
            }
            .create()
            .show()
    }
}
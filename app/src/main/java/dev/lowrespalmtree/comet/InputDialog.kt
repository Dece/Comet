package dev.lowrespalmtree.comet

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import dev.lowrespalmtree.comet.databinding.DialogInputBinding

/** Generic text input dialog. Used for code 10 and a few other simple text input. */
class InputDialog(
    private val context: Context,
    private val prompt: String
) {
    fun show(onOk: (text: String) -> Unit, onDismiss: () -> Unit) {
        val binding = DialogInputBinding.inflate(LayoutInflater.from(context))
        AlertDialog.Builder(context)
            .setMessage(prompt)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(binding.textInput.text.toString()) }
            .setOnDismissListener { onDismiss() }
            .create()
            .show()
    }
}
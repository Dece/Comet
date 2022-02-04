package dev.lowrespalmtree.comet.utils

import android.app.AlertDialog
import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import dev.lowrespalmtree.comet.R
import dev.lowrespalmtree.comet.databinding.DialogConfirmBinding

fun toast(context: Context, stringId: Int, length: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(context, stringId, length).show()

fun getDrawableFromAttr(context: Context, @AttrRes attr: Int) =
    TypedValue()
        .apply { context.theme.resolveAttribute(attr, this, true) }
        .resourceId

fun getFancySelectBgRes(context: Context) =
    getDrawableFromAttr(context, R.attr.selectableItemBackground)

fun confirm(context: Context, @StringRes prompt: Int, onOk: () -> Unit) {
    val binding = DialogConfirmBinding.inflate(LayoutInflater.from(context))
    binding.textView.setText(prompt)
    AlertDialog.Builder(context)
        .setTitle(R.string.confirm)
        .setView(binding.root)
        .setPositiveButton(android.R.string.ok) { _, _ -> onOk() }
        .create()
        .show()
}
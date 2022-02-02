package dev.lowrespalmtree.comet.utils

import android.content.Context
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.AttrRes
import dev.lowrespalmtree.comet.R

fun toast(context: Context, stringId: Int, length: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(context, stringId, length).show()

fun getDrawableFromAttr(context: Context, @AttrRes attr: Int) =
    TypedValue()
        .apply { context.theme.resolveAttribute(attr, this, true) }
        .resourceId

fun getFancySelectBgRes(context: Context) =
    getDrawableFromAttr(context, R.attr.selectableItemBackground)
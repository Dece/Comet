package dev.lowrespalmtree.comet

import android.content.Context
import android.widget.Toast

fun toast(context: Context, stringId: Int, length: Int = Toast.LENGTH_SHORT) =
    Toast.makeText(context, stringId, length).show()
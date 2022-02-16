package dev.lowrespalmtree.comet.utils

import android.os.Build

/** Return true if the device is running Android 10 ("Q") or higher. */
fun isPostQ() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
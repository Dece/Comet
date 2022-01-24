package dev.lowrespalmtree.comet

import android.content.Context
import androidx.preference.PreferenceManager

object Preferences {
    fun getHomeUrl(context: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(context).getString("home", null)
}
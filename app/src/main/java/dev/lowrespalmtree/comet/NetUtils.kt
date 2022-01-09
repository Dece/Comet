package dev.lowrespalmtree.comet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun isConnectedToNetwork(context: Context): Boolean {
    val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = connManager.getNetworkCapabilities(connManager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

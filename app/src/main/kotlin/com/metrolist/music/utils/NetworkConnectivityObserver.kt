/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple NetworkConnectivityObserver based on OuterTune's implementation
 * Provides network connectivity monitoring for auto-play functionality
 */
class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(isCurrentlyConnected())
    val networkStatus = _networkStatus.asStateFlow()

    // Follows the default network, the one apps actually use, and reads its state from the
    // capabilities Android hands over. Asking for the active network inside the callback raced:
    // when the connection came back, the check ran before Android had validated it (or made it
    // the default), saw no internet, and no later event corrected it, so "You're offline" stayed.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.value = hasInternet(connectivityManager.getNetworkCapabilities(network))
        }

        override fun onLost(network: Network) {
            _networkStatus.value = false
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _networkStatus.value = hasInternet(networkCapabilities)
        }
    }

    init {
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Fallback: assume connected if registration fails
            _networkStatus.value = true
        }
    }

    fun unregister() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
    
    /**
     * Check current connectivity state synchronously
     */
    fun isCurrentlyConnected(): Boolean {
        return try {
            hasInternet(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork))
        } catch (e: Exception) {
            false
        }
    }

    // Internet that Android has checked actually works: a Wi-Fi without internet, or a network
    // being set up, does not count (pages then said "Something went wrong").
    private fun hasInternet(capabilities: NetworkCapabilities?): Boolean =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

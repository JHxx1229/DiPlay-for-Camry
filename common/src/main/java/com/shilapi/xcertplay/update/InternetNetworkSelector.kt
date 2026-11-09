package com.shilapi.xcertplay.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.URL
import java.net.URLConnection

/**
 * Finds an internet route without changing the process-wide network used by CarPlay.
 * Prefers system-validated routes, but falls back to unvalidated non-VPN internet routes
 * for head units whose system captive-portal check cannot reach its probe server.
 */
internal object InternetNetworkSelector {
    fun select(context: Context): Network? = runCatching {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching null
        select(connectivity)
    }.getOrNull()

    fun select(connectivity: ConnectivityManager): Network? {
        val active = connectivity.activeNetwork
        val candidates = buildList {
            if (active != null) add(active)
            connectivity.allNetworks.forEach { if (it != active) add(it) }
        }.mapNotNull { network ->
            val capabilities = runCatching { connectivity.getNetworkCapabilities(network) }.getOrNull()
            capabilities?.takeIf(::isCandidateInternet)?.let { network to it }
        }
        return (candidates.firstOrNull {
            isValidatedInternet(it.second)
        } ?: candidates.firstOrNull())?.first
    }

    fun openConnection(context: Context, url: URL): URLConnection =
        select(context)?.openConnection(url) ?: url.openConnection()

    fun isCandidateInternet(capabilities: NetworkCapabilities): Boolean =
        isCandidateInternet(
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            captivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
        )

    internal fun isCandidateInternet(
        hasInternet: Boolean,
        captivePortal: Boolean = false,
        vpn: Boolean = false,
    ): Boolean = hasInternet && !captivePortal && !vpn

    fun isValidatedInternet(capabilities: NetworkCapabilities): Boolean =
        isCandidateInternet(capabilities) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    internal fun isValidatedInternet(
        hasInternet: Boolean,
        validated: Boolean,
        captivePortal: Boolean = false,
        vpn: Boolean = false,
    ): Boolean = isCandidateInternet(hasInternet, captivePortal, vpn) && validated
}

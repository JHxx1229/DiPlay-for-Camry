package com.shilapi.xcertplay.update

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29])
class InternetNetworkSelectorTest {
    private lateinit var manager: ConnectivityManager

    @Before
    fun setUp() {
        manager = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        shadowOf(manager).clearAllNetworks()
        shadowOf(manager).setActiveNetworkInfo(null)
    }

    @Suppress("DEPRECATION")
    private fun network(
        id: Int,
        transport: Int,
        internet: Boolean = true,
        validated: Boolean = false,
        captive: Boolean = false,
    ): Network {
        val network = ShadowNetwork.newInstance(id)
        val type = if (transport == NetworkCapabilities.TRANSPORT_CELLULAR) ConnectivityManager.TYPE_MOBILE
        else ConnectivityManager.TYPE_WIFI
        val info = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, type, 0, true, true)
        shadowOf(manager).addNetwork(network, info)
        val capabilities = NetworkCapabilities()
        shadowOf(capabilities).addTransportType(transport)
        if (internet) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (captive) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        shadowOf(manager).setNetworkCapabilities(network, capabilities)
        return network
    }

    @Test
    fun validatedCheckStillDistinguishesValidatedFromUnvalidated() {
        assertFalse(InternetNetworkSelector.isValidatedInternet(hasInternet = true, validated = false))
        assertTrue(InternetNetworkSelector.isValidatedInternet(hasInternet = true, validated = true))
        assertFalse(InternetNetworkSelector.isValidatedInternet(hasInternet = true, validated = true, vpn = true))
        assertFalse(InternetNetworkSelector.isValidatedInternet(hasInternet = true, validated = true, captivePortal = true))
    }

    @Test
    fun fallsBackToUnvalidatedInternetWhenHeadUnitValidationIsUnavailable() {
        network(101, NetworkCapabilities.TRANSPORT_WIFI, internet = false)
        val cellular = network(102, NetworkCapabilities.TRANSPORT_CELLULAR, validated = false)
        assertEquals(cellular, InternetNetworkSelector.select(manager))
    }

    @Test
    fun prefersValidatedInternetOverUnvalidatedRoute() {
        network(101, NetworkCapabilities.TRANSPORT_CELLULAR, validated = false)
        val wifi = network(102, NetworkCapabilities.TRANSPORT_WIFI, validated = true)
        assertEquals(wifi, InternetNetworkSelector.select(manager))
    }

    @Test
    fun rejectsLocalOnlyCaptivePortalAndVpnNetworks() {
        network(101, NetworkCapabilities.TRANSPORT_WIFI, internet = false)
        network(102, NetworkCapabilities.TRANSPORT_WIFI, captive = true)
        network(103, NetworkCapabilities.TRANSPORT_VPN, validated = true)
        assertNull(InternetNetworkSelector.select(manager))
    }
}

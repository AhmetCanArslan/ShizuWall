package com.arslan.shizuwall.vpn

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.arslan.shizuwall.R
import com.arslan.shizuwall.firewall.FirewallTargets
import com.arslan.shizuwall.firewall.PerUidFirewall
import com.arslan.shizuwall.ui.MainActivity
import com.arslan.shizuwall.utils.AppKey
import com.arslan.shizuwall.utils.FirewallUtils

class FirewallVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var network = true to true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(net: Network, caps: NetworkCapabilities) {
            val next = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) to
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            if (next == network) return
            network = next
            rebuild()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!rebuild()) onRevoke()
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }

    override fun onRevoke() {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
        synchronized(Companion) { keys?.clear() }
        FirewallUtils.saveFirewallEnabled(this, prefs, false)
        FirewallUtils.saveActivePackages(prefs, emptySet())
        close()
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        close()
        instance = null
        super.onDestroy()
    }

    @Synchronized
    private fun rebuild(): Boolean {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.215.173.1", 32)
            .addAddress("fd00:215:173::1", 128)
        val (mobile, wifi) = network
        val blocked = snapshot(this)
        val targets = FirewallTargets.vpnTargets(
            blocked, PerUidFirewall.mobileOnlyKeys(this), PerUidFirewall.wifiOnlyKeys(this), mobile, wifi
        )
        if (targets.isNotEmpty()) builder.addRoute("0.0.0.0", 0).addRoute("::", 0)
        val added = targets.ifEmpty { blocked.take(1).map { packageName } }
            .count { runCatching { builder.addAllowedApplication(it) }.isSuccess }
        val next = if (added == 0) null else runCatching { builder.establish() }.getOrNull() ?: return false
        runCatching { tun?.close() }
        tun = next
        keepAlive(next != null)
        return true
    }

    @Synchronized
    private fun close() {
        runCatching { tun?.close() }
        tun = null
        keepAlive(false)
    }

    private fun keepAlive(on: Boolean) {
        val intent = Intent(this, Keeper::class.java)
        runCatching { if (on) startService(intent) else stopService(intent) }
    }

    class Keeper : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }

    companion object {
        @Volatile
        private var instance: FirewallVpnService? = null
        private var keys: MutableSet<String>? = null

        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

        fun apply(context: Context, transform: (MutableSet<String>) -> Unit): Boolean {
            val empty = synchronized(this) { state(context).apply(transform).isEmpty() }
            instance?.let { return it.rebuild() }
            if (empty) return true
            return context.applicationContext
                .bindService(Intent(context, FirewallVpnService::class.java), connection, Context.BIND_AUTO_CREATE)
        }

        private fun snapshot(context: Context): Set<String> = synchronized(this) { state(context).toSet() }

        private fun state(context: Context): MutableSet<String> = keys ?: initial(context).also { keys = it }

        private fun initial(context: Context): MutableSet<String> {
            val prefs = context.getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)
            if (!FirewallUtils.loadFirewallEnabled(prefs)) return mutableSetOf()
            return FirewallUtils.loadActivePackages(prefs).filterTo(mutableSetOf()) { !AppKey.isSecondary(it) }
        }
    }
}

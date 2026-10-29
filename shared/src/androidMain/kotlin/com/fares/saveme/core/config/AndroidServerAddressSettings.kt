package com.fares.saveme.core.config

import android.content.Context
import android.content.Intent

/**
 * Keeps the override in plain SharedPreferences because it is read synchronously before Koin starts. It is not a secret,
 * but a restored backup must not carry a stale LAN address, so the file is excluded from backup next to the other stores.
 */
class AndroidServerAddressSettings(
    private val context: Context,
    override val default: String,
) : ServerAddressSettings {
    private val prefs get() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun current(): String = prefs.getString(KEY, null)?.takeIf { ServerAddress.parse(it) != null } ?: default

    override fun save(hostPort: String) {
        // Same as the default means no override, so a later rebuild with a new default is not shadowed.
        if (hostPort == default) reset() else prefs.edit().putString(KEY, hostPort).commit()
    }

    override fun reset() {
        prefs.edit().remove(KEY).commit()
    }

    override fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Runtime.getRuntime().exit(0)
    }

    private companion object {
        const val FILE = "server_address"
        const val KEY = "host_port"
    }
}

package com.fares.saveme.core.config

/** A backend address as the user types it: a host and an optional port, no scheme and no path. */
data class ServerAddress(val host: String, val port: Int?) {
    val hostPort: String get() = if (port == null) host else "$host:$port"

    companion object {
        private val Hostname = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$")

        /** Null when [input] is not `host[:port]`; a pasted `http://` prefix and trailing slash are tolerated. */
        fun parse(input: String): ServerAddress? {
            val trimmed = input.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
            if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains(' ')) return null
            val host = trimmed.substringBefore(':')
            if (!Hostname.matches(host)) return null
            if (!trimmed.contains(':')) return ServerAddress(host, null)
            val port = trimmed.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return ServerAddress(host, port)
        }
    }
}

/**
 * The backend address the app talks to. The build bakes a [default]; the user may override it when the backend moves
 * (a LAN address changes), which takes effect after a restart because the HTTP client and link parsers are built at start.
 */
interface ServerAddressSettings {
    val default: String
    fun current(): String
    fun save(hostPort: String)
    fun reset()
    fun restartApp()
}

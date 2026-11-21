package com.fares.saveme.core.config

enum class Environment { Dev, Staging, Prod }

/** Per-environment values supplied by the host app at startup (research R-11). */
data class AppConfig(
    val baseUrl: String,
    val linkHost: String,
    val environment: Environment,
    val isDebugTools: Boolean,
    /** Port of mailed links; null when the backend is reached on the scheme's default port (staging). */
    val linkPort: Int? = null,
    /** Scheme of the API and of mailed links: `http` for the LAN backends (dev, prod), `https` for the homelab staging stack. */
    val linkScheme: String = "https",
    /** Whether the server address can be changed in the app (Android only; the backend host may move on a LAN). */
    val serverAddressEditable: Boolean = false,
    /** The installed package's id, for Google Play's subscription centre link (spec 018 R-8); empty where there is none (iOS, tests). */
    val applicationId: String = "",
    /**
     * Build-time switch for the in-app Subscribe action (spec 018 US6). Off in every flavour: constitution P-12.3 allows only
     * "Restore purchases" and "Enter code" until the owner amends it, and nothing in the shipped code reads this switch yet.
     */
    val inAppSubscribe: Boolean = false,
)

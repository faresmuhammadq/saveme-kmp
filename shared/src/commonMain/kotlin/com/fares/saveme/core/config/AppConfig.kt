package com.fares.saveme.core.config

enum class Environment { Dev, Staging, Prod }

/** Per-environment values supplied by the host app at startup (research R-11). */
data class AppConfig(
    val baseUrl: String,
    val linkHost: String,
    val environment: Environment,
    val isDebugTools: Boolean,
    /** Port of mailed links; set only in dev, where the backend is reached on a non-default port. */
    val linkPort: Int? = null,
)

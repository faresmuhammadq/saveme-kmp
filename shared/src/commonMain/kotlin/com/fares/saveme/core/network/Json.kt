package com.fares.saveme.core.network

import kotlinx.serialization.json.Json

/** Unknown response members are ignored (P-02.3); nulls and defaults are never encoded (P-02.2). */
val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

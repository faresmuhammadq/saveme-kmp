package com.fares.saveme.feature.notifications.data

import com.fares.saveme.core.storage.KeyValueStore

/** Two per-installation flags about the phone, not the person: never cleared at sign-out (data-model §3). */
class PromptFlags(private val store: KeyValueStore) {
    suspend fun isOffered(): Boolean = store.get(OFFERED) == "true"
    suspend fun markOffered() = store.set(OFFERED, "true")

    suspend fun permissionAsked(): Boolean = store.get(ASKED) == "true"
    suspend fun markPermissionAsked() = store.set(ASKED, "true")

    companion object {
        const val OFFERED = "push_prompt_offered"
        const val ASKED = "push_permission_asked"
    }
}

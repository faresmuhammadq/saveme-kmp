package com.fares.saveme.core.locale

import com.fares.saveme.core.platform.ClientInfo
import com.fares.saveme.core.session.UserDataCleaner
import com.fares.saveme.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's display language. Signed in, it follows the profile language; signed out (no
 * override) it follows the device (spec 002 FR-016, research R-06).
 */
class AppLanguageStore(
    private val store: KeyValueStore,
    private val clientInfo: ClientInfo,
) : UserDataCleaner {
    private val override = MutableStateFlow<AppLanguage?>(null)

    /** The override only; null means "follow the device". */
    val language: StateFlow<AppLanguage?> = override.asStateFlow()

    suspend fun load() {
        override.value = AppLanguage.fromTag(store.get(KEY))
    }

    fun effective(): AppLanguage = override.value ?: device()

    fun device(): AppLanguage = AppLanguage.fromTag(clientInfo.languageTag()) ?: AppLanguage.En

    suspend fun set(language: AppLanguage) {
        store.set(KEY, language.tag)
        override.value = language
    }

    override suspend fun clear() {
        store.remove(KEY)
        override.value = null
    }

    private companion object {
        const val KEY = "app_language"
    }
}

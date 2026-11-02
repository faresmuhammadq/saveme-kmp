package com.fares.saveme.feature.auth.domain

import com.fares.saveme.core.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Whether the person closed Home's "set a monthly budget" suggestion; kept per account on this phone, no personal data. */
class HomeSuggestionStore(private val store: KeyValueStore, private val sessions: StateFlow<AuthState>) {
    private val flags = HashMap<String, MutableStateFlow<Boolean>>()

    private fun accountId(): String? = (sessions.value as? AuthState.SignedIn)?.account?.userId

    /** True (hidden) while signed out or until the stored value is read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<Boolean> = sessions.map { (it as? AuthState.SignedIn)?.account?.userId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) {
            flowOf(true)
        } else {
            flow {
                emit(true)
                val flag = flags.getOrPut(id) { MutableStateFlow(store.get(key(id)) == "1") }
                emitAll(flag)
            }
        }
    }

    suspend fun dismiss() {
        val id = accountId() ?: return
        store.set(key(id), "1")
        flags.getOrPut(id) { MutableStateFlow(true) }.value = true
    }

    private fun key(id: String) = "home.suggestion.dismissed.$id"
}

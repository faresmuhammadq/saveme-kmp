package com.fares.saveme.core.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * The store every screen extends (constitution P-16): one immutable [state], one stream of
 * one-shot [effects], and [onIntent] as the only way in.
 */
abstract class MviViewModel<S : Any, I : Any, E : Any>(initialState: S) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    protected val currentState: S get() = mutableState.value

    abstract fun onIntent(intent: I)

    /** Pass the screen's pure reducer: `setState { reduce(it, change) }`. */
    protected fun setState(transform: (S) -> S) {
        mutableState.update(transform)
    }

    protected fun sendEffect(effect: E) {
        effectChannel.trySend(effect)
    }
}

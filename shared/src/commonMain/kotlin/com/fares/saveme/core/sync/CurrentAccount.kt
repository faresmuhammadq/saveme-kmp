package com.fares.saveme.core.sync

/**
 * Who is signed in, as the sync engine needs it: a change is tagged with its author and only ever
 * sent for that account (FR-018). Implemented by the auth feature; core can't read the session
 * itself (P-10.7).
 */
fun interface CurrentAccount {
    /** The signed-in user's id, or null when signed out. */
    fun userId(): String?

    /**
     * Reads the saved session if nothing has yet. A background drain starts in a fresh process,
     * where no screen has restored it, and would otherwise see nobody signed in.
     */
    suspend fun load() {}
}

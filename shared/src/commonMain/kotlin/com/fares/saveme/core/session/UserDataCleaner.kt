package com.fares.saveme.core.session

/**
 * Wipes one kind of per-person local data at sign-out, so nothing survives into the next
 * person's session on a shared phone (P-09). Features register their own; core never knows them.
 */
fun interface UserDataCleaner {
    suspend fun clear()
}

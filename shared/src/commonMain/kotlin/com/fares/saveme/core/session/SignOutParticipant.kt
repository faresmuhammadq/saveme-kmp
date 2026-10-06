package com.fares.saveme.core.session

/**
 * Work a feature needs done while the stored session is still valid, before the server sign-out and the local wipe
 * (spec 017 R-07: removing this phone's push registration). Best effort and bounded by the sign-out; core never knows
 * the features (P-10.7). A remote session end runs only [UserDataCleaner]s, never participants.
 */
fun interface SignOutParticipant {
    suspend fun beforeSignOut()
}

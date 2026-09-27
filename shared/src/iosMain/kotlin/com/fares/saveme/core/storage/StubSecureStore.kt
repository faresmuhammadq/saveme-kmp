package com.fares.saveme.core.storage

/** iOS never signs in yet (P-15), so there is never a credential to hold. */
class StubSecureStore : SecureStore {
    override suspend fun read(): StoredSession? = null

    override suspend fun write(session: StoredSession) {
        throw UnsupportedOperationException("iOS sign-in out of scope (P-15)")
    }

    override suspend fun clear() = Unit
}

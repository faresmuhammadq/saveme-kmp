package com.fares.saveme.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.time.Instant

/**
 * Encrypts the session with an AES-256-GCM key that never leaves the Android Keystore, and keeps
 * only IV + ciphertext in a backup-excluded DataStore file (research R-06).
 */
class KeystoreSecureStore(private val dataStore: DataStore<Preferences>) : SecureStore {

    override suspend fun read(): StoredSession? {
        val encoded = dataStore.data.first()[SESSION] ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            val plain = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
            Json.decodeFromString(Payload.serializer(), plain).toSession()
        }.getOrElse {
            // Key lost or data corrupted: the credential is unrecoverable, so start signed out.
            clear()
            null
        }
    }

    override suspend fun write(session: StoredSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = Json.encodeToString(Payload.serializer(), Payload.from(session)).encodeToByteArray()
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
        dataStore.edit { it[SESSION] = encoded }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(SESSION) }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @Serializable
    private data class Payload(val credential: String, val sessionId: String, val issuedAtEpochMs: Long) {
        fun toSession() = StoredSession(credential, sessionId, Instant.fromEpochMilliseconds(issuedAtEpochMs))

        companion object {
            fun from(s: StoredSession) = Payload(s.credential, s.sessionId, s.issuedAt.toEpochMilliseconds())
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        val SESSION = stringPreferencesKey("session")
    }
}

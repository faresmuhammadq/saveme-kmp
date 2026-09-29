package com.fares.saveme.core.locale

import com.fares.saveme.core.network.FakeClientInfo
import com.fares.saveme.feature.auth.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguageStoreTest {

    @Test
    fun withoutOverrideTheDeviceLanguageIsUsed() = runTest {
        val store = AppLanguageStore(InMemoryKeyValueStore(), FakeClientInfo(language = "en"))
        store.load()
        assertNull(store.language.value)
        assertEquals(AppLanguage.En, store.effective())
    }

    @Test
    fun overrideWinsAndIsPersisted() = runTest {
        val values = InMemoryKeyValueStore()
        AppLanguageStore(values, FakeClientInfo(language = "en")).set(AppLanguage.Ar)
        assertEquals("ar", values.values["app_language"])

        val reopened = AppLanguageStore(values, FakeClientInfo(language = "en"))
        reopened.load()
        assertEquals(AppLanguage.Ar, reopened.effective())
    }

    @Test
    fun clearReturnsToTheDeviceLanguage() = runTest {
        val values = InMemoryKeyValueStore()
        val store = AppLanguageStore(values, FakeClientInfo(language = "ar"))
        store.set(AppLanguage.En)
        store.clear()
        assertNull(values.values["app_language"])
        assertEquals(AppLanguage.Ar, store.effective())
    }
}

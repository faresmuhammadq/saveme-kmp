package com.fares.saveme.feature.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.NotificationDisplay
import com.fares.saveme.feature.notifications.domain.PostedNotification
import com.fares.saveme.feature.notifications.ui.ChannelsEffect
import com.fares.saveme.feature.notifications.ui.resolveChannelLabels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Channel names, descriptions and the lock-screen text come from the string resources in the app language (R-08, FR-022, FR-041). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelLabelsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun englishLabels() = runBlocking {
        val labels = resolveChannelLabels()
        assertEquals(6, labels.channels.size)
        assertEquals("Recurring items due", labels.channels.getValue(NotificationCategory.RecurringDue).name)
        assertEquals("Budget alerts", labels.channels.getValue(NotificationCategory.BudgetExceeded).name)
        assertEquals("An export you asked for is ready.", labels.channels.getValue(NotificationCategory.ExportReady).description)
        assertEquals("New notification", labels.publicText)
    }

    @Test
    @Config(qualifiers = "+ar-rEG")
    fun arabicLabels() = runBlocking {
        val labels = resolveChannelLabels()
        assertEquals("عناصر متكررة مستحقة", labels.channels.getValue(NotificationCategory.RecurringDue).name)
        assertEquals("حذف الحساب", labels.channels.getValue(NotificationCategory.DeletionReminder).name)
        assertEquals("إشعار جديد", labels.publicText)
        assertTrue(labels.channels.values.all { it.description.isNotBlank() })
    }

    private class RecordingDisplay : NotificationDisplay {
        val calls = java.util.concurrent.CopyOnWriteArrayList<ChannelLabels>()
        override suspend fun ensureChannels(labels: ChannelLabels) { calls += labels }
        override fun post(notification: PostedNotification) = false
        override fun cancelAll() = Unit
    }

    @Test
    fun theChannelsEffectRunsAtStartAndAgainWhenTheLanguageChanges() {
        val display = RecordingDisplay()
        val provider = FakeProvider()
        var language by mutableStateOf(AppLanguage.En)
        compose.setContent { ChannelsEffect(language, provider = provider, display = display) }
        compose.waitUntil(5_000) { display.calls.size == 1 }
        compose.runOnIdle { language = AppLanguage.Ar }
        compose.waitForIdle()
        Thread.sleep(500)
        compose.waitForIdle()
        compose.waitUntil(5_000) { display.calls.size >= 2 }
        assertTrue(display.calls.size >= 2)
    }

    @Test
    fun aBuildWithoutAProviderCreatesNoChannels() {
        val display = RecordingDisplay()
        compose.setContent { ChannelsEffect(AppLanguage.En, provider = FakeProvider(isAvailable = false), display = display) }
        compose.waitForIdle()
        Thread.sleep(300)
        compose.waitForIdle()
        assertTrue(display.calls.isEmpty())
    }
}

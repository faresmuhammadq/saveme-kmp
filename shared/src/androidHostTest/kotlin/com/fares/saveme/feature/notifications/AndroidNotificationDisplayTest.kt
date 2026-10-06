package com.fares.saveme.feature.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.fares.saveme.feature.notifications.domain.ChannelLabel
import com.fares.saveme.feature.notifications.domain.ChannelLabels
import com.fares.saveme.feature.notifications.domain.NotificationCategory
import com.fares.saveme.feature.notifications.domain.PostedNotification
import com.fares.saveme.feature.notifications.domain.PushTapKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Channels, posting and clearing through the system notification service (spec 017 R-08, R-09, FR-022, FR-023). */
@RunWith(RobolectricTestRunner::class)
class AndroidNotificationDisplayTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val display = AndroidNotificationDisplay(context, android.R.drawable.stat_notify_more, AndroidNotificationPermissionState(context))

    private fun labels(prefix: String) = ChannelLabels(
        NotificationCategory.entries.associateWith { ChannelLabel("$prefix ${it.wire}", "about ${it.wire}") },
        publicText = "$prefix public",
    )

    @Before
    fun launcherActivity() {
        val component = ComponentName(context.packageName, "com.fares.saveme.TestMainActivity")
        val info = ActivityInfo().apply { packageName = context.packageName; name = component.className; exported = true }
        shadowOf(context.packageManager).addOrUpdateActivity(info)
        shadowOf(context.packageManager).addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun note(tag: String = "budget:b1", channel: String = "budget_exceeded") = PostedNotification(
        channel, tag, "Budget alert", "A budget went over its limit.",
        mapOf("schema" to "1", "type" to "budget_exceeded", "category" to channel, "notification_id" to "n-1", "budget_id" to "b1"),
    )

    @Test
    fun sixChannelsWithTheCategoryIdsDefaultImportanceAndPrivateLockScreen() = runBlocking {
        display.ensureChannels(labels("EN"))
        val channels = manager.notificationChannels
        assertEquals(NotificationCategory.entries.map { it.wire }.toSet(), channels.map { it.id }.toSet())
        channels.forEach {
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, it.importance)
            assertEquals(Notification.VISIBILITY_PRIVATE, it.lockscreenVisibility)
            assertEquals("EN ${it.id}", it.name.toString())
            assertEquals("about ${it.id}", it.description)
        }
    }

    @Test
    fun aSecondCallRenamesWithoutDeleting() = runBlocking {
        display.ensureChannels(labels("EN"))
        display.ensureChannels(labels("AR"))
        val channels = manager.notificationChannels
        assertEquals(6, channels.size)
        assertTrue(channels.all { it.name.toString().startsWith("AR ") })
    }

    @Test
    fun postSetsChannelTagAutoCancelPublicVersionAndTheTapExtrasExactly() = runBlocking {
        display.ensureChannels(labels("EN"))
        assertTrue(display.post(note()))
        val posted = shadowOf(manager).getNotification("budget:b1", 0)
        assertNotNull(posted)
        assertEquals("budget_exceeded", posted.channelId)
        assertEquals("Budget alert", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("A budget went over its limit.", posted.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals("EN public", posted.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        val intent = shadowOf(posted.contentIntent).savedIntent
        val extraKeys = intent.extras!!.keySet()
        assertEquals(setOf("schema", "type", "category", "notification_id", "budget_id"), extraKeys)
        assertTrue(extraKeys.all { it in PushTapKeys.all })
        assertEquals("b1", intent.getStringExtra("budget_id"))
    }

    @Test
    fun theSameTagReplacesAndADifferentOneAdds() = runBlocking {
        display.ensureChannels(labels("EN"))
        display.post(note("budget:b1"))
        display.post(note("budget:b1"))
        assertEquals(1, shadowOf(manager).size())
        display.post(note("budget:b2"))
        assertEquals(2, shadowOf(manager).size())
    }

    @Test
    fun nothingIsPostedWhereTheRuntimePermissionIsDenied() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        display.ensureChannels(labels("EN"))
        assertFalse(display.post(note()))
        assertEquals(0, shadowOf(manager).size())
    }

    @Test
    fun cancelAllClearsEverythingTheAppPosted() = runBlocking {
        display.ensureChannels(labels("EN"))
        display.post(note("a"))
        display.post(note("b"))
        display.cancelAll()
        assertEquals(0, shadowOf(manager).size())
    }

    @Test
    fun channelsAreNeverDeletedByTheApp() {
        val source = java.io.File("src/androidMain/kotlin/com/fares/saveme/feature/notifications/AndroidNotificationDisplay.kt").readText()
        assertFalse(source.contains("deleteNotificationChannel"))
    }
}

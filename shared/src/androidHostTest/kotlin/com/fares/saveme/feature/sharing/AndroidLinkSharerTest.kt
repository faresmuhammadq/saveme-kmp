package com.fares.saveme.feature.sharing

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The share sheet and the clipboard copy of a link (spec 013 FR-005, SC-005 US3-AS1, US3-AS5, R-17). */
@RunWith(RobolectricTestRunner::class)
class AndroidLinkSharerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val text = "Save Me: join my wallet. https://app.example/invite?token=Fake01TokenForTestsOnlyxxxxxxxxxxxxxxxxxxxx"

    @Test
    fun theCopyIsMarkedSensitiveSoTheSystemDoesNotPreviewIt() {
        AndroidLinkSharer(context).copy(text)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip!!
        assertEquals(text, clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test
    fun theShareOpensTheSystemChooserWithThePlainText() {
        AndroidLinkSharer(context).share(text)
        val chooser = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(text, send.getStringExtra(Intent.EXTRA_TEXT))
    }
}

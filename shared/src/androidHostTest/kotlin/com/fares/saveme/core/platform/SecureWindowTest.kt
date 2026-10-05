package com.fares.saveme.core.platform

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SecureWindowTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun secure() = (compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    @Test
    fun theFlagIsSetWhileComposedAndClearedOnDispose() {
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) SecureWindow() }
        compose.waitForIdle()
        assertTrue(secure())

        shown = false
        compose.waitForIdle()
        assertFalse(secure())
    }

    @Test
    fun aFlagThatWasAlreadySetStaysSet() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) SecureWindow() }
        compose.waitForIdle()
        shown = false
        compose.waitForIdle()
        assertTrue(secure())
    }
}

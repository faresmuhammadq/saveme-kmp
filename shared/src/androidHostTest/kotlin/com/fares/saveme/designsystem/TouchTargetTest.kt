package com.fares.saveme.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.fares.saveme.designsystem.catalog.BarsSection
import com.fares.saveme.designsystem.catalog.ButtonsSection
import com.fares.saveme.designsystem.catalog.FieldsSection
import com.fares.saveme.designsystem.catalog.SelectionSection
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P-08.1: every clickable component offers at least a 48×48dp touch target. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class TouchTargetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyClickableNodeHasA48dpTouchTarget() {
        compose.setContent {
            AppTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ButtonsSection()
                    BarsSection()
                    SelectionSection()
                    FieldsSection()
                }
            }
        }
        val minPx = with(compose.density) { 48f * density }
        val nodes = compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected clickable nodes", nodes.size > 10)
        nodes.forEach { node ->
            val bounds = node.touchBoundsInRoot
            assertTrue("touch width ${bounds.width} < $minPx for ${node.config}", bounds.width >= minPx - 0.5f)
            assertTrue("touch height ${bounds.height} < $minPx for ${node.config}", bounds.height >= minPx - 0.5f)
        }
    }

    @Test
    fun iconButtonExposesItsDescription() {
        compose.setContent { AppTheme { AppIconButton(AppIcons.ArrowBack, "Back", {}) } }
        compose.onNodeWithContentDescription("Back").assertExists()
    }

    @Test
    fun disabledAndLoadingButtonsIgnoreClicks() {
        var clicks = 0
        compose.setContent {
            AppTheme {
                Column {
                    PrimaryButton("Disabled", { clicks++ }, Modifier.testTag("disabled"), enabled = false)
                    PrimaryButton("Loading", { clicks++ }, Modifier.testTag("loading"), loading = true)
                }
            }
        }
        compose.onNodeWithTag("disabled").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("loading").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }
}

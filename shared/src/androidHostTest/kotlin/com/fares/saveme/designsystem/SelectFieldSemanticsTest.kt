package com.fares.saveme.designsystem

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.fares.saveme.designsystem.component.AppSelectField
import com.fares.saveme.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P-08: the select field's click overlay covers the text field, so accessibility services see only
 * the overlay. It must announce the label and the chosen value itself (the 006 E2E run found the
 * wallet editor's currency read as an unlabelled button with no value).
 */
@RunWith(RobolectricTestRunner::class)
class SelectFieldSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theOverlayButtonCarriesTheLabelAndTheValue() {
        compose.setContent { AppTheme { AppSelectField(label = "Currency", value = "EGP", onClick = {}) } }
        compose.onNode(
            hasContentDescription("Currency") and hasText("EGP") and hasClickAction() and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).assertIsDisplayed()
    }
}

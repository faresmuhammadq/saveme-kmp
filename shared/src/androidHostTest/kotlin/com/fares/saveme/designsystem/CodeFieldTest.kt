package com.fares.saveme.designsystem

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import com.fares.saveme.designsystem.component.CodeField
import com.fares.saveme.designsystem.component.GroupedCodeTransformation
import com.fares.saveme.designsystem.component.isAllowedInCodeMenu
import com.fares.saveme.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CodeFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private val transformation = GroupedCodeTransformation(4)

    @Test
    fun theVisualHyphensAreDrawnBetweenGroupsOnly() {
        assertEquals("7KQ2", transformation.filter(AnnotatedString("7KQ2")).text.text)
        assertEquals("7KQ2-M", transformation.filter(AnnotatedString("7KQ2M")).text.text)
        assertEquals("7KQ2-MX4D-9TRB-H3WE", transformation.filter(AnnotatedString("7KQ2MX4D9TRBH3WE")).text.text)
    }

    @Test
    fun theOffsetMappingKeepsTheCursorRightTypingAndDeleting() {
        val full = transformation.filter(AnnotatedString("7KQ2MX4D9TRBH3WE")).offsetMapping
        // After the fifth typed character the cursor is after the M, past the first hyphen.
        assertEquals(0, full.originalToTransformed(0))
        assertEquals(4, full.originalToTransformed(4))
        assertEquals(6, full.originalToTransformed(5))
        assertEquals(9, full.originalToTransformed(8))
        assertEquals(19, full.originalToTransformed(16))
        // And back: the cursor right after a hyphen is before the next character.
        for (original in 0..16) {
            assertEquals(original, full.transformedToOriginal(full.originalToTransformed(original)), "round trip $original")
        }
        assertEquals(4, full.transformedToOriginal(5))
        assertEquals(16, full.transformedToOriginal(19))
        val partial = transformation.filter(AnnotatedString("7KQ2M")).offsetMapping
        assertEquals(5, partial.transformedToOriginal(99), "never past the text")
    }

    @Test
    fun theMenuKeepsPasteAndDropsCopyAndCut() {
        assertTrue(isAllowedInCodeMenu(TextContextMenuKeys.PasteKey))
        assertTrue(isAllowedInCodeMenu(TextContextMenuKeys.SelectAllKey))
        assertFalse(isAllowedInCodeMenu(TextContextMenuKeys.CopyKey))
        assertFalse(isAllowedInCodeMenu(TextContextMenuKeys.CutKey))
        assertTrue(isAllowedInCodeMenu(TextContextMenuSeparator))
    }

    @Test
    fun theLabelIsAnnouncedAndTheCodeIsReadCharacterByCharacter() {
        compose.setContent { AppTheme { CodeField("7KQ2MX", {}, "Redeem code") } }
        compose.onNodeWithContentDescription("Redeem code")
            .assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "7 K Q 2 M X"))
        compose.onNode(hasContentDescription("Redeem code")).assertExists()
    }

    @Test
    fun typedTextReachesTheCallerRaw() {
        var value by mutableStateOf("")
        compose.setContent { AppTheme { CodeField(value, { value = it }, "Redeem code") } }
        compose.onNodeWithContentDescription("Redeem code").performTextInput("7kq2")
        assertEquals("7kq2", value, "the caller normalises")
    }
}

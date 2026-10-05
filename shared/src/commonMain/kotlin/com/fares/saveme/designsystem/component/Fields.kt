package com.fares.saveme.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_hide_password
import saveme.shared.generated.resources.cd_show_password

/** 52dp field; [errorText] replaces [supportingText] and is announced as an error. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = AppTheme.type.body,
    minLines: Int = 1,
) {
    val colors = AppTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = AppTheme.type.label) },
        supportingText = (errorText ?: supportingText)?.let { text -> { Text(text, style = AppTheme.type.caption) } },
        isError = errorText != null,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        minLines = minLines,
        shape = AppTheme.shapes.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            errorBorderColor = colors.error,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.ink2,
            errorLabelColor = colors.error,
            errorSupportingTextColor = colors.error,
            unfocusedSupportingTextColor = colors.caption,
            focusedSupportingTextColor = colors.caption,
            cursorColor = colors.primary,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorContainerColor = colors.surface,
            disabledContainerColor = colors.sunken,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.field)
            .semantics { if (errorText != null) error(errorText) },
    )
}

/** Masked by default; the toggle is a labelled 48dp icon button. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        supportingText = supportingText,
        errorText = errorText,
        enabled = enabled,
        keyboardOptions = keyboardOptions.copy(keyboardType = KeyboardType.Password),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            AppIconButton(
                icon = if (visible) AppIcons.VisibilityOff else AppIcons.Visibility,
                contentDescription = stringResource(if (visible) Res.string.cd_hide_password else Res.string.cd_show_password),
                onClick = onToggleVisible,
                tint = AppTheme.colors.ink2,
            )
        },
    )
}

/**
 * A field for a short mailed code shown in groups (spec 015 FR-004, FR-029). [value] is the raw characters
 * without separators; the hyphens between groups are drawn, never part of the text, so deleting behaves.
 * The text is always left-to-right (a code is Latin letters and digits, also inside Arabic); the label and the
 * messages follow the app direction. The keyboard is asked not to learn or suggest (password variation, no
 * masking), and the context menu offers paste, not copy or cut. Pasted text arrives through [onValueChange]
 * like typed text: the caller normalises both.
 */
@Composable
fun CodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    groupSize: Int = 4,
    onDone: () -> Unit = {},
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        supportingText = supportingText,
        errorText = errorText,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = remember(groupSize) { GroupedCodeTransformation(groupSize) },
        textStyle = AppTheme.type.numeralL.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Start),
        modifier = modifier
            .filterTextContextMenuComponents { isAllowedInCodeMenu(it) }
            .semantics {
                contentDescription = label
                // Read as separate characters, not as a word.
                stateDescription = value.toList().joinToString(" ")
            },
    )
}

/** Paste (and select all) only: nothing in the menu puts the code on the clipboard (P-09.2). */
internal fun isAllowedInCodeMenu(component: TextContextMenuComponent): Boolean = isAllowedInCodeMenu(component.key)

internal fun isAllowedInCodeMenu(key: Any): Boolean = key != TextContextMenuKeys.CopyKey && key != TextContextMenuKeys.CutKey

/** Draws a hyphen between groups with an exact offset mapping, so the cursor stays right typing and deleting. */
internal class GroupedCodeTransformation(private val groupSize: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = raw.chunked(groupSize).joinToString("-")
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 0) 0 else offset + (offset - 1) / groupSize

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / (groupSize + 1)).coerceIn(0, raw.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}

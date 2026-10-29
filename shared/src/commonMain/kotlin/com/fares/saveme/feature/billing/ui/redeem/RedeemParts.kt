package com.fares.saveme.feature.billing.ui.redeem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.isAllowedInCodeMenu
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.authb_redeem_count

private const val GroupSize = 4
private const val Groups = 4

/**
 * The mailed code as four 4-character boxes under a label, with a running count and the error below. One real text field sits
 * behind the boxes, so typing, pasting and the keyboard behave as for [com.fares.saveme.designsystem.component.CodeField];
 * the text is always left-to-right, the label and messages follow the app direction.
 */
@Composable
internal fun SegmentedCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val active = (value.length / GroupSize).coerceAtMost(Groups - 1)
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = colors.ink2,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                textStyle = AppTheme.type.body.copy(textDirection = TextDirection.Ltr),
                cursorBrush = SolidColor(colors.primary),
                modifier = modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
                    .filterTextContextMenuComponents { isAllowedInCodeMenu(it) }
                    .semantics {
                        contentDescription = label
                        stateDescription = value.toList().joinToString(" ")
                        if (errorText != null) error(errorText)
                    },
                decorationBox = { inner ->
                    Box {
                        Box(Modifier.size(1.dp).alpha(0f)) { inner() }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    focus.requestFocus()
                                    keyboard?.show()
                                },
                        ) {
                            repeat(Groups) { group ->
                                val on = focused && group == active
                                val shape = RoundedCornerShape(14.dp)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .background(colors.surface, shape)
                                        .border(
                                            if (on || errorText != null) 2.dp else 1.5.dp,
                                            when {
                                                errorText != null -> colors.error
                                                on -> colors.primary
                                                else -> colors.outlineVariant
                                            },
                                            shape,
                                        ),
                                ) {
                                    Text(
                                        value.drop(group * GroupSize).take(GroupSize),
                                        style = AppTheme.type.body.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            textAlign = TextAlign.Center,
                                        ),
                                        color = colors.ink,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
        Text(
            errorText ?: stringResource(Res.string.authb_redeem_count, value.length),
            style = AppTheme.type.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = if (errorText != null) colors.error else colors.ink2,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

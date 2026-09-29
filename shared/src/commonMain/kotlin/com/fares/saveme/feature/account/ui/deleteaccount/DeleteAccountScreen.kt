package com.fares.saveme.feature.account.ui.deleteaccount

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.PasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.format.formatDate
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.BackBar
import com.fares.saveme.feature.account.ui.text
import com.fares.saveme.feature.auth.ui.FieldProblem
import com.fares.saveme.feature.auth.ui.text
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.delete_confirm
import saveme.shared.generated.resources.delete_continue
import saveme.shared.generated.resources.delete_explain_body
import saveme.shared.generated.resources.delete_password
import saveme.shared.generated.resources.delete_scheduled_body
import saveme.shared.generated.resources.delete_title
import saveme.shared.generated.resources.error_account_locked
import saveme.shared.generated.resources.error_password_incorrect
import saveme.shared.generated.resources.field_error_required

@Composable
fun DeleteAccountScreen(state: DeleteAccountState, onIntent: (DeleteAccountIntent) -> Unit) {
    AppScreen(topBar = { BackBar(stringResource(Res.string.delete_title)) { onIntent(DeleteAccountIntent.Back) } }) {
        when (val step = state.step) {
            DeleteStep.Explain -> {
                // Explained before the password is asked for (FR-017).
                Text(stringResource(Res.string.delete_explain_body), style = AppTheme.type.body, color = AppTheme.colors.ink)
                DestructiveButton(stringResource(Res.string.delete_continue), { onIntent(DeleteAccountIntent.Continue) })
            }
            DeleteStep.Confirm -> {
                state.waitMinutes?.let { InlineAlert(AlertTone.Warning, pluralStringResource(Res.plurals.error_account_locked, it, it)) }
                state.banner?.let { banner ->
                    InlineAlert(
                        AlertTone.Error,
                        when (banner) {
                            DeleteBanner.Offline -> AccountBanner.Offline.text()
                            is DeleteBanner.Error -> AccountBanner.Error(banner.code, banner.requestId).text()
                        },
                    )
                }
                Text(stringResource(Res.string.delete_explain_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
                PasswordField(
                    value = state.password,
                    onValueChange = { onIntent(DeleteAccountIntent.PasswordChanged(it)) },
                    label = stringResource(Res.string.delete_password),
                    visible = state.passwordVisible,
                    onToggleVisible = { onIntent(DeleteAccountIntent.TogglePasswordVisible) },
                    errorText = when (val error = state.passwordError) {
                        DeletePasswordProblem.Required -> stringResource(Res.string.field_error_required)
                        DeletePasswordProblem.Incorrect -> stringResource(Res.string.error_password_incorrect)
                        is DeletePasswordProblem.Server -> FieldProblem.Server(error.code).text()
                        null -> null
                    },
                    enabled = !state.submitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onIntent(DeleteAccountIntent.Confirm) }),
                )
                DestructiveButton(
                    stringResource(Res.string.delete_confirm),
                    { onIntent(DeleteAccountIntent.Confirm) },
                    modifier = Modifier.testTag("deleteAccount.confirm"),
                    enabled = state.waitMinutes == null,
                    loading = state.submitting,
                )
            }
            is DeleteStep.Scheduled -> {
                InlineAlert(
                    AlertTone.Warning,
                    stringResource(Res.string.delete_scheduled_body, formatDate(step.date, TimeZone.currentSystemDefault().id)),
                )
                PrimaryButton(stringResource(Res.string.action_done), { onIntent(DeleteAccountIntent.Done) })
            }
        }
    }
}

@Preview
@Composable
private fun DeleteAccountScreenPreview() {
    AppTheme { DeleteAccountScreen(DeleteAccountState(step = DeleteStep.Confirm, passwordError = DeletePasswordProblem.Incorrect)) {} }
}

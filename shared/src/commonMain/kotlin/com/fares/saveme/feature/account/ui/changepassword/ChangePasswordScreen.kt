package com.fares.saveme.feature.account.ui.changepassword

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.CalmActionBar
import com.fares.saveme.designsystem.component.CalmCard
import com.fares.saveme.designsystem.component.CalmCheckBadge
import com.fares.saveme.designsystem.component.CalmScreen
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.CalmPasswordField
import com.fares.saveme.designsystem.component.PrimaryButton
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.feature.account.ui.AccountBanner
import com.fares.saveme.feature.account.ui.text
import com.fares.saveme.feature.auth.ui.text
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.acct_pw_rule_email
import saveme.shared.generated.resources.acct_pw_rule_length
import saveme.shared.generated.resources.acct_pw_signs_out_others
import saveme.shared.generated.resources.action_done
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.change_password_current
import saveme.shared.generated.resources.change_password_done
import saveme.shared.generated.resources.change_password_new
import saveme.shared.generated.resources.change_password_submit
import saveme.shared.generated.resources.change_password_title
import saveme.shared.generated.resources.error_account_locked
import saveme.shared.generated.resources.error_current_password_incorrect
import saveme.shared.generated.resources.error_rate_limited
import saveme.shared.generated.resources.error_rate_limited_soon
import saveme.shared.generated.resources.error_validation

@Composable
fun ChangePasswordScreen(state: ChangePasswordState, onIntent: (ChangePasswordIntent) -> Unit) {
    CalmScreen(
        title = stringResource(Res.string.change_password_title),
        onBack = { onIntent(ChangePasswordIntent.Back) },
        backDescription = stringResource(Res.string.cd_back),
        bottomBar = {
            CalmActionBar {
                if (state.done) {
                    PrimaryButton(stringResource(Res.string.action_done), { onIntent(ChangePasswordIntent.Back) })
                } else {
                    PrimaryButton(
                        stringResource(Res.string.change_password_submit),
                        { onIntent(ChangePasswordIntent.Submit) },
                        modifier = Modifier.testTag("changePassword.submit"),
                        enabled = state.waitMinutes == null,
                        loading = state.submitting,
                    )
                }
            }
        },
    ) {
        if (state.done) {
            InlineAlert(tone = AlertTone.Info, text = stringResource(Res.string.change_password_done))
            return@CalmScreen
        }
        state.waitReason?.let { reason ->
            val minutes = state.waitMinutes
            InlineAlert(
                tone = AlertTone.Warning,
                text = when {
                    minutes == null -> stringResource(Res.string.error_rate_limited_soon)
                    reason == WaitReason.Locked -> pluralStringResource(Res.plurals.error_account_locked, minutes, minutes)
                    else -> pluralStringResource(Res.plurals.error_rate_limited, minutes, minutes)
                },
            )
        }
        state.banner?.let { banner ->
            InlineAlert(
                tone = AlertTone.Error,
                text = when (banner) {
                    ChangePasswordBanner.Offline -> AccountBanner.Offline.text()
                    ChangePasswordBanner.Validation -> stringResource(Res.string.error_validation)
                    is ChangePasswordBanner.Error -> AccountBanner.Error(banner.code, banner.requestId).text()
                },
            )
        }
        CalmPasswordField(
            value = state.current,
            onValueChange = { onIntent(ChangePasswordIntent.CurrentChanged(it)) },
            label = stringResource(Res.string.change_password_current),
            modifier = Modifier.testTag("changePassword.current"),
            visible = state.currentVisible,
            onToggleVisible = { onIntent(ChangePasswordIntent.ToggleCurrentVisible) },
            errorText = when (val error = state.currentError) {
                CurrentPasswordProblem.Incorrect -> stringResource(Res.string.error_current_password_incorrect)
                is CurrentPasswordProblem.Field -> error.problem.text()
                null -> null
            },
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        CalmPasswordField(
            value = state.new,
            onValueChange = { onIntent(ChangePasswordIntent.NewChanged(it)) },
            label = stringResource(Res.string.change_password_new),
            modifier = Modifier.testTag("changePassword.new"),
            visible = state.newVisible,
            onToggleVisible = { onIntent(ChangePasswordIntent.ToggleNewVisible) },
            errorText = state.newError.text(),
            enabled = !state.submitting,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(ChangePasswordIntent.Submit) }),
        )
        CalmCard(radius = 18.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            val typed = state.new.isNotEmpty()
            RuleRow(stringResource(Res.string.acct_pw_rule_length), if (typed) state.new.length in 8..128 else null)
            RuleRow(
                stringResource(Res.string.acct_pw_rule_email),
                if (typed) !state.new.trim().equals(state.email.trim(), ignoreCase = true) else null,
            )
        }
        Text(
            stringResource(Res.string.acct_pw_signs_out_others),
            style = AppTheme.type.caption.copy(fontSize = 13.sp),
            color = AppTheme.colors.ink2,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** [met] is null until something is typed. */
@Composable
private fun RuleRow(text: String, met: Boolean?) {
    val tint = when (met) {
        true -> AppTheme.money.positive
        false -> AppTheme.money.over
        null -> AppTheme.colors.caption
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        CalmCheckBadge(tint)
        Text(text, style = AppTheme.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = tint)
    }
}

@Preview
@Composable
private fun ChangePasswordScreenPreview() {
    AppTheme { ChangePasswordScreen(ChangePasswordState(current = "secret", currentError = CurrentPasswordProblem.Incorrect)) {} }
}

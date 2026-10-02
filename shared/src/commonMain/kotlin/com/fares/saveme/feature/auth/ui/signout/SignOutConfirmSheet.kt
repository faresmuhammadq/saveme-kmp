package com.fares.saveme.feature.auth.ui.signout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.component.SecondaryButton
import org.jetbrains.compose.resources.pluralStringResource
import com.fares.saveme.designsystem.component.AppBottomSheet
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.DestructiveButton
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.action_cancel
import saveme.shared.generated.resources.action_sync_now
import saveme.shared.generated.resources.sign_out_pending_warning
import saveme.shared.generated.resources.sign_out_action
import saveme.shared.generated.resources.sign_out_confirm_body
import saveme.shared.generated.resources.sign_out_confirm_title

/**
 * "Sign out of this phone?" — shared by Home and the Account area. With changes still only on the
 * phone it says how many would be discarded and offers to sync first (spec 004 FR-017).
 */
@Composable
fun SignOutConfirmSheet(
    signingOut: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    pendingChanges: Int = 0,
    onSyncNow: (() -> Unit)? = null,
) {
    AppBottomSheet(onDismiss = { if (!signingOut) onDismiss() }, title = stringResource(Res.string.sign_out_confirm_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
            Text(stringResource(Res.string.sign_out_confirm_body), style = AppTheme.type.body, color = AppTheme.colors.ink2)
            if (pendingChanges > 0) {
                InlineAlert(
                    tone = AlertTone.Warning,
                    text = pluralStringResource(Res.plurals.sign_out_pending_warning, pendingChanges, pendingChanges),
                    modifier = Modifier.testTag("signOut.pendingWarning"),
                )
                onSyncNow?.let {
                    SecondaryButton(
                        stringResource(Res.string.action_sync_now),
                        it,
                        modifier = Modifier.testTag("signOut.syncNow"),
                        enabled = !signingOut,
                    )
                }
            }
            DestructiveButton(stringResource(Res.string.sign_out_action), onConfirm, loading = signingOut)
            AppTextButton(stringResource(Res.string.action_cancel), onDismiss, Modifier.fillMaxWidth(), enabled = !signingOut)
        }
    }
}

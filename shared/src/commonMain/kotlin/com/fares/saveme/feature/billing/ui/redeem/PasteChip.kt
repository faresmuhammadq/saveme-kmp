package com.fares.saveme.feature.billing.ui.redeem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.icon.FlowClipboard
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.authb_redeem_paste

/** "Paste from email": a 40dp pill (48dp to touch) that hands the pasted text to [onText]. It only reads, on the tap. */
@Composable
internal fun PasteChip(onText: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    @Suppress("DEPRECATION")
    val pasteSource = LocalClipboardManager.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button) {
                @Suppress("DEPRECATION")
                pasteSource.getText()?.text?.let(onText)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(40.dp)
                .background(AppTheme.colors.surface, AppTheme.shapes.pill)
                .border(1.dp, AppTheme.colors.outlineVariant, AppTheme.shapes.pill)
                .padding(horizontal = 14.dp),
        ) {
            Icon(AppIcons.FlowClipboard, contentDescription = null, tint = AppTheme.colors.ink, modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.authb_redeem_paste),
                style = AppTheme.type.caption.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                color = AppTheme.colors.ink,
            )
        }
    }
}

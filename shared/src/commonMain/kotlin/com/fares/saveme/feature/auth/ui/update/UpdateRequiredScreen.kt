package com.fares.saveme.feature.auth.ui.update

import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fares.saveme.feature.auth.ui.AuthLogo
import saveme.shared.generated.resources.authb_update_have
import saveme.shared.generated.resources.authb_update_need
import saveme.shared.generated.resources.authb_update_newer
import saveme.shared.generated.resources.authb_update_or_later
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.update_action
import saveme.shared.generated.resources.authb_update_body
import saveme.shared.generated.resources.update_title

private val UpdateBlue = Color(0xFF2E4FB8)

/** Blocking: no back action (FR-018). */
@Composable
fun UpdateRequiredScreen(state: UpdateRequiredState, onIntent: (UpdateRequiredIntent) -> Unit) {
    val minimum = state.minimumVersion?.takeIf { it.isNotBlank() }
    Column(
        Modifier
            .fillMaxSize()
            .background(UpdateBlue)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        AuthLogo(44.dp, Modifier.padding(start = 26.dp, top = 40.dp), ring = Color.White, dot = Color.White)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 40.dp),
        ) {
            Text(
                stringResource(Res.string.update_title),
                style = AppTheme.type.headline.copy(fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.6).sp),
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.authb_update_body),
                style = AppTheme.type.body.copy(fontSize = 16.sp),
                color = Color.White.copy(alpha = 0.85f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
                VersionTile(
                    stringResource(Res.string.authb_update_have),
                    state.installedVersion?.takeIf { it.isNotBlank() } ?: "—",
                    AppTheme.colors.ink,
                    Modifier.weight(1f),
                )
                VersionTile(
                    stringResource(Res.string.authb_update_need),
                    minimum?.let { stringResource(Res.string.authb_update_or_later, it) } ?: stringResource(Res.string.authb_update_newer),
                    UpdateBlue,
                    Modifier.weight(1f),
                )
            }
        }
        Button(
            onClick = { onIntent(UpdateRequiredIntent.UpdateClicked) },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = UpdateBlue),
            modifier = Modifier
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 26.dp)
                .fillMaxWidth()
                .heightIn(min = AppTheme.sizes.button),
        ) {
            Text(stringResource(Res.string.update_action), style = AppTheme.type.label.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold))
        }
    }
}

@Composable
private fun VersionTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(AppTheme.colors.surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.ink2)
        Text(
            value,
            style = AppTheme.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Preview
@Composable
private fun UpdateRequiredScreenPreview() {
    AppTheme { UpdateRequiredScreen(UpdateRequiredState("0.3.0")) {} }
}

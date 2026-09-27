package com.fares.saveme.feature.auth.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AlertTone
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.InlineAlert
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.home_deletion_cancelled
import saveme.shared.generated.resources.home_signed_in_as
import saveme.shared.generated.resources.home_title

@Composable
fun HomeScreen(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    AppScreen(topBar = { AppTopAppBar(stringResource(Res.string.home_title)) }) {
        if (state.deletionCancelledNotice) {
            InlineAlert(
                tone = AlertTone.Info,
                text = stringResource(Res.string.home_deletion_cancelled),
            )
        }
        Text(stringResource(Res.string.home_signed_in_as, state.email), style = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    AppTheme { HomeScreen(HomeState(email = "sara@example.com", deletionCancelledNotice = true)) {} }
}

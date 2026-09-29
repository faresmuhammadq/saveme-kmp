package com.fares.saveme.feature.account.ui

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.icon.AppIcons
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back

/** Top bar with a back arrow, shared by the account screens. */
@Composable
internal fun BackBar(title: String, onBack: () -> Unit) {
    AppTopAppBar(
        title = title,
        navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), onBack) },
    )
}

package com.fares.saveme.devtools.push

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.fares.saveme.designsystem.component.AppIconButton
import com.fares.saveme.designsystem.component.AppListItem
import com.fares.saveme.designsystem.component.AppScreen
import com.fares.saveme.designsystem.component.AppTextButton
import com.fares.saveme.designsystem.component.AppTopAppBar
import com.fares.saveme.designsystem.component.ListTrailing
import com.fares.saveme.designsystem.component.SecondaryButton
import com.fares.saveme.designsystem.icon.AppIcons
import com.fares.saveme.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import saveme.shared.generated.resources.Res
import saveme.shared.generated.resources.cd_back
import saveme.shared.generated.resources.simulate_push_not_on_phone
import saveme.shared.generated.resources.simulate_push_post
import saveme.shared.generated.resources.simulate_push_rotate
import saveme.shared.generated.resources.simulate_push_title
import saveme.shared.generated.resources.simulate_push_unknown

@Composable
fun SimulatePushScreen(state: SimulatePushState, onIntent: (SimulatePushIntent) -> Unit) {
    AppScreen(
        topBar = {
            AppTopAppBar(
                title = stringResource(Res.string.simulate_push_title),
                navigationIcon = { AppIconButton(AppIcons.ArrowBack, stringResource(Res.string.cd_back), { onIntent(SimulatePushIntent.Back) }) },
            )
        },
    ) {
        SecondaryButton(
            stringResource(Res.string.simulate_push_rotate),
            { onIntent(SimulatePushIntent.Rotate) },
            modifier = Modifier.testTag("simulatePush.rotate"),
        )
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.surface, AppTheme.shapes.card)) {
            val post = stringResource(Res.string.simulate_push_post)
            val notOnPhone = stringResource(Res.string.simulate_push_not_on_phone)
            val unknown = stringResource(Res.string.simulate_push_unknown)
            state.rows.forEach { row ->
                val name = if (row.type == SimulatePayloads.UNKNOWN) unknown else row.type
                AppListItem(
                    headline = name,
                    supporting = if (row.notOnPhone) notOnPhone else null,
                    trailing = ListTrailing.Custom {
                        AppTextButton(
                            post,
                            { onIntent(SimulatePushIntent.Post(row.type)) },
                            Modifier.testTag("simulatePush.post.${row.type}"),
                        )
                    },
                    modifier = Modifier.testTag("simulatePush.type.${row.type}"),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SimulatePushScreenPreview() {
    AppTheme { SimulatePushScreen(SimulatePushState(rows = SimulatePayloads.rows(PhoneIds(null, null, null)))) {} }
}

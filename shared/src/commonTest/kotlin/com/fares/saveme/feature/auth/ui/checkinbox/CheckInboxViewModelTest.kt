package com.fares.saveme.feature.auth.ui.checkinbox

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckInboxViewModelTest {

    @Test
    fun goToSignInCarriesTheEmailForBothKinds() = runMviTest {
        CheckInboxKind.entries.forEach { kind ->
            val vm = CheckInboxViewModel("sara@example.com", kind)
            vm.effects.test {
                vm.onIntent(CheckInboxIntent.GoToSignInClicked)
                assertEquals(CheckInboxEffect.NavigateSignIn("sara@example.com"), awaitItem())
            }
        }
    }
}

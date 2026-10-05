package com.fares.saveme.feature.sharing.ui.invitation

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.sharing.FAKE_TOKEN
import com.fares.saveme.feature.sharing.domain.model.DeclineResult
import com.fares.saveme.feature.sharing.domain.model.JoinResult
import com.fares.saveme.feature.sharing.domain.model.PreviewResult
import com.fares.saveme.feature.sharing.ui.uiPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvitationReducerTest {
    @Test
    fun modeBusyDeclineConfirmationAndTimeZoneAreCarried() {
        var state = InvitationState()
        assertEquals(InvitationMode.Loading, state.mode)
        state = reduce(state, InvitationChange.Mode(InvitationMode.Ready(uiPreview())))
        state = reduce(state, InvitationChange.Busy(true))
        state = reduce(state, InvitationChange.ConfirmDecline(true))
        state = reduce(state, InvitationChange.TimeZone("Africa/Cairo"))
        assertIs<InvitationMode.Ready>(state.mode)
        assertTrue(state.busy && state.confirmDecline)
        assertEquals("Africa/Cairo", state.timeZoneId)
    }

    @Test
    fun theStateHoldsNoSecret() {
        val state = reduce(InvitationState(), InvitationChange.Mode(InvitationMode.Ready(uiPreview())))
        assertFalse(state.toString().contains(FAKE_TOKEN))
    }
}

class InvitationViewModelTest {
    private var signedIn = true
    private var previewAnswer: PreviewResult = PreviewResult.Ready(uiPreview())
    private var joinAnswer: JoinResult = JoinResult.Joined("w1")
    private var declineAnswer: DeclineResult = DeclineResult.Declined
    private val calls = mutableListOf<String>()
    private val held = mutableListOf<String>()
    private var cleared = 0
    private var declinedNotices = 0

    private fun vm(token: String = FAKE_TOKEN) = InvitationViewModel(
        token = token, isSignedIn = { signedIn },
        preview = { calls += "preview"; previewAnswer }, join = { calls += "join"; joinAnswer }, decline = { calls += "decline"; declineAnswer },
        hold = { held += it }, clearHeld = { cleared++ }, announceDeclined = { declinedNotices++ },
    )

    @Test
    fun aSignedInPersonSeesWhatTheLinkOffers() = runMviTest {
        val vm = vm()
        vm.onIntent(InvitationIntent.Opened)
        assertEquals(InvitationMode.Ready(uiPreview()), vm.state.value.mode)
        assertFalse(vm.state.value.busy)
        vm.onIntent(InvitationIntent.Opened)
        assertEquals(listOf("preview"), calls, "opened once")
    }

    @Test
    fun signedOutMakesNoCallAndAMalformedLinkNeverCallsTheServer() = runMviTest {
        signedIn = false
        val signedOut = vm()
        signedOut.onIntent(InvitationIntent.Opened)
        assertEquals(InvitationMode.SignedOut, signedOut.state.value.mode)
        signedIn = true
        val bad = vm("short")
        bad.onIntent(InvitationIntent.Opened)
        assertEquals(InvitationMode.InvalidLink, bad.state.value.mode)
        assertTrue(calls.isEmpty())
        signedOut.effects.test {
            signedOut.onIntent(InvitationIntent.SignInClicked)
            assertEquals(InvitationEffect.NavigateSignIn, awaitItem())
            signedOut.onIntent(InvitationIntent.RegisterClicked)
            assertEquals(InvitationEffect.NavigateRegister, awaitItem())
        }
    }

    @Test
    fun everyPreviewAnswerBecomesAMode() = runMviTest {
        val cases = listOf(
            PreviewResult.Unavailable to InvitationMode.Unavailable,
            PreviewResult.InvalidLink to InvitationMode.InvalidLink,
            PreviewResult.TooMany(3) to InvitationMode.TooMany(3),
            PreviewResult.Offline to InvitationMode.Offline,
            PreviewResult.Failed("r1") to InvitationMode.Failed("r1"),
            PreviewResult.Ready(uiPreview(already = true)) to InvitationMode.AlreadyMember("w1", "Home"),
        )
        for ((answer, mode) in cases) {
            previewAnswer = answer
            val vm = vm()
            vm.onIntent(InvitationIntent.Opened)
            assertEquals(mode, vm.state.value.mode)
        }
    }

    @Test
    fun anUnusableLinkDropsTheHeldInvitation() = runMviTest {
        previewAnswer = PreviewResult.Unavailable
        vm().onIntent(InvitationIntent.Opened)
        assertEquals(1, cleared)
    }

    @Test
    fun joiningOpensTheWalletAndNeverRepeatsOnItsOwn() = runMviTest {
        val vm = vm()
        vm.onIntent(InvitationIntent.Opened)
        vm.effects.test {
            vm.onIntent(InvitationIntent.JoinClicked)
            assertEquals(InvitationEffect.OpenWallet("w1"), awaitItem())
        }
        assertEquals(listOf("preview", "join"), calls)
    }

    @Test
    fun aJoinThatFailsKeepsTheScreenAndTryAgainRepeatsTheJoinOnly() = runMviTest {
        val vm = vm()
        vm.onIntent(InvitationIntent.Opened)
        joinAnswer = JoinResult.Offline
        vm.onIntent(InvitationIntent.JoinClicked)
        assertEquals(InvitationMode.Offline, vm.state.value.mode)
        joinAnswer = JoinResult.OwnerPlanFull
        vm.onIntent(InvitationIntent.RetryClicked)
        assertEquals(InvitationMode.OwnerPlanFull, vm.state.value.mode)
        assertEquals(listOf("preview", "join", "join"), calls)
    }

    @Test
    fun theOtherJoinAnswersBecomeModes() = runMviTest {
        val cases = listOf(
            JoinResult.Unavailable to InvitationMode.Unavailable,
            JoinResult.InvalidLink to InvitationMode.InvalidLink,
            JoinResult.TooMany(2) to InvitationMode.TooMany(2),
            JoinResult.Failed("r") to InvitationMode.Failed("r"),
        )
        for ((answer, mode) in cases) {
            joinAnswer = answer
            val vm = vm()
            vm.onIntent(InvitationIntent.Opened)
            vm.onIntent(InvitationIntent.JoinClicked)
            assertEquals(mode, vm.state.value.mode)
        }
    }

    @Test
    fun decliningConfirmsFirstThenClosesWithTheMessage() = runMviTest {
        val vm = vm()
        vm.onIntent(InvitationIntent.Opened)
        vm.onIntent(InvitationIntent.DeclineClicked)
        assertTrue(vm.state.value.confirmDecline)
        vm.onIntent(InvitationIntent.DeclineDismissed)
        assertFalse(vm.state.value.confirmDecline)
        assertFalse("decline" in calls)
        vm.onIntent(InvitationIntent.DeclineClicked)
        vm.effects.test {
            vm.onIntent(InvitationIntent.DeclineConfirmed)
            assertEquals(InvitationEffect.Close(InvitationCloseMessage.Declined), awaitItem())
        }
        assertEquals(1, declinedNotices)
    }

    @Test
    fun notNowKeepsTheSecretInMemoryAndClosesAndAnInvalidLinkKeepsNothing() = runMviTest {
        val vm = vm()
        vm.onIntent(InvitationIntent.Opened)
        vm.effects.test {
            vm.onIntent(InvitationIntent.LaterClicked)
            assertEquals(InvitationEffect.Close(), awaitItem())
        }
        assertEquals(listOf(FAKE_TOKEN), held)
        val bad = vm("short")
        bad.effects.test {
            bad.onIntent(InvitationIntent.LaterClicked)
            awaitItem()
        }
        assertEquals(1, held.size)
    }

    @Test
    fun openWalletOnlyWorksForAnAlreadyMember() = runMviTest {
        previewAnswer = PreviewResult.Ready(uiPreview(already = true))
        val vm = vm()
        vm.onIntent(InvitationIntent.Opened)
        vm.effects.test {
            vm.onIntent(InvitationIntent.OpenWalletClicked)
            assertEquals(InvitationEffect.OpenWallet("w1"), awaitItem())
        }
        assertNull(null)
    }
}

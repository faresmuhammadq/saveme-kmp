package com.fares.saveme.feature.sharing.ui.invite

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.sharing.domain.model.Delivery
import com.fares.saveme.feature.sharing.domain.model.EmailProblem
import com.fares.saveme.feature.sharing.domain.model.InvitationDraft
import com.fares.saveme.feature.sharing.domain.model.InviteCheck
import com.fares.saveme.feature.sharing.domain.model.InviteNotice
import com.fares.saveme.feature.sharing.domain.model.InviteResult
import com.fares.saveme.feature.sharing.domain.model.InvitedRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InviteReducerTest {
    @Test
    fun leastPrivilegeIsPreselectedAndChannelAndRoleLockOnceALinkIsAskedFor() {
        val state = InviteState()
        assertEquals(InvitedRole.Viewer, state.role)
        assertEquals(InviteChannel.Email, state.channel)
        val linked = reduce(state, InviteChange.Link(LinkProgress.Waiting("i1")))
        assertTrue(linked.locked)
        assertEquals(linked, reduce(linked, InviteChange.Role(InvitedRole.Editor)))
        assertEquals(linked, reduce(linked, InviteChange.Channel(InviteChannel.Email)))
        assertEquals(InvitedRole.Editor, reduce(state, InviteChange.Role(InvitedRole.Editor)).role)
    }

    @Test
    fun typingClearsTheErrorAndTheErrorIsShownAfterTheFieldIsLeft() {
        val withError = reduce(InviteState(), InviteChange.EmailError(EmailProblem.Invalid))
        assertEquals(EmailProblem.Invalid, withError.emailError)
        assertEquals(null, reduce(withError, InviteChange.Email("a")).emailError)
    }

    @Test
    fun theStateNeverPrintsTheAddress() {
        val state = reduce(InviteState(), InviteChange.Email("karim@example.com"))
        assertFalse(state.toString().contains("karim"))
    }
}

class InviteViewModelTest {
    private val check = MutableStateFlow<InviteCheck>(InviteCheck.Allowed)
    private val drafts = mutableListOf<InvitationDraft>()
    private val announced = mutableListOf<InviteNotice>()
    private var inviteAnswer: InviteResult = InviteResult.Queued("i1")
    private var confirmedAnswer: InviteResult = InviteResult.Queued("i2")
    private var delivery: Delivery = Delivery.Sent
    private val held = MutableStateFlow(false)
    private var text: String? = "SECRET-SHARE-TEXT"

    private fun vm(replacing: String? = null, replaceAnswer: InviteResult = InviteResult.Queued("i9")) = InviteViewModel(
        walletId = "w1", replacing = replacing, inviteCheck = check,
        invite = { drafts += it; inviteAnswer }, inviteConfirmed = { drafts += it; confirmedAnswer },
        replaceLink = { replaceAnswer }, awaitDelivery = { delivery }, linkHeld = { held }, shareText = { text },
        announce = { announced += it },
    )

    @Test
    fun sendingAnEmailInvitationClosesWithSentOrSavedAndLeavesTheNoticeForMembers() = runMviTest {
        val vm = vm()
        vm.onIntent(InviteIntent.RoleChosen(InvitedRole.Editor))
        vm.onIntent(InviteIntent.EmailChanged(" karim@example.com "))
        vm.effects.test {
            vm.onIntent(InviteIntent.SendClicked)
            assertEquals(InviteEffect.Close(InviteNotice.Sent), awaitItem())
        }
        assertEquals(listOf(InvitationDraft(InvitedRole.Editor, "karim@example.com")), drafts)
        assertEquals(listOf(InviteNotice.Sent), announced)

        delivery = Delivery.Waiting
        val offline = vm()
        offline.onIntent(InviteIntent.EmailChanged("a@b.co"))
        offline.effects.test {
            offline.onIntent(InviteIntent.SendClicked)
            assertEquals(InviteEffect.Close(InviteNotice.SavedOffline), awaitItem())
        }
    }

    @Test
    fun aBlankAddressShowsAnErrorWithoutCalling() = runMviTest {
        val vm = vm()
        vm.onIntent(InviteIntent.SendClicked)
        assertEquals(EmailProblem.Invalid, vm.state.value.emailError)
        assertTrue(drafts.isEmpty())
        vm.onIntent(InviteIntent.EmailChanged("not an address"))
        vm.onIntent(InviteIntent.EmailLeft)
        assertEquals(EmailProblem.Invalid, vm.state.value.emailError)
    }

    @Test
    fun theGateAndFieldAnswersLandInTheState() = runMviTest {
        val vm = vm()
        vm.onIntent(InviteIntent.EmailChanged("a@b.co"))
        inviteAnswer = InviteResult.OwnAddress
        vm.onIntent(InviteIntent.SendClicked)
        assertEquals(EmailProblem.OwnAddress, vm.state.value.emailError)
        inviteAnswer = InviteResult.Full(2)
        vm.onIntent(InviteIntent.SendClicked)
        assertEquals(InviteCheck.Full(2), vm.state.value.check)
        inviteAnswer = InviteResult.SharingNotIncluded
        vm.onIntent(InviteIntent.SendClicked)
        assertEquals(InviteCheck.SharingNotIncluded, vm.state.value.check)
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun anUnverifiedOwnerGetsThePrompt() = runMviTest {
        inviteAnswer = InviteResult.NotVerified
        val vm = vm()
        vm.onIntent(InviteIntent.EmailChanged("a@b.co"))
        vm.effects.test {
            vm.onIntent(InviteIntent.SendClicked)
            assertEquals(InviteEffect.ShowVerifyPrompt, awaitItem())
        }
    }

    @Test
    fun supersedeAsksThenSendsTheConfirmedInvitation() = runMviTest {
        inviteAnswer = InviteResult.SupersedeNeeded
        val vm = vm()
        vm.onIntent(InviteIntent.EmailChanged("a@b.co"))
        vm.onIntent(InviteIntent.SendClicked)
        assertTrue(vm.state.value.confirmSupersede)
        vm.onIntent(InviteIntent.SupersedeDismissed)
        assertFalse(vm.state.value.confirmSupersede)
        vm.onIntent(InviteIntent.SendClicked)
        vm.effects.test {
            vm.onIntent(InviteIntent.SupersedeConfirmed)
            assertEquals(InviteEffect.Close(InviteNotice.Sent), awaitItem())
        }
        assertEquals(3, drafts.size)
    }

    @Test
    fun seePlanOnlyOpensThePlan() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(InviteIntent.SeePlanClicked)
            assertEquals(InviteEffect.OpenPlan, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun theLinkChannelWaitsThenBecomesReadyWhenTheLinkArrives() = runMviTest {
        delivery = Delivery.Sent
        val vm = vm()
        vm.onIntent(InviteIntent.ChannelChosen(InviteChannel.Link))
        vm.onIntent(InviteIntent.SendClicked)
        assertEquals(InvitationDraft(InvitedRole.Viewer, null), drafts.single())
        assertEquals(LinkProgress.Waiting("i1"), vm.state.value.link)
        held.value = true
        assertEquals(LinkProgress.Ready("i1"), vm.state.value.link)
    }

    @Test
    fun offlineTheLinkWaitsForTheConnectionAndCanBeSharedFromMembers() = runMviTest {
        delivery = Delivery.Waiting
        val vm = vm()
        vm.onIntent(InviteIntent.ChannelChosen(InviteChannel.Link))
        vm.onIntent(InviteIntent.SendClicked)
        assertEquals(LinkProgress.WaitingOffline("i1"), vm.state.value.link)
        held.value = true
        assertIs<LinkProgress.Ready>(vm.state.value.link)
    }

    @Test
    fun shareAndCopyEmitRedactedEffectsOnceTheLinkIsReady() = runMviTest {
        val vm = vm()
        vm.onIntent(InviteIntent.ChannelChosen(InviteChannel.Link))
        vm.onIntent(InviteIntent.SendClicked)
        held.value = true
        vm.effects.test {
            vm.onIntent(InviteIntent.ShareClicked)
            val share = awaitItem()
            assertEquals(InviteEffect.Share("SECRET-SHARE-TEXT"), share)
            assertFalse(share.toString().contains("SECRET"))
            vm.onIntent(InviteIntent.CopyClicked)
            val copy = awaitItem()
            assertFalse(copy.toString().contains("SECRET"))
            assertEquals(InviteEffect.Copied, awaitItem())
            vm.onIntent(InviteIntent.DoneClicked)
            assertEquals(InviteEffect.Close(), awaitItem())
        }
        assertFalse(vm.state.value.toString().contains("SECRET"))
    }

    @Test
    fun shareDoesNothingBeforeALinkWasAskedFor() = runMviTest {
        val vm = vm()
        vm.effects.test {
            vm.onIntent(InviteIntent.ShareClicked)
            vm.onIntent(InviteIntent.CopyClicked)
            expectNoEvents()
        }
    }

    @Test
    fun replacingALostLinkStartsInTheLinkChannelAndAsksForANewLink() = runMviTest {
        val vm = vm(replacing = "old")
        assertEquals(InviteChannel.Link, vm.state.value.channel)
        assertEquals(LinkProgress.Waiting("i9"), vm.state.value.link)
        held.value = true
        assertEquals(LinkProgress.Ready("i9"), vm.state.value.link)
    }

    @Test
    fun aSecondSendWhileOneRunsIsIgnored() = runMviTest {
        val gate = CompletableDeferred<InviteResult>()
        val calls = mutableListOf<Int>()
        val vm = InviteViewModel(
            walletId = "w1", replacing = null, inviteCheck = check,
            invite = { calls += 1; gate.await() }, inviteConfirmed = { gate.await() },
            replaceLink = { gate.await() }, awaitDelivery = { Delivery.Sent }, linkHeld = { held }, shareText = { null },
        )
        vm.onIntent(InviteIntent.EmailChanged("a@b.co"))
        vm.onIntent(InviteIntent.SendClicked)
        vm.onIntent(InviteIntent.SendClicked)
        assertEquals(1, calls.size)
        gate.complete(InviteResult.Queued("i1"))
    }
}

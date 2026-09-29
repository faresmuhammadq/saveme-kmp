package com.fares.saveme.feature.account.ui.sessions

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.DeviceSession
import com.fares.saveme.feature.account.domain.model.DeviceSessionList
import com.fares.saveme.feature.account.ui.AccountBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val at = Instant.parse("2026-09-28T09:00:00Z")
private val current = DeviceSession("a", "Pixel 8", "android", "1.0.0", at, at, isCurrent = true)
private val other = DeviceSession("b", "Galaxy", "android", "1.0.0", at, null, isCurrent = false)

class DeviceSessionsReducerTest {
    @Test
    fun endOthersOnlyWhenAnotherSessionExists() {
        val alone = reduce(DeviceSessionsState(), DeviceSessionsChange.Stored(DeviceSessionList(listOf(current), at)))
        assertEquals(false, alone.canEndOthers)
        val two = reduce(alone, DeviceSessionsChange.Stored(DeviceSessionList(listOf(current, other), at)))
        assertEquals(true, two.canEndOthers)
    }
}

class DeviceSessionsViewModelTest {

    private fun vm(
        list: List<DeviceSession> = listOf(current, other),
        refresh: suspend () -> ActionOutcome = { ActionOutcome.Done },
        endSession: suspend (String) -> ActionOutcome = { ActionOutcome.Done },
        endOthers: suspend () -> ActionOutcome = { ActionOutcome.Done },
    ) = DeviceSessionsViewModel(
        sessions = MutableStateFlow(DeviceSessionList(list, at)),
        timeZoneId = flowOf("Africa/Cairo"),
        refresh = refresh,
        endSession = endSession,
        endOthers = endOthers,
    )

    @Test
    fun theCurrentSessionCannotBeEndedFromTheList() = runMviTest {
        val vm = vm()
        vm.onIntent(DeviceSessionsIntent.EndClicked("a"))
        assertNull(vm.state.value.confirm)
    }

    @Test
    fun endingAsksFirstThenCallsOnce() = runMviTest {
        val ended = mutableListOf<String>()
        val vm = vm(endSession = { ended += it; ActionOutcome.Done })
        vm.onIntent(DeviceSessionsIntent.EndClicked("b"))
        assertEquals(ConfirmEnd.One("b", "Galaxy"), vm.state.value.confirm)
        vm.onIntent(DeviceSessionsIntent.DismissConfirm)
        assertNull(vm.state.value.confirm)
        assertEquals(emptyList(), ended)

        vm.onIntent(DeviceSessionsIntent.EndClicked("b"))
        vm.effects.test {
            vm.onIntent(DeviceSessionsIntent.ConfirmEnd)
            assertEquals(DeviceSessionsEffect.Ended(others = false), awaitItem())
        }
        assertEquals(listOf("b"), ended)
        assertNull(vm.state.value.confirm)
        assertEquals(false, vm.state.value.ending)
    }

    @Test
    fun endOthersIsHiddenWhenAlone() = runMviTest {
        val vm = vm(list = listOf(current))
        vm.onIntent(DeviceSessionsIntent.EndOthersClicked)
        assertNull(vm.state.value.confirm)
    }

    @Test
    fun offlineEndShowsTheBannerAndKeepsTheList() = runMviTest {
        val vm = vm(endOthers = { ActionOutcome.Offline })
        vm.onIntent(DeviceSessionsIntent.EndOthersClicked)
        vm.onIntent(DeviceSessionsIntent.ConfirmEnd)
        assertEquals(AccountBanner.Offline, vm.state.value.banner)
        assertEquals(2, vm.state.value.sessions.size)
    }

    @Test
    fun openingRefreshes() = runMviTest {
        var refreshed = 0
        vm(refresh = { refreshed++; ActionOutcome.Done })
        assertEquals(1, refreshed)
    }
}

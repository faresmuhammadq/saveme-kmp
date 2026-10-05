package com.fares.saveme.feature.sharing.ui.activity

import app.cash.turbine.test
import com.fares.saveme.core.presentation.runMviTest
import com.fares.saveme.feature.sharing.domain.model.ActivityAction
import com.fares.saveme.feature.sharing.domain.model.ActivityItem
import com.fares.saveme.feature.sharing.domain.model.ActivityPage
import com.fares.saveme.feature.sharing.domain.model.ActivitySubject
import com.fares.saveme.feature.sharing.domain.model.Actor
import com.fares.saveme.feature.sharing.domain.model.SubjectKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun item(id: String, kind: SubjectKind = SubjectKind.Transaction, onPhone: Boolean = true) = ActivityItem(
    id, Instant.parse("2026-10-02T10:00:00Z"), Actor.Named("Karim"), ActivityAction.Created, ActivitySubject(kind, "s-$id", null, onPhone), null,
)

class ActivityReducerTest {
    @Test
    fun theFirstPageReplacesAndMoreAppendsWithoutDuplicates() {
        var state = reduce(ActivityState(), ActivityChange.First(listOf(item("a"), item("b")), "c1"))
        assertFalse(state.loading)
        assertEquals("c1", state.nextCursor)
        state = reduce(state, ActivityChange.More(listOf(item("b"), item("c")), null))
        assertEquals(listOf("a", "b", "c"), state.items.map { it.id })
        assertEquals(null, state.nextCursor)
        assertFalse(state.canLoadMore)
    }

    @Test
    fun anErrorStopsLoadingAndHidesLoadMoreUntilRetried() {
        val withMore = reduce(ActivityState(), ActivityChange.First(listOf(item("a")), "c1"))
        assertTrue(withMore.canLoadMore)
        val failed = reduce(withMore, ActivityChange.Error(ActivityError.Offline))
        assertFalse(failed.canLoadMore)
        assertEquals(ActivityError.Offline, failed.error)
        assertEquals(null, reduce(failed, ActivityChange.LoadingMore(true)).error)
    }

    @Test
    fun anEmptyFirstPageIsEmptyOnlyAfterLoading() {
        assertFalse(ActivityState().isEmpty)
        assertTrue(reduce(ActivityState(), ActivityChange.First(emptyList(), null)).isEmpty)
    }
}

class ActivityViewModelTest {
    private val cursors = mutableListOf<String?>()
    private var answers = ArrayDeque<ActivityPage>()

    private fun vm() = ActivityViewModel("w1", page = { cursor -> cursors += cursor; answers.removeFirst() })

    @Test
    fun theFirstPageLoadsOnOpenAndOnlyOnce() = runMviTest {
        answers += ActivityPage.Loaded(listOf(item("a")), "c1")
        val vm = vm()
        vm.onIntent(ActivityIntent.Opened)
        vm.onIntent(ActivityIntent.Opened)
        assertEquals(listOf<String?>(null), cursors)
        assertEquals(listOf("a"), vm.state.value.items.map { it.id })
    }

    @Test
    fun loadMoreFollowsTheCursorAndNeverLoadsTwiceAtOnce() = runMviTest {
        answers += ActivityPage.Loaded(listOf(item("a")), "c1")
        answers += ActivityPage.Loaded(listOf(item("b")), null)
        val vm = vm()
        vm.onIntent(ActivityIntent.Opened)
        vm.onIntent(ActivityIntent.LoadMore)
        vm.onIntent(ActivityIntent.LoadMore)
        assertEquals(listOf<String?>(null, "c1"), cursors)
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.id })
    }

    @Test
    fun errorsOfferTryAgainWhichRepeatsTheRightRead() = runMviTest {
        answers += ActivityPage.Offline
        val vm = vm()
        vm.onIntent(ActivityIntent.Opened)
        assertEquals(ActivityError.Offline, vm.state.value.error)
        answers += ActivityPage.Loaded(listOf(item("a")), "c1")
        vm.onIntent(ActivityIntent.Retry)
        assertEquals(listOf<String?>(null, null), cursors)
        answers += ActivityPage.Failed("r1")
        vm.onIntent(ActivityIntent.LoadMore)
        assertEquals(ActivityError.Failed("r1"), vm.state.value.error)
        answers += ActivityPage.Loaded(listOf(item("b")), null)
        vm.onIntent(ActivityIntent.Retry)
        assertEquals("c1", cursors.last())
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.id })
    }

    @Test
    fun aRestartedWalkReplacesTheListAndGoneClosesTheScreen() = runMviTest {
        answers += ActivityPage.Loaded(listOf(item("a")), "c1")
        answers += ActivityPage.Loaded(listOf(item("z")), "c9", restarted = true)
        val vm = vm()
        vm.onIntent(ActivityIntent.Opened)
        vm.onIntent(ActivityIntent.LoadMore)
        assertEquals(listOf("z"), vm.state.value.items.map { it.id })
        answers += ActivityPage.Gone
        vm.effects.test {
            vm.onIntent(ActivityIntent.LoadMore)
            assertEquals(ActivityEffect.NavigateBack, awaitItem())
        }
    }

    @Test
    fun tappingATransactionOpensItOnlyWhenThePhoneHasIt() = runMviTest {
        answers += ActivityPage.Loaded(listOf(item("a"), item("b", onPhone = false), item("c", SubjectKind.Member)), null)
        val vm = vm()
        vm.onIntent(ActivityIntent.Opened)
        vm.effects.test {
            vm.onIntent(ActivityIntent.ItemClicked("a"))
            assertEquals(ActivityEffect.OpenTransaction("s-a", "w1"), awaitItem())
            vm.onIntent(ActivityIntent.ItemClicked("b"))
            vm.onIntent(ActivityIntent.ItemClicked("c"))
            vm.onIntent(ActivityIntent.Back)
            assertEquals(ActivityEffect.NavigateBack, awaitItem())
        }
    }
}

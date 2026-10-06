package com.fares.saveme.feature.transactions.domain

import com.fares.saveme.feature.transactions.domain.model.AddedBy
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Added by a former member" only when the wallet's members are known and lack the author (spec 016 FR-033). */
class TransactionAddedByTest {

    private val members = mapOf("u2" to "Omar", "u4" to null as String?, "u5" to " ")

    @Test
    fun anAuthorMissingFromANonEmptyMemberMapIsAFormerMember() {
        assertEquals(AddedBy.FormerMember, addedByOf("u9", "u1", members))
    }

    @Test
    fun aPresentMemberWithoutAUsableNameIsAnotherMember() {
        assertEquals(AddedBy.AnotherMember, addedByOf("u4", "u1", members))
        assertEquals(AddedBy.AnotherMember, addedByOf("u5", "u1", members))
    }

    @Test
    fun aNamedMemberIsNamed() {
        assertEquals(AddedBy.Member("Omar"), addedByOf("u2", "u1", members))
    }

    @Test
    fun anEmptyMemberMapMeansTheMembersAreNotReadYet() {
        assertEquals(AddedBy.AnotherMember, addedByOf("u9", "u1", emptyMap()))
    }

    @Test
    fun selfAndBlankAreYou() {
        assertEquals(AddedBy.You, addedByOf("u1", "u1", members))
        assertEquals(AddedBy.You, addedByOf("", "u1", members))
        assertEquals(AddedBy.You, addedByOf("u1", "u1", emptyMap()))
    }
}

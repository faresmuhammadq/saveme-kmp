package com.fares.saveme.core.sync

import com.fares.saveme.core.database.FeedPlace

/** What a refresh does with one followed wallet after the heads were read (spec 011 research R-04). */
enum class WalletReadPlan {
    /** Read the wallet's feed from its stored place (or from the start when it has none). */
    Read,

    /** The stored place is older than the purge horizon: forget it, then read from the start with the sweep. */
    ReadFromStart,

    /** The phone already holds everything the server has for the wallet: stamp it as read, send nothing. */
    Skip,
}

/**
 * The decision table of sync.yaml v1.10 `listWalletHeads`, as a pure function (spec 011 FR-005). The stored position
 * is compared only with this wallet's own head; the cursor is never parsed, and the position is never sent.
 */
object HeadsDecision {
    fun decide(place: FeedPlace?, head: WalletHeadDto?): WalletReadPlan {
        if (head == null) return WalletReadPlan.Read
        val position = place?.position
        if (place?.cursor == null || position == null) return WalletReadPlan.Read
        return when {
            head.purgedChangeSeq > position -> WalletReadPlan.ReadFromStart
            head.changeSeq == position -> WalletReadPlan.Skip
            else -> WalletReadPlan.Read
        }
    }
}

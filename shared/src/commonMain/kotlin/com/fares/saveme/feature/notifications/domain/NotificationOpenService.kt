package com.fares.saveme.feature.notifications.domain

import com.fares.saveme.core.sync.ReadTarget
import com.fares.saveme.core.sync.SyncRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What a tap runs before its screen opens (R-10, FR-032). Its only collaborators are reads (the sync runner, an
 * entitlements read, an account read) and a presence lookup: a tap never confirms, skips, joins or saves (FR-029).
 */
class NotificationOpenService(
    private val runner: () -> SyncRunner,
    private val refreshEntitlements: suspend () -> Unit,
    private val refreshAccount: suspend () -> Unit,
    private val presence: RecordPresence,
    private val scope: CoroutineScope,
    private val timeout: Duration = 10.seconds,
) {
    /**
     * Returns the target once its screen can open: at once when its record is already on the phone (the read goes on in
     * the background), otherwise after the read finished, failed, was offline or took [timeout].
     */
    suspend fun open(route: PushRoute): PushTarget {
        if (route.read == PushRead.None) return route.target
        if (presence.has(route.target)) {
            scope.launch { read(route.read) }
            return route.target
        }
        withTimeoutOrNull(timeout) { read(route.read) }
        return route.target
    }

    private suspend fun read(read: PushRead) {
        when (read) {
            PushRead.None -> Unit
            PushRead.User -> runner().refresh(ReadTarget.User, force = true)
            is PushRead.Wallet -> runner().refresh(ReadTarget.Wallet(read.id), force = true)
            PushRead.Everything -> runner().refresh(ReadTarget.Everything, force = true)
            PushRead.Entitlements -> refreshEntitlements()
            PushRead.Account -> refreshAccount()
        }
    }
}

package com.fares.saveme.feature.account.domain

import com.fares.saveme.core.locale.AppLanguage
import com.fares.saveme.core.locale.AppLanguageStore
import com.fares.saveme.core.network.ApiResult
import com.fares.saveme.feature.account.data.ProfileRepository
import com.fares.saveme.feature.account.domain.model.ActionOutcome
import com.fares.saveme.feature.account.domain.model.toActionOutcome
import com.fares.saveme.feature.auth.domain.SessionManager

/**
 * Pulls `/me` into the device: the profile's server copy (when it is not older than the stored one), the cached account (deletion state)
 * and the app language — unless an edit is waiting, whose language already applies — then pushes
 * any waiting edit (spec FR-011, FR-016, FR-021).
 */
class AccountRefresher(
    private val repository: ProfileRepository,
    private val syncer: ProfileSyncer,
    private val sessions: SessionManager,
    private val languages: AppLanguageStore,
) {
    suspend fun refresh(): ActionOutcome {
        val result = repository.fetchMe()
        if (result !is ApiResult.Success) return result.toActionOutcome()
        val me = result.value
        // The profile can also reach the phone through its change feed; the newer version wins (spec 009 R-13).
        repository.storeFeedCopy(me.profile)
        sessions.updateAccount(me.account)
        if (!syncer.hasPendingEdit()) {
            AppLanguage.fromTag(me.profile.locale)?.let { languages.set(it) }
        } else {
            syncer.push()
        }
        return ActionOutcome.Done
    }
}

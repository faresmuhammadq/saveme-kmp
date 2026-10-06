package com.fares.saveme.feature.reports.domain

import com.fares.saveme.core.session.UserDataCleaner

/** Sign-out drops the session's choices and learned period dates (FR-029); reports store nothing else. */
class ReportsCleaner(
    private val choices: ReportChoicesStore,
    private val learned: LearnedPeriods,
) : UserDataCleaner {
    override suspend fun clear() {
        choices.clear()
        learned.clear()
    }
}

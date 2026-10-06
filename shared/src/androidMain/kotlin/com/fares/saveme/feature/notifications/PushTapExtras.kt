package com.fares.saveme.feature.notifications

import android.content.Intent
import android.os.Bundle
import com.fares.saveme.feature.notifications.domain.PushTapKeys

/**
 * The tap data of a notification, read from launcher extras that any app can set (FR-044): only the contract's keys, only
 * string values, and only when both `type` and `notification_id` are there. Background messages of the push service add
 * their own keys (`google.message_id` and others), which are ignored.
 */
object PushTapExtras {

    /** Null when [extras] is not a notification tap. */
    fun from(extras: Bundle?): Map<String, String>? {
        if (extras == null) return null
        val data = buildMap {
            PushTapKeys.all.forEach { key ->
                @Suppress("DEPRECATION")
                (extras.get(key) as? String)?.let { put(key, it) }
            }
        }
        return data.takeIf { it["type"] != null && it["notification_id"] != null }
    }

    /**
     * The host's whole intake of one intent: submit a tap if it is one, then strip its keys so a re-delivery cannot route
     * twice (FR-033). Returns whether a tap was submitted.
     */
    fun intake(intent: Intent?, submit: (Map<String, String>) -> Unit): Boolean {
        if (intent == null) return false
        val tap = from(intent.extras)
        tap?.let(submit)
        strip(intent)
        return tap != null
    }

    /** Puts [data] (already filtered to the contract keys) on a launch intent. */
    fun put(intent: Intent, data: Map<String, String>): Intent = intent.apply {
        PushTapKeys.filter(data).forEach { (key, value) -> putExtra(key, value) }
    }

    /** Removes the tap's keys so a re-delivery of the same intent cannot route twice (FR-033). */
    fun strip(intent: Intent) {
        PushTapKeys.all.forEach { intent.removeExtra(it) }
        intent.removeExtra("google.message_id")
    }
}

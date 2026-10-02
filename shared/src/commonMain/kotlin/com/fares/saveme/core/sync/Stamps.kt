package com.fares.saveme.core.sync

import kotlin.time.Instant

/**
 * A fixed-width UTC instant (millisecond precision), so stored times compare as strings in the
 * DAOs. `Instant.toString()` drops a zero fraction ("…:50Z" sorts after "…:50.1Z").
 */
internal fun Instant.syncStamp(): String {
    val text = Instant.fromEpochMilliseconds(toEpochMilliseconds()).toString()
    return if ('.' in text) text else text.removeSuffix("Z") + ".000Z"
}

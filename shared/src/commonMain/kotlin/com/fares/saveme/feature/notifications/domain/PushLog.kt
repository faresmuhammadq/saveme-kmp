package com.fares.saveme.feature.notifications.domain

import co.touchlab.kermit.Logger

/** One tag; lines carry an outcome or a message type only, never an address, id, title or text (FR-008, FR-038). */
internal val pushLog: Logger = Logger.withTag("push")

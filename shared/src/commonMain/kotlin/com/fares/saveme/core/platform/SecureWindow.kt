package com.fares.saveme.core.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the window out of screenshots, screen recordings and the recent-apps preview while this is
 * composed (spec 015 FR-029, P-09.2). Android sets `FLAG_SECURE`; iOS does nothing (P-15).
 */
@Composable
expect fun SecureWindow()

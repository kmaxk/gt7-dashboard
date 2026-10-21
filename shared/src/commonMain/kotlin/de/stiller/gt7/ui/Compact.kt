package de.stiller.gt7.ui

import androidx.compose.runtime.staticCompositionLocalOf

private const val COMPACT_WIDTH_DP = 840f
private const val COMPACT_HEIGHT_DP = 480f

/** Phones in landscape: narrow, or – large phones – low. The desktop app never asks. */
fun isCompact(widthDp: Float, heightDp: Float): Boolean = widthDp < COMPACT_WIDTH_DP || heightDp < COMPACT_HEIGHT_DP

/** On a phone the live tab uses the whole height; the tab row comes back on request. */
fun tabRowVisible(compact: Boolean, liveTab: Boolean, expanded: Boolean): Boolean = !compact || !liveTab || expanded

val LocalCompact = staticCompositionLocalOf { false }

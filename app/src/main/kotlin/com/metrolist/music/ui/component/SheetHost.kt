/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

val LocalSheetHost = compositionLocalOf { SheetHostState() }

/**
 * A sheet opened from a menu (e.g. Edit playlist) is shown here, on its own, after the menu
 * closes. Shown inside the menu instead, the menu stayed open underneath and moved with the
 * keyboard, so two sheets showed whenever the keyboard changed (title to description).
 */
@Stable
class SheetHostState {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    fun dismiss() {
        content = null
    }
}

@Composable
fun SheetHost(state: SheetHostState) {
    state.content?.invoke()
}

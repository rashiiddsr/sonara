/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.ui.theme.WindowLayout
import com.metrolist.music.ui.theme.rememberWindowLayout

/**
 * A page made of a header (cover, title, play and shuffle) and a track list. On phones and
 * portrait tablets the header is the list's first item, as it always was. With room for two
 * panes (a landscape tablet) the header stays put on the left and only the tracks scroll on
 * the right, so the cover and the play button never scroll away.
 *
 * [content] gets `headerInList`: whether the list itself should show the header.
 */
@Composable
fun DetailPane(
    header: (@Composable () -> Unit)?,
    content: @Composable (headerInList: Boolean) -> Unit,
) {
    val twoPane = rememberWindowLayout().twoPane
    if (!twoPane || header == null) {
        content(true)
        return
    }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(WindowLayout.SidePaneWidth)
                .fillMaxHeight()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState()),
        ) { header() }
        Box(Modifier.weight(1f)) { content(false) }
    }
}

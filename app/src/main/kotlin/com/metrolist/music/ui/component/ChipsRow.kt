/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.metrolist.music.ui.theme.Spacing

/** Horizontal row of [ErChip]s keyed by value; kept for screens that pass label pairs. */
@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") containerColor: Color = Color.Unspecified,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.S),
    ) {
        chips.forEach { (value, label) ->
            ErChip(label = label, selected = currentValue == value, onClick = { onValueUpdate(value) })
        }
    }
}

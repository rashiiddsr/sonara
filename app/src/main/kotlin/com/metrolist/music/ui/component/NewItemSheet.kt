/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Spacing

/** Library's + : "New" with Playlist and Import playlist, like YouTube Music's New sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewItemSheet(
    onDismiss: () -> Unit,
    onNewPlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ErShape.SheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.L)) {
            Text(
                stringResource(R.string.er_new),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.XL, vertical = Spacing.S),
            )
            NewRow(R.drawable.playlist_add, R.string.er_type_playlist, R.string.er_new_playlist_desc) {
                onDismiss()
                onNewPlaylist()
            }
            NewRow(R.drawable.link, R.string.er_import_playlist, R.string.er_import_playlist_desc) {
                onDismiss()
                onImportPlaylist()
            }
        }
    }
}

@Composable
private fun NewRow(
    icon: Int,
    title: Int,
    description: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.XL, vertical = Spacing.M),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(Spacing.L))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

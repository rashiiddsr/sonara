/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.utils.YouTubePlaylistEditor
import com.metrolist.music.utils.YouTubePlaylistEditor.CollaborationChange
import com.metrolist.music.utils.appScope
import kotlinx.coroutines.launch

/**
 * YouTube Music's Collaborate panel: collaboration on/off, whether new people may join, and the
 * invite link to share. Each switch sends the command the panel itself carries; it moves once
 * YouTube accepted it (a spinner meanwhile). Turning collaboration off asks first, since
 * collaborators lose access. [initial] is the state the editor already read, so it opens at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaborateSheet(
    playlistId: String,
    playlistTitle: String,
    onDismiss: () -> Unit,
    initial: YouTubePlaylistEditor.Collaboration? = null,
    onState: (YouTubePlaylistEditor.Collaboration?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(initial == null) }
    var busy by remember { mutableStateOf<CollaborationChange?>(null) }
    var confirmOff by remember { mutableStateOf(false) }

    // Quietly refreshes the link and the commands; the spinner only shows when nothing is known yet.
    fun reload() {
        scope.launch {
            YouTubePlaylistEditor.collaboration(playlistId, fresh = true)?.let {
                state = it
                onState(it)
            }
            loading = false
        }
    }

    fun apply(change: CollaborationChange) {
        val current = state ?: return
        busy = change
        appScope.launch {
            val accepted = YouTubePlaylistEditor.changeCollaboration(current, change)
            scope.launch {
                busy = null
                if (accepted != null) {
                    state = accepted
                    onState(accepted)
                    // A new invite link exists once new people may join; read it if YouTube's answer had none.
                    if (accepted.enabled && accepted.allowNew && accepted.inviteLink == null) reload()
                } else {
                    ErToast.show(context, R.string.er_playlist_save_failed)
                }
            }
        }
    }

    LaunchedEffect(playlistId) {
        if (initial == null) {
            // Shares the request the playlist page or editor may already have running.
            YouTubePlaylistEditor.collaboration(playlistId)?.let {
                state = it
                onState(it)
            }
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ErShape.SheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Spacing.XL, end = Spacing.S),
            ) {
                Text(
                    stringResource(R.string.er_collaborate),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.er_close))
                }
            }
            HorizontalDivider()
            val current = state
            if (current == null) {
                Box(Modifier.fillMaxWidth().padding(Spacing.XL), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator() else Text(stringResource(R.string.er_playlist_save_failed))
                }
            } else {
                SwitchRow(
                    title = stringResource(R.string.er_collaborate),
                    subtitle = stringResource(R.string.er_collaborate_desc),
                    checked = current.enabled,
                    enabled = busy == null,
                    working = busy == CollaborationChange.ENABLE || busy == CollaborationChange.DISABLE,
                    onChange = { on -> if (on) apply(CollaborationChange.ENABLE) else confirmOff = true },
                )
                SwitchRow(
                    title = stringResource(R.string.er_allow_new_collaborators),
                    subtitle = stringResource(R.string.er_allow_new_collaborators_desc),
                    checked = current.allowNew,
                    enabled = busy == null && current.enabled,
                    working = busy == CollaborationChange.ALLOW_NEW || busy == CollaborationChange.BLOCK_NEW,
                    onChange = { on -> apply(if (on) CollaborationChange.ALLOW_NEW else CollaborationChange.BLOCK_NEW) },
                )
                HorizontalDivider()
                // Always in place (greyed out until there is a link to share), so the sheet keeps
                // its height and the button is not pushed below the edge when it becomes usable.
                val link = current.inviteLink
                val canInvite = current.enabled && current.allowNew
                val shareText = link?.let { stringResource(R.string.er_invite_share_text, playlistTitle, it) }
                Button(
                    enabled = canInvite && shareText != null && busy == null,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    shape = ErShape.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XL, vertical = Spacing.L)
                        .heightIn(min = Size.IconButton),
                ) {
                    if (canInvite && link == null) {
                        CircularProgressIndicator(strokeWidth = Spacing.XXS, modifier = Modifier.size(Size.IconSmall))
                    } else {
                        Icon(painterResource(R.drawable.person_add), contentDescription = null)
                    }
                    Spacer(Modifier.width(Spacing.S))
                    Text(stringResource(R.string.er_invite_collaborators))
                }
            }
            Spacer(Modifier.padding(bottom = Spacing.L))
        }
    }

    if (confirmOff) {
        DefaultDialog(
            onDismiss = { confirmOff = false },
            title = { Text(stringResource(R.string.er_turn_off_collaboration)) },
            buttons = {
                TextButton(onClick = { confirmOff = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(
                    onClick = {
                        confirmOff = false
                        apply(CollaborationChange.DISABLE)
                    },
                ) { Text(stringResource(R.string.er_turn_off)) }
            },
        ) {
            Text(stringResource(R.string.er_turn_off_collaboration_desc), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    working: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val alpha = if (enabled || working) 1f else DISABLED_ALPHA
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XL, vertical = Spacing.M),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (working) {
            Box(Modifier.width(Size.IconButton), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = Spacing.XXS, modifier = Modifier.size(Size.IconSmall))
            }
        } else {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

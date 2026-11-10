/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey
import com.metrolist.music.playback.SleepTimer
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.delay

private val Presets = listOf(5, 10, 15, 30, 45, 60)

/** Sleep timer as a plain list of choices, the way music apps present it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    sleepTimer: SleepTimer,
    onDismiss: () -> Unit,
) {
    val stopAfterCurrent by rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val fadeOut by rememberPreference(SleepTimerFadeOutKey, false)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepTimer.isActive) {
        while (sleepTimer.isActive) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = ErShape.SheetTop, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.XL),
            )
            if (sleepTimer.isActive) {
                Text(
                    text = if (sleepTimer.triggerTime != -1L) {
                        stringResource(R.string.er_sleep_timer_remaining, formatTime(sleepTimer.triggerTime - now))
                    } else {
                        stringResource(R.string.end_of_song)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.XL),
                )
            }
            Spacer(Modifier.height(Spacing.S))
            Presets.forEach { minutes ->
                TimerRow(pluralStringResource(R.plurals.minute, minutes, minutes), selected = false) {
                    sleepTimer.start(minute = minutes, stopAfterCurrentSong = stopAfterCurrent, fadeOut = fadeOut)
                    onDismiss()
                }
            }
            TimerRow(stringResource(R.string.end_of_song), selected = sleepTimer.pauseWhenSongEnd) {
                sleepTimer.start(minute = -1)
                onDismiss()
            }
            if (sleepTimer.isActive) {
                TimerRow(stringResource(R.string.er_sleep_timer_cancel), selected = false) {
                    sleepTimer.clear()
                    onDismiss()
                }
            }
            Spacer(Modifier.height(Spacing.L))
        }
    }
}

@Composable
private fun TimerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.ListRow - Spacing.M)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.XL),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                painterResource(R.drawable.check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(Size.Icon),
            )
        }
    }
}

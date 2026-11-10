/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import com.metrolist.music.ui.theme.Motion
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErMusicTheme
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

/**
 * A settings section: optional title, then plain rows. No card or dividers, so lists read as
 * lists rather than stacks of boxes.
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>,
    @Suppress("UNUSED_PARAMETER") useLowContrast: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.S)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.XS, top = Spacing.L, bottom = Spacing.S),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            items.forEach { item ->
                // Rows that depend on a switch slide in and out instead of popping.
                AnimatedVisibility(
                    visible = item.visible,
                    enter = expandVertically(Motion.medium()) + fadeIn(Motion.medium()),
                    exit = shrinkVertically(Motion.fast()) + fadeOut(Motion.fast()),
                ) {
                    SettingsRow(item)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(item: Material3SettingsItem) {
    val disabledAlpha = 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.ListRow - Spacing.S)
            .clip(ErShape.Small)
            .clickable(enabled = item.enabled && item.onClick != null) { item.onClick?.invoke() }
            .padding(horizontal = Spacing.XS, vertical = Spacing.S),
    ) {
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(Modifier.width(Spacing.L))
        } else if (item.icon != null) {
            val tint =
                if (!item.enabled) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                } else if (item.isHighlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Size.Icon + Spacing.XS)) {
                if (item.showBadge) {
                    BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Size.Icon))
                    }
                } else {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Size.Icon))
                }
            }
            Spacer(Modifier.width(Spacing.L))
        }
        Column(Modifier.weight(1f)) {
            ProvideTextStyle(
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.enabled) 1f else disabledAlpha),
                ),
            ) { item.title() }
            item.description?.let { desc ->
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.enabled) 1f else disabledAlpha),
                    ),
                ) { desc() }
            }
        }
        item.trailingContent?.let {
            Spacer(Modifier.width(Spacing.S))
            it()
        }
    }
}

data class Material3SettingsItem(
    val icon: Painter? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val visible: Boolean = true,
)

@Preview(name = "Settings group light")
@Composable
private fun SettingsGroupPreviewLight() {
    ErMusicTheme(darkTheme = false) { SettingsGroupPreviewContent() }
}

@Preview(name = "Settings group dark")
@Composable
private fun SettingsGroupPreviewDark() {
    ErMusicTheme(darkTheme = true) { SettingsGroupPreviewContent() }
}

@Composable
private fun SettingsGroupPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.L)) {
        Material3SettingsGroup(
            title = "Playback",
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text("Volume normalization") },
                    description = { Text("Keep loudness consistent") },
                ),
                Material3SettingsItem(icon = painterResource(R.drawable.bedtime), title = { Text("Sleep timer") }),
            ),
        )
    }
}

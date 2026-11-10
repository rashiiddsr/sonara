/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErMusicTheme
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

private val ButtonPadding = PaddingValues(horizontal = Spacing.XL, vertical = Spacing.M)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ErShape.Pill,
        contentPadding = ButtonPadding,
        modifier = modifier,
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = ErShape.Pill,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun ButtonContent(
    text: String,
    @DrawableRes icon: Int?,
) {
    if (icon != null) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Size.IconSmall))
        Spacer(Modifier.width(Spacing.S))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

/** Section title with an optional "more" action, used above carousels and lists. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.Gutter, end = Spacing.S, top = Spacing.XL, bottom = Spacing.S),
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onAction != null) {
            Text(
                text = actionLabel ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(ErShape.Pill)
                    .clickable(onClick = onAction)
                    .padding(horizontal = Spacing.S, vertical = Spacing.XS),
            )
        }
    }
}

@Immutable
data class ChipOption<T>(
    val value: T,
    val label: String,
)

/** Selectable pill chip. Selected chips take the accent; a white pill glares on the dark base. */
@Composable
fun ErChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(Size.ChipHeight)
            .clip(ErShape.Pill)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.L),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun <T> ErChipRow(
    options: List<ChipOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.S),
    ) {
        options.forEach { option ->
            ErChip(
                label = option.label,
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

@Preview(name = "Controls light")
@Composable
private fun ControlsPreviewLight() {
    ErMusicTheme(darkTheme = false) { ControlsPreviewContent() }
}

@Preview(name = "Controls dark")
@Composable
private fun ControlsPreviewDark() {
    ErMusicTheme(darkTheme = true) { ControlsPreviewContent() }
}

@Composable
private fun ControlsPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background).padding(bottom = Spacing.L)) {
        SectionHeader(title = "Quick picks", subtitle = "Start radio", actionLabel = "More", onAction = {})
        ErChipRow(
            options = listOf(ChipOption(0, "Songs"), ChipOption(1, "Albums"), ChipOption(2, "Downloads")),
            selected = 0,
            onSelect = {},
        )
        Row(Modifier.padding(Spacing.Gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PrimaryButton(text = "Play", icon = R.drawable.play, onClick = {})
            SecondaryButton(text = "Shuffle", icon = R.drawable.shuffle, onClick = {})
        }
    }
}

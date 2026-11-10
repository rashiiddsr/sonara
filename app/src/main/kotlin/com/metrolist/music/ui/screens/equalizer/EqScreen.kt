/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.equalizer

import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.ChipOption
import com.metrolist.music.ui.component.ErChipRow
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import kotlin.math.roundToInt

private val SliderHeight = 200.dp
private val SliderWidth = 28.dp
private const val CUSTOM_CHIP = "custom"

private fun presetLabel(preset: EqPreset) =
    when (preset) {
        EqPreset.BASS -> R.string.er_eq_bass
        EqPreset.BASS_REDUCER -> R.string.er_eq_bass_reducer
        EqPreset.TREBLE -> R.string.er_eq_treble
        EqPreset.VOCAL -> R.string.er_eq_vocal
        EqPreset.POP -> R.string.er_eq_pop
        EqPreset.ROCK -> R.string.er_eq_rock
        EqPreset.ELECTRONIC -> R.string.er_eq_electronic
        EqPreset.HIP_HOP -> R.string.er_eq_hip_hop
        EqPreset.JAZZ -> R.string.er_eq_jazz
        EqPreset.CLASSICAL -> R.string.er_eq_classical
        EqPreset.ACOUSTIC -> R.string.er_eq_acoustic
    }

private fun bandLabel(hz: Double) = if (hz >= 1000) "${(hz / 1000).roundToInt()}k" else hz.roundToInt().toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(viewModel: EQViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val options =
        listOf(ChipOption(CUSTOM_CHIP, stringResource(R.string.er_eq_custom))) +
            EqPreset.entries.map { ChipOption(it.name, stringResource(presetLabel(it))) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(Size.TopBar))

        Box(Modifier.padding(horizontal = Spacing.Gutter)) {
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.er_equalizer)) },
                        trailingContent = { Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled) },
                        onClick = { viewModel.setEnabled(!state.enabled) },
                    ),
                ),
            )
        }

        ErChipRow(
            options = options,
            selected = if (state.enabled) state.preset?.name ?: CUSTOM_CHIP else null,
            onSelect = { key -> if (key == CUSTOM_CHIP) viewModel.selectCustom() else viewModel.selectPreset(EqPreset.valueOf(key)) },
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter, vertical = Spacing.L)
                .alpha(if (state.enabled) 1f else 0.4f),
        ) {
            EqBands.forEachIndexed { index, hz ->
                val gain = state.gains[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (gain > 0) "+${gain.roundToInt()}" else gain.roundToInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(SliderWidth),
                    )
                    Spacer(Modifier.height(Spacing.XS))
                    GainSlider(
                        gain = gain,
                        onChange = { viewModel.setBand(index, it) },
                        onChangeFinished = viewModel::commitBand,
                    )
                    Spacer(Modifier.height(Spacing.XS))
                    Text(
                        text = bandLabel(hz),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(SliderWidth),
                    )
                }
            }
        }

        if (state.enabled && state.preset == null) {
            TextButton(
                onClick = viewModel::reset,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.er_eq_reset))
            }
        }

    }

    TopAppBar(
        colors = backdropTopBarColors(),
        modifier = Modifier.topBarBackdrop(),
        title = { Text(stringResource(R.string.er_equalizer)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.er_back))
            }
        },
    )
}

/** Vertical -12..+12 dB slider, filled from the 0 dB line towards the thumb. */
@Composable
private fun GainSlider(
    gain: Float,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnFinished by rememberUpdatedState(onChangeFinished)
    fun gainAt(y: Float, height: Float) = (((1f - y / height) * 2f - 1f) * EQ_MAX_GAIN).coerceIn(-EQ_MAX_GAIN, EQ_MAX_GAIN).roundToInt().toFloat()

    Box(
        Modifier
            .width(SliderWidth)
            .height(SliderHeight)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnChange(gainAt(offset.y, size.height.toFloat()))
                    currentOnFinished()
                }
            }.pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { currentOnFinished() },
                    onDragCancel = { currentOnFinished() },
                ) { change, _ ->
                    currentOnChange(gainAt(change.position.y, size.height.toFloat()))
                }
            }.drawBehind {
                val x = size.width / 2
                val track = Size.SliderTrack.toPx()
                val zero = size.height / 2
                val thumbY = zero - (gain / EQ_MAX_GAIN) * (size.height / 2 - track)
                drawLine(colors.surfaceContainerHighest, Offset(x, track), Offset(x, size.height - track), track, StrokeCap.Round)
                drawLine(colors.primary, Offset(x, zero), Offset(x, thumbY), track, StrokeCap.Round)
                drawCircle(colors.onSurface, Size.SliderThumbActive.toPx() / 2, Offset(x, thumbY))
            },
    )
}

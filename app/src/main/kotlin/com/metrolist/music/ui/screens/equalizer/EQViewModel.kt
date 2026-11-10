/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.eq.EqualizerService
import com.metrolist.music.eq.data.EQProfileRepository
import com.metrolist.music.eq.data.ParametricEQBand
import com.metrolist.music.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The ten octave bands every preset and the custom curve are written on. */
val EqBands = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
const val EQ_MAX_GAIN = 12f

enum class EqPreset(val gains: List<Float>) {
    BASS(listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    BASS_REDUCER(listOf(-6f, -5f, -4f, -2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    TREBLE(listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)),
    VOCAL(listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
    POP(listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f)),
    ROCK(listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f)),
    ELECTRONIC(listOf(5f, 4f, 2f, 0f, -2f, 0f, 1f, 3f, 4f, 5f)),
    HIP_HOP(listOf(5f, 4f, 2f, 3f, -1f, -1f, 1f, 0f, 2f, 3f)),
    JAZZ(listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    CLASSICAL(listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
    ACOUSTIC(listOf(4f, 3f, 2f, 1f, 1f, 1f, 2f, 3f, 3f, 2f)),
    ;

    val profileId get() = "preset_${name.lowercase()}"
}

private const val CUSTOM_ID = "preset_custom"

data class EqUiState(
    val enabled: Boolean = false,
    /** null while the custom curve is selected. */
    val preset: EqPreset? = EqPreset.BASS,
    val gains: List<Float> = EqPreset.BASS.gains,
)

/**
 * Fixed presets plus one custom curve. The active profile lives in [EQProfileRepository], which
 * MusicService observes and applies, so the choice survives restarts.
 */
@HiltViewModel
class EQViewModel @Inject constructor(
    private val repository: EQProfileRepository,
    private val equalizerService: EqualizerService,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<EqUiState> = _state.asStateFlow()

    private fun initialState(): EqUiState {
        val active = repository.activeProfile.value
        val custom = repository.profiles.value.find { it.id == CUSTOM_ID }?.let(::gainsOf)
        val preset = EqPreset.entries.find { it.profileId == active?.id }
        return when {
            preset != null -> EqUiState(enabled = true, preset = preset, gains = preset.gains)
            active?.id == CUSTOM_ID -> EqUiState(enabled = true, preset = null, gains = gainsOf(active))
            custom != null -> EqUiState(enabled = false, preset = null, gains = custom)
            else -> EqUiState()
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        persist()
    }

    fun selectPreset(preset: EqPreset) {
        _state.update { it.copy(enabled = true, preset = preset, gains = preset.gains) }
        persist()
    }

    fun selectCustom() {
        val saved = repository.profiles.value.find { it.id == CUSTOM_ID }?.let(::gainsOf)
        _state.update { it.copy(enabled = true, preset = null, gains = saved ?: it.gains) }
        persist()
    }

    /** Live while dragging: applied straight to the audio processor, saved on [commitBand]. */
    fun setBand(index: Int, gain: Float) {
        _state.update { s -> s.copy(enabled = true, preset = null, gains = s.gains.toMutableList().also { it[index] = gain }) }
        equalizerService.applyProfile(profileOf(CUSTOM_ID, _state.value.gains))
    }

    fun commitBand() = persist()

    fun reset() {
        _state.update { it.copy(enabled = true, preset = null, gains = List(EqBands.size) { 0f }) }
        persist()
    }

    private fun persist() {
        val s = _state.value
        viewModelScope.launch {
            if (!s.enabled) {
                repository.setActiveProfile(null)
                return@launch
            }
            val id = s.preset?.profileId ?: CUSTOM_ID
            repository.saveProfile(profileOf(id, s.gains))
            repository.setActiveProfile(id)
        }
    }

    private fun profileOf(id: String, gains: List<Float>) =
        SavedEQProfile(
            id = id,
            name = id,
            deviceModel = "",
            bands = EqBands.zip(gains) { f, g -> ParametricEQBand(frequency = f, gain = g.toDouble(), q = 1.41) },
            // Leave headroom for the loudest boost so the curve never clips.
            preamp = -(gains.maxOrNull() ?: 0f).coerceAtLeast(0f).toDouble(),
            isCustom = false,
        )

    private fun gainsOf(profile: SavedEQProfile): List<Float> =
        EqBands.map { f -> profile.bands.find { it.frequency == f }?.gain?.toFloat() ?: 0f }
}

/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ermusic.desktop.data.Downloads
import com.ermusic.desktop.data.Pref
import com.ermusic.desktop.data.Settings
import com.ermusic.desktop.playback.Equalizer
import com.ermusic.desktop.playback.LyricsTranslator
import com.ermusic.desktop.playback.SongCache
import com.ermusic.desktop.Os
import com.ermusic.desktop.window.StartAtLogin
import kotlin.math.roundToInt

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = Spacing.XL)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.S, bottom = Spacing.S))
        Column(Modifier.fillMaxWidth().clip(ErShape.Medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(Spacing.M)) { content() }
    }
}

@Composable
private fun SettingRow(title: String, description: String?, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)) {
        Column(Modifier.weight(1f).padding(end = Spacing.L)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing()
    }
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.handCursor(),
    )
}

@Composable
private fun SwitchRow(title: String, description: String?, pref: Pref<Boolean>) {
    SettingRow(title, description) { Toggle(pref.value) { pref.value = it } }
}

@Composable
private fun <T> ChoiceRow(title: String, description: String?, options: List<Pair<T, String>>, selected: T, menuModifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, description) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(ErShape.Small).clickable { open = true }.handCursor().padding(Spacing.S)) {
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(icon("expand_more"), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Size.IconSmall))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = ErShape.Medium, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = menuModifier) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        onSelect(value)
                        open = false
                    }, modifier = Modifier.handCursor())
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(app: AppState) {
    var cacheBytes by remember { mutableLongStateOf(SongCache.sizeBytes()) }
    var startsAtLogin by remember { mutableStateOf(StartAtLogin.enabled()) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ScrollingColumn(Modifier.widthIn(max = Layout.ReadingMaxWidth).fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.L, vertical = Spacing.L)) {
            item(key = "title", contentType = "title") { Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = Spacing.S, bottom = Spacing.L)) }
            item(key = "look", contentType = "group") {
                Group("Appearance") {
                    ChoiceRow("Theme", null, listOf("dark" to "Dark", "light" to "Light", "system" to "Same as ${Os.name}"), Settings.theme.value) { Settings.theme.value = it }
                    SwitchRow("Open the song panel when playing", "Shows the cover, the artist and what's next on the right.", Settings.openNowPlaying)
                }
            }
            item(key = "playback", contentType = "group") {
                Group("Playback") {
                    SwitchRow("High quality audio", "Uses more data per song.", Settings.highQuality)
                    SwitchRow("Normalize volume", "Turns loud songs down so they match the rest.", Settings.normalize)
                    SwitchRow("Mono audio", "Plays the same sound in both ears.", Settings.mono)
                    SwitchRow("Autoplay", "When the queue ends, carries on with similar songs.", Settings.autoplay)
                    val seconds = Settings.crossfadeSeconds.value
                    SettingRow("Crossfade", if (seconds == 0) "Off" else "$seconds seconds between songs") {
                        ThinSlider(value = seconds.toFloat() / MAX_CROSSFADE, onChange = { Settings.crossfadeSeconds.value = (it * MAX_CROSSFADE).roundToInt() }, modifier = Modifier.width(CROSSFADE_WIDTH))
                    }
                }
            }
            item(key = "equalizer", contentType = "group") {
                Group("Equalizer") {
                    SwitchRow("Equalizer", null, Settings.eqEnabled)
                    if (Settings.eqEnabled.value) EqualizerControls()
                }
            }
            item(key = "content", contentType = "group") {
                Group("Content") {
                    SwitchRow("Hide explicit content", "Leaves out songs and albums marked explicit.", Settings.hideExplicit)
                    SwitchRow("Hide music videos", "Shows songs only.", Settings.hideVideos)
                }
            }
            item(key = "lyrics", contentType = "group") {
                Group("Lyrics") {
                    SwitchRow("AI translator", "Translates lyrics as soon as they load.", Settings.translator)
                    if (Settings.translator.value) {
                        ChoiceRow("Translate into", null, LyricsTranslator.languages, Settings.translateLanguage.value, Modifier.height(MENU_HEIGHT)) { Settings.translateLanguage.value = it }
                    }
                }
            }
            item(key = "app", contentType = "group") {
                Group(Os.name) {
                    if (Os.isMac) {
                        SwitchRow("Keep playing in the menu bar", "Closing the window leaves Sonara in the menu bar.", Settings.closeToTray)
                    } else {
                        SwitchRow("Keep playing in the tray", "Closing the window leaves Sonara in the taskbar corner.", Settings.closeToTray)
                    }
                    if (StartAtLogin.available) {
                        SettingRow(StartAtLogin.label, null) {
                            Toggle(startsAtLogin) {
                                StartAtLogin.set(it)
                                startsAtLogin = StartAtLogin.enabled()
                            }
                        }
                    }
                }
            }
            item(key = "storage", contentType = "group") {
                Group("Storage") {
                    SettingRow("Song cache", "%.0f MB. Songs you downloaded are kept.".format(cacheBytes / BYTES_PER_MB)) {
                        OutlinedButton(onClick = {
                            SongCache.clear()
                            cacheBytes = SongCache.sizeBytes()
                            app.showToast("Cache cleared")
                        }, modifier = Modifier.handCursor()) { Text("Clear") }
                    }
                    SettingRow("Downloads", songCount(Downloads.tracks.size)) {
                        OutlinedButton(onClick = { Downloads.tracks.toList().forEach { Downloads.remove(it.id) } }, enabled = Downloads.tracks.isNotEmpty(), modifier = Modifier.handCursor()) { Text("Remove all") }
                    }
                }
            }
            item(key = "account", contentType = "group") {
                Group("Account") {
                    SettingRow(app.profile?.name ?: "Google account", app.profile?.email) {
                        OutlinedButton(onClick = app.onSignOut, modifier = Modifier.handCursor()) { Text("Sign out") }
                    }
                }
            }
            item(key = "about", contentType = "group") {
                Group("About") {
                    Text("Sonara 2.3.5 for ${Os.name}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.XS))
                    Text("Free software under GPL-3.0, derived from Metrolist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EqualizerControls() {
    val gains = Equalizer.parse(Settings.eqGains.value)
    val preset = Equalizer.PRESETS.entries.firstOrNull { (_, values) -> values.map { it.toFloat() } == gains }?.key
    ChoiceRow("Preset", null, Equalizer.PRESETS.keys.map { it to it } + listOfNotNull(("Custom" to "Custom").takeIf { preset == null }), preset ?: "Custom") { name ->
        Equalizer.PRESETS[name]?.let { values -> Settings.eqGains.value = Equalizer.encode(values.map { it.toFloat() }) }
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)) {
        Equalizer.LABELS.forEachIndexed { band, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VerticalSlider((gains[band] + Equalizer.MAX_GAIN_DB) / (2 * Equalizer.MAX_GAIN_DB), EQ_SLIDER_HEIGHT) { level ->
                    val value = (level * 2 * Equalizer.MAX_GAIN_DB - Equalizer.MAX_GAIN_DB).roundToInt().toFloat()
                    val current = Equalizer.parse(Settings.eqGains.value)
                    Settings.eqGains.value = Equalizer.encode(current.mapIndexed { index, old -> if (index == band) value else old })
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val EQ_SLIDER_HEIGHT = 140.dp
private const val BYTES_PER_MB = 1_048_576.0
private const val MAX_CROSSFADE = 12
private val CROSSFADE_WIDTH = 160.dp
private val MENU_HEIGHT = 320.dp

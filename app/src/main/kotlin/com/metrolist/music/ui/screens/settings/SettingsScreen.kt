/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.MonoAudioKey
import com.metrolist.music.constants.AccountImageUrlKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.AudioNormalizationKey
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.constants.AudioQualityKey
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.constants.CrossfadeDurationKey
import com.metrolist.music.constants.CrossfadeEnabledKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DownloadQualityKey
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.LyricsTranslateKey
import com.metrolist.music.constants.LyricsTranslateLanguageKey
import com.metrolist.music.constants.WifiOnlyDownloadsKey
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.lyrics.LyricsTranslator
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.ui.component.ErToast
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.theme.WindowLayout
import com.metrolist.music.ui.theme.backdropTopBarColors
import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.utils.openLinkSettings
import com.metrolist.music.utils.opensYouTubeMusicLinks
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All settings on one page, Spotify-style: short labels, values on the right, no essays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val accountImageUrl by rememberPreference(AccountImageUrlKey, "")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val isLoggedIn = "SAPISID" in parseCookieString(innerTubeCookie)

    val (crossfade, onCrossfade) = rememberPreference(CrossfadeEnabledKey, false)
    val (crossfadeSeconds, onCrossfadeSeconds) = rememberPreference(CrossfadeDurationKey, 5f)
    val (normalize, onNormalize) = rememberPreference(AudioNormalizationKey, true)
    val (mono, onMono) = rememberPreference(MonoAudioKey, false)
    val (autoplay, onAutoplay) = rememberPreference(AutoRadioQueueKey, true)
    val (hideExplicit, onHideExplicit) = rememberPreference(HideExplicitKey, false)
    val (streamQuality, onStreamQuality) = rememberEnumPreference(AudioQualityKey, AudioQuality.AUTO)
    val (downloadQuality, onDownloadQuality) = rememberEnumPreference(DownloadQualityKey, AudioQuality.AUTO)
    val (wifiOnly, onWifiOnly) = rememberPreference(WifiOnlyDownloadsKey, true)
    val (darkMode, onDarkMode) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val themeMenu = remember { mutableStateOf(false) }
    val (aiTranslate, onAiTranslate) = rememberPreference(LyricsTranslateKey, false)
    val (translateTo, onTranslateTo) = rememberPreference(LyricsTranslateLanguageKey, LyricsTranslator.DEFAULT_LANGUAGE)
    val translateMenu = remember { mutableStateOf(false) }

    val playerCache = LocalPlayerConnection.current?.service?.playerCache
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache
    val imageCache = context.imageLoader.diskCache
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var downloadBytes by remember { mutableLongStateOf(0L) }
    var refreshSizes by remember { mutableStateOf(0) }
    LaunchedEffect(refreshSizes, playerCache, downloadCache) {
        withContext(Dispatchers.IO) {
            cacheBytes = (tryOrNull { playerCache?.cacheSpace } ?: 0L) + (imageCache?.size ?: 0L)
            downloadBytes = tryOrNull { downloadCache?.cacheSpace } ?: 0L
        }
    }
    val streamMenu = remember { mutableStateOf(false) }
    val downloadMenu = remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmRemoveDownloads by remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            // A reading column on tablets, centred, instead of rows across the whole screen.
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = WindowLayout.ReadingMaxWidth)
            .padding(horizontal = Spacing.Gutter),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    leadingContent =
                        accountImageUrl.takeIf { isLoggedIn && it.isNotBlank() }?.let { url ->
                            {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.size(Size.Avatar).clip(CircleShape),
                                )
                            }
                        },
                    icon = painterResource(R.drawable.account),
                    title = { Text(if (isLoggedIn) accountName.ifBlank { stringResource(R.string.account) } else stringResource(R.string.login)) },
                    description = accountEmail.takeIf { isLoggedIn && it.isNotBlank() }?.let { email -> { Text(email) } },
                    onClick = { navController.navigate(if (isLoggedIn) "settings/account" else "login") },
                ),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.er_settings_appearance),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.contrast),
                    title = { Text(stringResource(R.string.er_theme)) },
                    trailingContent = {
                        Box {
                            ValueText(stringResource(themeLabel(darkMode)))
                            DropdownMenu(expanded = themeMenu.value, onDismissRequest = { themeMenu.value = false }) {
                                listOf(DarkMode.AUTO, DarkMode.ON, DarkMode.OFF).forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(themeLabel(mode))) },
                                        onClick = {
                                            onDarkMode(mode)
                                            themeMenu.value = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    onClick = { themeMenu.value = true },
                ),
            ),
        )

        // AI translator: off by default; once on, every song's lyrics get a translation under
        // each line, in the language chosen below (English unless changed).
        Material3SettingsGroup(
            title = stringResource(R.string.er_settings_lyrics),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.er_ai_translator)) },
                    description = { Text(stringResource(R.string.er_ai_translator_desc)) },
                    trailingContent = { Switch(checked = aiTranslate, onCheckedChange = onAiTranslate) },
                    onClick = { onAiTranslate(!aiTranslate) },
                ),
                Material3SettingsItem(
                    visible = aiTranslate,
                    title = { Text(stringResource(R.string.er_translate_to)) },
                    trailingContent = {
                        Box {
                            ValueText(LyricsTranslator.languageName(translateTo))
                            DropdownMenu(expanded = translateMenu.value, onDismissRequest = { translateMenu.value = false }) {
                                LyricsTranslator.languages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            onTranslateTo(code)
                                            translateMenu.value = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    onClick = { translateMenu.value = true },
                ),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.er_settings_playback),
            items = buildList {
                add(switchItem(R.string.er_crossfade, crossfade, onCrossfade))
                add(
                    Material3SettingsItem(
                        visible = crossfade,
                        title = {
                            // Dragging moves a local value; it is saved once, on release.
                            var seconds by remember(crossfadeSeconds) { mutableFloatStateOf(crossfadeSeconds) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(
                                    value = seconds,
                                    onValueChange = { seconds = it },
                                    onValueChangeFinished = { onCrossfadeSeconds(seconds) },
                                    valueRange = 1f..12f,
                                    steps = 10,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.er_seconds, seconds.roundToInt()),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(Size.IconButton),
                                )
                            }
                        },
                    ),
                )
                add(switchItem(R.string.er_normalize_volume, normalize, onNormalize))
                add(switchItem(R.string.er_mono_audio, mono, onMono))
                add(switchItem(R.string.er_autoplay, autoplay, onAutoplay))
                add(switchItem(R.string.er_hide_explicit, hideExplicit, onHideExplicit))
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.er_equalizer)) },
                        onClick = { navController.navigate("equalizer") },
                    ),
                )
            },
        )

        Material3SettingsGroup(
            title = stringResource(R.string.er_audio_quality),
            items = listOf(
                qualityItem(R.string.er_quality_streaming, streamQuality, onStreamQuality, streamMenu),
                qualityItem(R.string.er_quality_download, downloadQuality, onDownloadQuality, downloadMenu),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.er_storage),
            items = listOf(
                switchItem(R.string.er_download_mobile_data, !wifiOnly) { onWifiOnly(!it) },
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.er_clear_cache)) },
                    trailingContent = { ValueText(Formatter.formatShortFileSize(context, cacheBytes)) },
                    onClick = { confirmClearCache = true },
                ),
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.er_remove_downloads)) },
                    trailingContent = { ValueText(Formatter.formatShortFileSize(context, downloadBytes)) },
                    enabled = downloadBytes > 0,
                    onClick = { confirmRemoveDownloads = true },
                ),
            ),
        )

        // Android gives music.youtube.com links to YouTube Music (it owns the domain); only the
        // phone's settings can hand them to SONARA. The switch shows where links open now and
        // goes straight to that settings page; it is read again on return.
        var linksHere by remember { mutableStateOf(opensYouTubeMusicLinks(context)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { linksHere = opensYouTubeMusicLinks(context) }
        val openLinks = {
            if (!openLinkSettings(context)) ErToast.show(context, R.string.er_open_links_failed)
        }
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = { Text(stringResource(R.string.er_open_links)) },
                    trailingContent = { Switch(checked = linksHere, onCheckedChange = { openLinks() }) },
                    onClick = openLinks,
                ),
            ),
        )

        Material3SettingsGroup(
            items = buildList {
                add(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.er_settings_about)) },
                        trailingContent = { ValueText(BuildConfig.VERSION_NAME) },
                        onClick = { navController.navigate("settings/about") },
                    ),
                )
            },
        )
        Spacer(Modifier.height(Spacing.XL))
    }

    TopAppBar(
        colors = backdropTopBarColors(),
        modifier = Modifier.topBarBackdrop(),
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.er_back))
            }
        },
    )

    if (confirmClearCache) {
        ConfirmDialog(
            title = stringResource(R.string.er_clear_cache_title),
            message = stringResource(R.string.er_clear_cache_message),
            confirm = stringResource(R.string.er_clear_cache),
            onDismiss = { confirmClearCache = false },
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    playerCache?.keys?.toList()?.forEach { tryOrNull { playerCache.removeResource(it) } }
                    imageCache?.clear()
                    refreshSizes++
                }
            },
        )
    }
    if (confirmRemoveDownloads) {
        ConfirmDialog(
            title = stringResource(R.string.er_remove_downloads_title),
            message = stringResource(R.string.er_remove_downloads_message),
            confirm = stringResource(R.string.er_remove_downloads),
            onDismiss = { confirmRemoveDownloads = false },
            onConfirm = {
                DownloadService.sendRemoveAllDownloads(context, ExoDownloadService::class.java, false)
                refreshSizes++
            },
        )
    }
}

@Composable
private fun ValueText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun switchItem(
    label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = Material3SettingsItem(
    title = { Text(stringResource(label)) },
    trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    onClick = { onChange(!checked) },
)

/** Sonar Night is the dark theme, Sonar Day the light one. */
private fun themeLabel(mode: DarkMode) =
    when (mode) {
        DarkMode.AUTO -> R.string.er_theme_system
        DarkMode.ON -> R.string.er_theme_dark
        DarkMode.OFF -> R.string.er_theme_light
    }

private fun qualityLabel(quality: AudioQuality) =
    when (quality) {
        AudioQuality.AUTO -> R.string.er_quality_auto
        AudioQuality.LOW -> R.string.er_quality_low
        AudioQuality.HIGH -> R.string.er_quality_high
    }

private fun qualityItem(
    label: Int,
    value: AudioQuality,
    onChange: (AudioQuality) -> Unit,
    expanded: MutableState<Boolean>,
): Material3SettingsItem =
    Material3SettingsItem(
        title = { Text(stringResource(label)) },
        trailingContent = {
            Box {
                ValueText(stringResource(qualityLabel(value)))
                DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                    AudioQuality.entries.forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(stringResource(qualityLabel(quality))) },
                            onClick = {
                                onChange(quality)
                                expanded.value = false
                            },
                        )
                    }
                }
            }
        },
        onClick = { expanded.value = true },
    )

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

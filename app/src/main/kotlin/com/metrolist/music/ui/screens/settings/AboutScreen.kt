/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

private data class Upstream(
    val name: String,
    val url: String,
)

private val Lineage =
    listOf(
        Upstream("Metrolist", "https://github.com/MetrolistGroup/Metrolist"),
    )

private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private data class Contact(
    val label: String,
    val icon: Int,
    val url: String,
)

private const val CREATOR = "Rashid"
private const val CREATOR_HANDLE = "rashiiddsr"

/** Each opens straight in its app (GitHub, mail, Instagram, WhatsApp chat). */
private val Contacts =
    listOf(
        Contact("GitHub", R.drawable.github, "https://github.com/$CREATOR_HANDLE"),
        Contact("Email", R.drawable.mail, "mailto:rashiiddsr@gmail.com"),
        Contact("Instagram", R.drawable.instagram, "https://instagram.com/$CREATOR_HANDLE"),
        // 0851... in international form for wa.me.
        Contact("WhatsApp", R.drawable.whatsapp, "https://wa.me/6285174388234"),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.Gutter),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.XL),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(Size.CardThumbSmall - Spacing.XXL)
                    .clip(ErShape.Large)
                    .background(colorResource(R.color.ic_launcher_background)),
            )
            Spacer(Modifier.height(Spacing.M))
            Text(stringResource(R.string.er_brand), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.er_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.XS))
            Text(
                BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // One card rather than four rows, so About stays as compact as License and Based on.
        Text(
            stringResource(R.string.er_about_creator),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.XS, top = Spacing.L, bottom = Spacing.S),
        )
        Surface(
            shape = ErShape.Large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S),
        ) {
            Column(Modifier.padding(Spacing.L)) {
                Text(CREATOR, style = MaterialTheme.typography.titleLarge)
                Text(
                    "@$CREATOR_HANDLE",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.L))
                Row(Modifier.fillMaxWidth()) {
                    Contacts.forEach { contact ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(ErShape.Medium)
                                .clickable { uriHandler.openUri(contact.url) }
                                .padding(vertical = Spacing.S),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(Size.IconButton)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                            ) {
                                Icon(
                                    painterResource(contact.icon),
                                    contentDescription = contact.label,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(Size.IconSmall),
                                )
                            }
                            Spacer(Modifier.height(Spacing.XS))
                            Text(contact.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Material3SettingsGroup(
            title = stringResource(R.string.er_about_license),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.info),
                    title = { Text("GNU General Public License v3.0") },
                    onClick = { uriHandler.openUri(GPL_URL) },
                ),
            ),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.er_about_based_on),
            items = Lineage.map { upstream ->
                Material3SettingsItem(
                    icon = painterResource(R.drawable.link),
                    title = { Text(upstream.name) },
                    description = { Text(upstream.url.removePrefix("https://")) },
                    onClick = { uriHandler.openUri(upstream.url) },
                )
            },
        )

        Text(
            text = stringResource(R.string.er_about_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.L, horizontal = Spacing.XS),
        )
    }

    TopAppBar(
        colors = backdropTopBarColors(),
        modifier = Modifier.topBarBackdrop(),
        title = { Text(stringResource(R.string.er_settings_about)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.er_back))
            }
        },
    )
}

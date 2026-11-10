/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import androidx.compose.ui.platform.LocalUriHandler
import com.metrolist.music.ui.theme.Size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import coil3.compose.AsyncImage
import com.metrolist.music.constants.AccountImageUrlKey
import com.metrolist.music.LocalPlayerConnection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import kotlinx.coroutines.launch

/** Signed-in account: identity, library sync toggle, manual sync, log out (RULE-05). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManageScreen(
    navController: NavController,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val accountHandle by rememberPreference(AccountChannelHandleKey, "")
    val accountImageUrl by rememberPreference(AccountImageUrlKey, "")
    val uriHandler = LocalUriHandler.current
    var confirmLogout by remember { mutableStateOf(false) }
    val playerConnection = LocalPlayerConnection.current

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
            if (accountImageUrl.isNotBlank()) {
                AsyncImage(
                    model = accountImageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(Size.CardThumbSmall - Spacing.XXL).clip(CircleShape),
                )
            } else {
                Icon(
                    painterResource(R.drawable.account),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Size.CardThumbSmall - Spacing.XXL),
                )
            }
            Spacer(Modifier.height(Spacing.M))
            Text(accountName.ifBlank { stringResource(R.string.account) }, style = MaterialTheme.typography.titleLarge)
        }
        Material3SettingsGroup(
            items = buildList {
                if (accountEmail.isNotBlank()) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.account),
                            title = { Text(stringResource(R.string.er_email)) },
                            trailingContent = { Text(accountEmail, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        ),
                    )
                }
                if (accountHandle.isNotBlank()) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.person),
                            title = { Text(stringResource(R.string.er_channel)) },
                            trailingContent = { Text(accountHandle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        ),
                    )
                }
            },
        )
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.logout),
                    title = { Text(stringResource(R.string.er_log_out), color = MaterialTheme.colorScheme.error) },
                    onClick = { confirmLogout = true },
                ),
            ),
        )
        Spacer(Modifier.height(Spacing.XL))
    }

    TopAppBar(
        colors = backdropTopBarColors(),
        modifier = Modifier.topBarBackdrop(),
        title = { Text(stringResource(R.string.account)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.er_back))
            }
        },
    )

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.er_log_out)) },
            text = { Text(stringResource(R.string.er_log_out_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        // Signing out ends the session: nothing keeps playing behind the sign-in page.
                        playerConnection?.player?.run {
                            stop()
                            clearMediaItems()
                        }
                        scope.launch {
                            viewModel.logout(context)
                            navController.navigateUp()
                        }
                    },
                ) { Text(stringResource(R.string.er_log_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

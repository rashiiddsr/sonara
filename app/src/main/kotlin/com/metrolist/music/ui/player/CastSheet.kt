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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.metrolist.music.R
import com.metrolist.music.playback.SonaraCast
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

/** Cast icon for the player bar; hidden when Google Play services has no Cast support. */
@Composable
fun CastButton() {
    val context = LocalContext.current
    val available = remember { SonaraCast.context(context) != null }
    if (!available) return
    val state by SonaraCast.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    IconButton(onClick = { showSheet = true }) {
        Icon(
            painterResource(if (state.casting) R.drawable.cast_connected else R.drawable.cast),
            contentDescription = stringResource(R.string.er_cast),
            tint = if (state.casting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (showSheet) CastSheet(onDismiss = { showSheet = false })
}

/** Device picker in the app's own style, built on MediaRouter discovery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by SonaraCast.state.collectAsStateWithLifecycle()
    val router = remember { MediaRouter.getInstance(context) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }
    val routes = remember { mutableStateListOf<MediaRouter.RouteInfo>() }
    DisposableEffect(router) {
        fun refresh() {
            routes.clear()
            routes.addAll(router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) })
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = ErShape.SheetTop, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                stringResource(R.string.er_cast),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.XL),
            )
            state.error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.XL, vertical = Spacing.XS),
                )
            }
            Spacer(Modifier.height(Spacing.S))
            if (state.casting) {
                DeviceRow(
                    name = state.deviceName ?: stringResource(R.string.er_cast),
                    selected = true,
                    onClick = {},
                )
                DeviceRow(name = stringResource(R.string.er_cast_stop), selected = false, icon = R.drawable.close) {
                    SonaraCast.stopCasting()
                    onDismiss()
                }
            } else if (routes.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.XL, vertical = Spacing.M),
                ) {
                    CircularProgressIndicator(Modifier.size(Size.IconSmall), strokeWidth = Spacing.XXS)
                    Spacer(Modifier.width(Spacing.M))
                    Text(
                        stringResource(R.string.er_cast_searching),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                routes.forEach { route ->
                    DeviceRow(name = route.name, selected = route.isSelected) {
                        route.select()
                        onDismiss()
                    }
                }
            }
            Spacer(Modifier.height(Spacing.L))
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    selected: Boolean,
    icon: Int = R.drawable.cast,
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
        Icon(
            painterResource(if (selected) R.drawable.cast_connected else icon),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.L))
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Motion
import com.metrolist.music.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

private const val TOAST_MS = 2200L

/**
 * Short messages in the app's own style, shown above the player instead of the system toast,
 * which is light grey on many skins and clashes with the dark theme. While the app is not in
 * the foreground (e.g. a message from the playback service) the system toast is used.
 */
object ErToast {
    internal val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    @Volatile
    internal var hostVisible = false

    fun show(context: Context, text: CharSequence) {
        if (hostVisible && messages.tryEmit(text.toString())) return
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    fun show(context: Context, @StringRes text: Int) = show(context, context.getString(text))
}

@Composable
fun ErToastHost(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ErToast.hostVisible = true
                Lifecycle.Event.ON_PAUSE -> ErToast.hostVisible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ErToast.hostVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ErToast.hostVisible = false
        }
    }
    LaunchedEffect(Unit) {
        ErToast.messages.collectLatest {
            message = it
            visible = true
            delay(TOAST_MS)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { it / 2 },
        exit = fadeOut(Motion.fast()) + slideOutVertically(Motion.fast()) { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = ErShape.Pill,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M),
            )
        }
    }
}

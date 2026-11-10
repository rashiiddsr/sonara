/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

private const val PULSE_MS = 2400

/**
 * First screen until the user signs in: SONARA needs the YouTube Music account for the library,
 * likes and playlists, so the only action is "Sign in with Google": one Google sign-in page, in a
 * sheet from the bottom, which also gives the app the YouTube Music session.
 */
@Composable
fun WelcomeScreen() {
    var signingIn by rememberSaveable { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "welcomePulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val mark = Size.PlayButton + Spacing.XL

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.XL),
        ) {
            // The mark sends out its pulse, the same idea as the splash screen.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(mark * 2.4f)
                    .drawBehind {
                        val base = mark.toPx() / 2
                        val reach = size.minDimension / 2 - base
                        repeat(3) { i ->
                            val t = (phase + i / 3f) % 1f
                            drawCircle(
                                color = accent.copy(alpha = (1f - t) * 0.35f),
                                radius = base + reach * t,
                                style = Stroke(width = Spacing.XXS.toPx()),
                            )
                        }
                    },
            ) {
                Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(mark))
            }
            Spacer(Modifier.height(Spacing.L))
            Text(
                stringResource(R.string.er_brand),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.XS))
            Text(
                stringResource(R.string.er_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL, vertical = Spacing.XL),
        ) {
            Text(
                stringResource(R.string.er_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.L))
            Button(
                onClick = { signingIn = true },
                shape = ErShape.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().height(Size.IconButton + Spacing.XS),
            ) {
                // Google's mark sits on a white disc so its colors stay true on the blue button.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(Size.Icon + Spacing.XS)
                        .clip(CircleShape)
                        .background(Color.White),
                ) {
                    Image(painterResource(R.drawable.google_g), contentDescription = null, modifier = Modifier.size(Size.IconSmall - Spacing.XXS))
                }
                Spacer(Modifier.width(Spacing.M))
                Text(stringResource(R.string.er_sign_in_google), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (signingIn) {
        LoginSheet(onClose = { signingIn = false })
    }
}

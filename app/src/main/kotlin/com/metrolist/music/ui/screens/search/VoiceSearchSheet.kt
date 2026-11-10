/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

private const val PULSE_MS = 1600

/**
 * Voice search inside the app: the SONARA pulse rings out from the mic while it listens, and the
 * words appear as they are recognized. Uses the platform SpeechRecognizer, so it needs the
 * microphone permission (asked before this sheet opens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSearchSheet(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var listening by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    ListeningEffects(onLeave = onDismiss)

    DisposableEffect(attempt) {
        val recognizer = createRecognizer(context)
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    failed = false
                }

                override fun onRmsChanged(rmsdB: Float) {
                    level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (text.isNullOrEmpty()) failed = true else currentOnResult(text)
                }

                override fun onError(error: Int) {
                    listening = false
                    failed = true
                }

                override fun onEndOfSpeech() {
                    level = 0f
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        partial = ""
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
        onDispose {
            recognizer.cancel()
            recognizer.destroy()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ErShape.SheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.XL, vertical = Spacing.L),
        ) {
            Text(
                text = when {
                    failed -> stringResource(R.string.er_voice_retry)
                    partial.isNotEmpty() -> partial
                    else -> stringResource(R.string.er_voice_listening)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (partial.isNotEmpty() && !failed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = Size.IconButton * 2),
            )
            Spacer(Modifier.height(Spacing.XL))
            PulseButton(
                icon = R.drawable.mic,
                contentDescription = stringResource(R.string.er_voice_search),
                active = listening,
                level = level,
                onClick = { if (!listening) attempt++ },
            )
            Spacer(Modifier.height(Spacing.XXL))
        }
    }
}

/** A round button with sonar rings: they ring out while listening and swell with the level. */
@Composable
internal fun PulseButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    active: Boolean,
    level: Float,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val mic = Size.PlayButton
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(mic * 2.6f)
            .drawBehind {
                if (!active) return@drawBehind
                val base = mic.toPx() / 2
                val reach = size.minDimension / 2 - base
                repeat(3) { i ->
                    val t = (phase + i / 3f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - t) * 0.5f),
                        radius = base + reach * t,
                        style = Stroke(width = Spacing.XXS.toPx() * 1.5f),
                    )
                }
                drawCircle(accent.copy(alpha = 0.22f), radius = base * (1f + 0.35f * level))
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(mic)
                .clip(CircleShape)
                .background(if (active) accent else MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onClick),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = contentDescription,
                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(Size.Icon + Spacing.S),
            )
        }
    }
}

/** A speech service to search by voice: the system's (usually Google's), or Android's own on-device one. */
fun speechRecognitionAvailable(context: android.content.Context): Boolean =
    SpeechRecognizer.isRecognitionAvailable(context) ||
        (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

private fun createRecognizer(context: android.content.Context): SpeechRecognizer =
    if (!SpeechRecognizer.isRecognitionAvailable(context) &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    } else {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

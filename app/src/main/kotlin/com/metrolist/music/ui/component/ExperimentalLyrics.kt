/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.theme.rememberWindowLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import android.text.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.LyricsClickKey
import com.metrolist.music.constants.LyricsTextPositionKey
import com.metrolist.music.constants.RespectAgentPositioningKey
import com.metrolist.music.constants.ShowIntervalIndicatorKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.lyrics.LyricsUtils.findActiveLineIndices
import com.metrolist.music.lyrics.lyricsTextLooksSynced
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.screens.settings.LyricsPosition
import com.metrolist.music.utils.ComposeToImage
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LyricsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LYRICS_ANCHOR_RATIO = 0.35f
private val LYRICS_ITEM_FALLBACK_HEIGHT_DP = 68.dp
private val LYRICS_ITEM_GAP_DP = 16.dp

/** Space between the last lyric line and the credits under it. */
private val LYRICS_FOOTER_GAP = 40.dp
private val LYRICS_FADE_TOP_DP = 130.dp

/** Where the first line sits while nothing has been sung yet. */
private val LYRICS_TOP_INSET = Spacing.XL
private val LYRICS_FADE_BOTTOM_DP = 160.dp
private const val LYRICS_STAGGER_DELAY_PER_DISTANCE = 45
private const val LYRICS_STAGGER_DELAY_MAX_MS = 360
private const val LYRICS_PREVIEW_TIME = 8000L

/** How long after the last touch the lyrics follow the song again. */
private const val LYRICS_AUTO_RESYNC_IDLE_MS = 2500L

// Cubic ease-in-out, same curve as the reference renderer's scroll.
private val LyricsEaseInOut = Easing { x -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f }

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@SuppressLint("UnusedBoxWithConstraintsScope", "StringFormatInvalid")
@Composable
fun ExperimentalLyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    translations: Map<String, String> = emptyMap(),
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val density = LocalDensity.current
    // Bigger on tablets, where the lyrics fill a panel or the whole screen.
    val lyricsScale = rememberWindowLayout().lyricsScale
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.LEFT)
    val changeLyrics by rememberPreference(LyricsClickKey, true)
    val respectAgentPositioning by rememberPreference(RespectAgentPositioningKey, true)
    val showIntervalIndicator by rememberPreference(ShowIntervalIndicatorKey, true)
    
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentLyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyricsEntity = currentLyricsEntity
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val lyrics = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }


    val lines by lyricsViewModel.lines.collectAsStateWithLifecycle()
    val mergedLyricsList by lyricsViewModel.mergedLyricsList.collectAsStateWithLifecycle()

    LaunchedEffect(lyrics, showIntervalIndicator) {
        lyricsViewModel.processLyrics(lyrics, showIntervalIndicator)
    }

    val isSynced = remember(lyrics) { lyricsTextLooksSynced(lyrics) }
    val hasWordTimings = remember(lines) { lines.any { it.words?.isNotEmpty() == true } }

    // Sonara's player sits on the theme's own backdrop (light on Sonar Day, dark on Sonar Night),
    // so lyrics take the theme's text colour. The old blur/gradient styles forced white, which
    // vanished on the light theme; that setting is no longer offered.
    val expressiveAccent = MaterialTheme.colorScheme.onSurface

    var activeLineIndices by remember { mutableStateOf(emptySet<Int>()) }
    var scrollTargetIndex by rememberSaveable { mutableIntStateOf(-1) }
    var previousScrollActiveIndices by remember { mutableStateOf(emptySet<Int>()) }
    
    var currentPositionState by remember { mutableLongStateOf(0L) }
    var deferredCurrentLineIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastPreviewTime by rememberSaveable { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    // The lyrics image made for sharing and its file, shown before it is sent.
    var sharePreview by remember { mutableStateOf<Pair<android.graphics.Bitmap, android.net.Uri>?>(null) }

    /**
     * Share goes straight to an image, no options: SONARA's card (sonar rings from the cover in
     * the cover's colour, the chosen lines fading like an echo, SONARA's mark and a pulse).
     * The image shows first, with a Share button, so it can be checked before it is sent.
     */
    fun shareLyricsImage(
        text: String,
        title: String,
        artists: String,
    ) {
        showProgressDialog = true
        scope.launch {
            try {
                val image = ComposeToImage.createLyricsImage(
                    context, mediaMetadata?.thumbnailUrl, title, artists, text,
                    (configuration.screenWidthDp * density.density).toInt(),
                    (configuration.screenHeightDp * density.density).toInt(),
                    lyricsAlignment = Layout.Alignment.ALIGN_NORMAL,
                    sonar = true,
                )
                val uri = ComposeToImage.saveBitmapAsFile(context, image, "lyrics_${System.currentTimeMillis()}")
                sharePreview = image to uri
            } catch (e: Exception) {
                ErToast.show(context, context.getString(R.string.failed_to_create_image, e.message))
            } finally {
                showProgressDialog = false
            }
        }
    }
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val isLyricsProviderShown = lyricsEntity != null && lyricsEntity.provider != "Unknown" && lyricsEntity.provider != "Manual" && !isSelectionModeActive
    var isAutoScrollEnabled by rememberSaveable { mutableStateOf(true) }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    val maxSelectionLimit = 5
    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            ErToast.show(context, context.getString(R.string.max_selection_limit, maxSelectionLimit))
            showMaxSelectionToast = false
        }
    }

    var lastMainMaxSeen by remember(lyrics, lines) { mutableIntStateOf(-1) }
    var smoothPositionForSync by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lyrics, lines) {
        if (lyrics.isNullOrEmpty() || lines.isEmpty()) {
            activeLineIndices = emptySet()
            return@LaunchedEffect
        }
        
        var lastPlayerPos = playerConnection.currentPosition
        var lastUpdateTime = System.currentTimeMillis()
        var wasPlaying = playerConnection.isEffectivelyPlaying.value
        
        while (isActive) {
            withFrameNanos { _ -> }
            val now = System.currentTimeMillis()
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            
            val position = if (isSeeking) {
                sliderPosition!!
            } else {
                val isPlaying = playerConnection.isEffectivelyPlaying.value
                val playerPos = playerConnection.currentPosition
                if (playerPos != lastPlayerPos || (isPlaying && !wasPlaying)) {
                    lastPlayerPos = playerPos
                    lastUpdateTime = now
                }
                wasPlaying = isPlaying
                val elapsed = now - lastUpdateTime
                lastPlayerPos + (if (isPlaying) elapsed else 0)
            }
            
            currentPositionState = position
            smoothPositionForSync = position
            
            val lyricsOffset = currentSong?.song?.lyricsOffset ?: 0
            val effectivePosition = position + lyricsOffset
            
            val initialActiveIndices = findActiveLineIndices(lines, effectivePosition)
            val scrollActiveIndicesRaw = findActiveLineIndices(lines, effectivePosition + (if (hasWordTimings) 0L else 250L))
            
            val scrollActiveIndices = scrollActiveIndicesRaw.toMutableSet()
            for (i in scrollActiveIndicesRaw) {
                if (lines.getOrNull(i)?.isBackground == true) {
                    for (j in i - 1 downTo 0) {
                        if (lines.getOrNull(j)?.isBackground == false) {
                            scrollActiveIndices.add(j)
                            break
                        }
                    }
                }
            }
            
            val newActiveIndices = initialActiveIndices.toMutableSet()
            for (i in initialActiveIndices) {
                if (lines.getOrNull(i)?.isBackground == true) {
                    for (j in i - 1 downTo 0) {
                        if (lines.getOrNull(j)?.isBackground == false) {
                            newActiveIndices.add(j)
                            break
                        }
                    }
                }
            }

            val scrollMax = scrollActiveIndices
                .filter { lines.getOrNull(it)?.isBackground == false }
                .maxOrNull() ?: (scrollActiveIndices.maxOrNull() ?: -1)

            val isCurrentTargetStillActive = scrollTargetIndex in scrollActiveIndices
            val anyStillActive = scrollActiveIndices.isNotEmpty()

            val shouldScroll = when {
                isSeeking -> true
                // If current target just ended and there are other active lines, scroll to the new max
                !isCurrentTargetStillActive && anyStillActive && scrollMax > scrollTargetIndex -> true
                
                // If current target just ended and no other active lines
                !isCurrentTargetStillActive && !anyStillActive && previousScrollActiveIndices.isNotEmpty() -> true
                
                // If we don't have a target yet and lines became active
                scrollTargetIndex == -1 && anyStillActive -> true
                
                // New line started while nothing was active before
                previousScrollActiveIndices.isEmpty() && anyStillActive && scrollMax > scrollTargetIndex -> true

                else -> false
            }

            if (shouldScroll) {
                val targetToScroll = when {
                    isSeeking -> scrollMax
                    !isCurrentTargetStillActive && anyStillActive -> scrollMax
                    !isCurrentTargetStillActive && !anyStillActive -> {
                        (lastMainMaxSeen + 1 until lines.size).firstOrNull {
                            lines.getOrNull(it)?.isBackground == false
                        } ?: scrollTargetIndex
                    }
                    else -> scrollMax
                }
                if (targetToScroll != -1 && (isSeeking || targetToScroll > scrollTargetIndex)) {
                    scrollTargetIndex = targetToScroll
                }
            }
            
            if (scrollMax > lastMainMaxSeen && scrollMax != -1) {
                lastMainMaxSeen = scrollMax
            }
            
            // Opened (or paused) between two lines, nothing is active yet: anchor on the last line
            // already sung, not on the first line of the song, which left the top empty.
            if (scrollTargetIndex == -1 && !isSeeking) {
                val sung = lines.indexOfLast { !it.isBackground && it.time <= effectivePosition }
                if (sung > 0) scrollTargetIndex = sung
            }

            previousScrollActiveIndices = scrollActiveIndices
            activeLineIndices = newActiveIndices
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LYRICS_PREVIEW_TIME)
            lastPreviewTime = 0L
        }
    }

    LaunchedEffect(scrollTargetIndex, isAutoScrollEnabled) {
        if (scrollTargetIndex != -1 && isAutoScrollEnabled) {
            deferredCurrentLineIndex = scrollTargetIndex
        }
    }

    var userManualOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lyrics, lines) {
        isAutoScrollEnabled = true
        userManualOffset = 0f
        scrollTargetIndex = -1
        deferredCurrentLineIndex = 0
        isSelectionModeActive = false
        selectedIndices.clear()
        previousScrollActiveIndices = emptySet()
    }
    
    var flingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // When the user last touched or scrolled the lyrics, and whether a finger is on them now.
    var lastUserActivity by remember { mutableLongStateOf(0L) }
    var userTouching by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    val decayAnimSpec = remember { exponentialDecay<Float>(frictionMultiplier = 1.8f) }
    val itemHeights = remember(lyrics, mergedLyricsList) { mutableStateMapOf<Int, Int>() }
    var isInitialLayout by remember(lyrics, mergedLyricsList) { mutableStateOf(true) }

    val activeListIndex by remember(mergedLyricsList, deferredCurrentLineIndex) {
        derivedStateOf {
            mergedLyricsList.indexOfFirst { 
                (it is LyricsListItem.Line && it.index == deferredCurrentLineIndex) ||
                (it is LyricsListItem.Indicator && it.afterLineIndex == deferredCurrentLineIndex)
            }.coerceAtLeast(0)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(showLyrics) {
        val activity = context as? Activity
        if (showLyrics) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize().padding(bottom = 12.dp)
    ) {
        val maxHeightPx = constraints.maxHeight.toFloat()
        // Where the first line is drawn right now, for the top fade.
        val firstLineY = remember { mutableFloatStateOf(0f) }
        val anchorY = maxHeightPx * LYRICS_ANCHOR_RATIO
        val lineHeightPx = with(density) { LYRICS_ITEM_FALLBACK_HEIGHT_DP.toPx() }
        val indicatorHeightPx = with(density) { 72.dp.toPx() }
        
        // Use a more permissive fallback for constraints to prevent "locks" if items are not measured yet
        val constraintLineHeightPx = with(density) { 120.dp.toPx() }

        val positions = remember(itemHeights.toMap(), activeListIndex, mergedLyricsList) {
            val map = mutableMapOf<Int, Float>()
            if (activeListIndex == -1 || mergedLyricsList.isEmpty()) return@remember map
            
            map[activeListIndex] = 0f
            var currentY = 0f
            for (i in activeListIndex - 1 downTo 0) {
                val item = mergedLyricsList[i]
                val height = itemHeights[i]?.toFloat() ?: (if (item is LyricsListItem.Indicator) indicatorHeightPx else lineHeightPx)
                val noGap = (item as? LyricsListItem.Line)?.entry?.isBackground == true || item is LyricsListItem.Indicator
                currentY -= (height + if (noGap) 0f else with(density) { LYRICS_ITEM_GAP_DP.toPx() })
                map[i] = currentY
            }
            currentY = 0f
            for (i in activeListIndex until mergedLyricsList.size - 1) {
                val currentItem = mergedLyricsList[i]
                val nextItem = mergedLyricsList[i + 1]
                val height = itemHeights[i]?.toFloat() ?: (if (currentItem is LyricsListItem.Indicator) indicatorHeightPx else lineHeightPx)
                val nextNoGap = (nextItem as? LyricsListItem.Line)?.entry?.isBackground == true || nextItem is LyricsListItem.Indicator
                currentY += (height + if (nextNoGap) 0f else with(density) { LYRICS_ITEM_GAP_DP.toPx() })
                map[i + 1] = currentY
            }
            map
        }

        // The current line is held a third of the way down, but early in a song that would leave
        // the space above the first line empty. Everything lifts by that empty space instead, so
        // the song starts at the top and the current line walks down to the anchor as it plays.
        val topInsetPx = with(density) { LYRICS_TOP_INSET.toPx() }
        val topShift = (anchorY + (positions[0] ?: 0f) - topInsetPx).coerceAtLeast(0f)

        // Scrolling stops at the song's two natural ends: the first line at the top, the last line
        // where the current line is held. Before, the first line could be dragged down to the
        // bottom of the screen and the last one up past the top, leaving the page empty.
        val firstLineTop = anchorY - topShift + (positions[0] ?: 0f)
        val lastLineTop = anchorY - topShift + (positions[mergedLyricsList.lastIndex] ?: 0f)
        val maxOffset = if (mergedLyricsList.isEmpty()) 0f else topInsetPx - firstLineTop
        val minOffset = if (mergedLyricsList.isEmpty()) 0f else anchorY - lastLineTop
        val scrollClampMin = minOf(minOffset, maxOffset)
        val scrollClampMax = maxOf(minOffset, maxOffset)
        // The drag handler below is created once (pointerInput(Unit)); reading the bounds through
        // updated state keeps them current as lines advance and get measured. Captured plain values
        // froze the bounds from when the page opened, so scrolling up worked only sometimes.
        val currentClampMin by rememberUpdatedState(scrollClampMin)
        val currentClampMax by rememberUpdatedState(scrollClampMax)

        LaunchedEffect(scrollClampMin, scrollClampMax) {
            if (userManualOffset < scrollClampMin || userManualOffset > scrollClampMax) {
                userManualOffset = userManualOffset.coerceIn(scrollClampMin, scrollClampMax)
            }
        }

        LaunchedEffect(isAutoScrollEnabled, lines) {
            if (isAutoScrollEnabled) {
                val start = userManualOffset
                if (abs(start) < 1f) {
                    userManualOffset = 0f
                    return@LaunchedEffect
                }
                val anim = Animatable(start)
                var lastValue = start
                anim.animateTo(0f, tween((abs(start) / 4f).toInt().coerceIn(200, 600), easing = FastOutSlowInEasing)) {
                    userManualOffset += (value - lastValue)
                    lastValue = value
                }
                userManualOffset = 0f
            }
        }

        LaunchedEffect(showLyrics, lyrics, mergedLyricsList.size) {
            if (showLyrics && mergedLyricsList.isNotEmpty()) {
                isInitialLayout = true
                snapshotFlow { 
                    val h = itemHeights.toMap()
                    val windowStart = (activeListIndex - 8).coerceAtLeast(0)
                    val windowEnd = (activeListIndex + 12).coerceAtMost(mergedLyricsList.size - 1)
                    (windowStart..windowEnd).all { h.containsKey(it) } 
                }.first { it }
                isInitialLayout = false
            }
        }

        // When jumping significantly, we might want to reset isInitialLayout to prevent jumps
        LaunchedEffect(activeListIndex) {
            if (mergedLyricsList.isNotEmpty()) {
                val h = itemHeights.toMap()
                val windowStart = (activeListIndex - 3).coerceAtLeast(0)
                val windowEnd = (activeListIndex + 3).coerceAtMost(mergedLyricsList.size - 1)
                val needsMeasurement = (windowStart..windowEnd).any { !h.containsKey(it) }
                if (needsMeasurement) {
                    isInitialLayout = true
                    snapshotFlow { 
                        val hh = itemHeights.toMap()
                        (windowStart..windowEnd).all { hh.containsKey(it) } 
                    }.first { it }
                    isInitialLayout = false
                }
            }
        }

        val latestResyncLyrics by rememberUpdatedState(
            newValue = {
                flingJob?.cancel()
                var target = scrollTargetIndex
                if (target == -1) {
                    target =
                        findActiveLineIndices(
                            lines,
                            currentPositionState + (currentSong?.song?.lyricsOffset ?: 0),
                        ).maxOrNull() ?: -1
                }
                if (target != -1) {
                    val listIdx =
                        mergedLyricsList.indexOfFirst {
                            it is LyricsListItem.Line && it.index == target
                        }.coerceAtLeast(0)
                    userManualOffset += positions[listIdx] ?: 0f
                    deferredCurrentLineIndex = target
                    scrollTargetIndex = target
                }
                isAutoScrollEnabled = true
            },
        )

        // No "back to the current line" button: like other music players, the lyrics follow the
        // song again on their own once the user has left them alone for a moment. While a finger
        // is on them, or they are still scrolling or flinging, nothing moves under the user. It
        // only counts time since the last touch, so it works the same offline.
        LaunchedEffect(isAutoScrollEnabled, userTouching, lastUserActivity, isSelectionModeActive) {
            if (isAutoScrollEnabled || !isSynced || isSelectionModeActive || userTouching) return@LaunchedEffect
            delay(LYRICS_AUTO_RESYNC_IDLE_MS)
            flingJob?.join()
            if (!isAutoScrollEnabled && !userTouching &&
                System.currentTimeMillis() - lastUserActivity >= LYRICS_AUTO_RESYNC_IDLE_MS
            ) {
                latestResyncLyrics()
            }
        }

        if (lyrics == LYRICS_NOT_FOUND) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.lyrics_not_found), fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.alpha(0.5f))
            }
        } else if (lyrics == null) {
             Column(modifier = Modifier.padding(top = 100.dp)) {
                 ShimmerHost { repeat(10) { Box(contentAlignment = when (lyricsTextPosition) {
                     LyricsPosition.LEFT -> Alignment.CenterStart; LyricsPosition.CENTER -> Alignment.Center; else -> Alignment.CenterEnd
                 }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) { TextPlaceholder() } } }
             }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The top fade grows only once lines have gone up past the top; the first
                    // line, sitting at the top, is never faded.
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        val inset = LYRICS_TOP_INSET.toPx()
                        val fadeTop = (2 * inset - firstLineY.floatValue).coerceIn(inset, LYRICS_FADE_TOP_DP.toPx())
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fadeTop),
                            blendMode = BlendMode.DstIn,
                        )
                        val fadeBottom = LYRICS_FADE_BOTTOM_DP.toPx()
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Black, Color.Transparent),
                                startY = size.height - fadeBottom,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .clipToBounds()
                    .nestedScroll(remember {
                        object : NestedScrollConnection {
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput) {
                                    isAutoScrollEnabled = false
                                    lastUserActivity = System.currentTimeMillis()
                                }
                                if (!isSelectionModeActive) lastPreviewTime = System.currentTimeMillis()
                                return super.onPostScroll(consumed, available, source)
                            }
                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                isAutoScrollEnabled = false
                                if (!isSelectionModeActive) lastPreviewTime = System.currentTimeMillis()
                                return super.onPostFling(consumed, available)
                            }
                        }
                    })
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (isInitialLayout) continue
                                flingJob?.cancel()
                                velocityTracker.resetTracking()
                                isAutoScrollEnabled = false
                                userTouching = true
                                lastUserActivity = System.currentTimeMillis()
                                lastPreviewTime = System.currentTimeMillis()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                verticalDrag(down.id) { change ->
                                    userManualOffset = (userManualOffset + change.positionChange().y).coerceIn(currentClampMin, currentClampMax)
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    lastUserActivity = System.currentTimeMillis()
                                    change.consume()
                                }
                                userTouching = false
                                val velocity = velocityTracker.calculateVelocity().y
                                flingJob = scope.launch {
                                    AnimationState(initialValue = userManualOffset, initialVelocity = velocity).animateDecay(decayAnimSpec) {
                                        val clamped = value.coerceIn(currentClampMin, currentClampMax)
                                        userManualOffset = clamped
                                        if (value != clamped) cancelAnimation()
                                    }
                                    // The idle time counts from when the lyrics stopped moving.
                                    lastUserActivity = System.currentTimeMillis()
                                }
                            }
                        }
                    }
            ) {
                val lyricsOffsetVal = (currentSong?.song?.lyricsOffset ?: 0).toLong()
                val currentEffectivePosition = currentPositionState + lyricsOffsetVal
                
                // The credits go under the last line with words (LRC files often end with an empty
                // timed line), inside that line's own slot, so they move with it exactly: frozen
                // while the user scrolls, animated with it otherwise. Placed on their own, they
                // drifted onto the last line whenever the two were animated differently.
                val footerListIndex = mergedLyricsList
                    .indexOfLast { it is LyricsListItem.Line && it.entry.text.isNotBlank() }
                    .takeIf { it >= 0 } ?: mergedLyricsList.lastIndex

                mergedLyricsList.forEachIndexed { listIndex, listItem ->
                    key(listItem) {
                        val distance = abs(listIndex - activeListIndex)
                        val targetOffset = anchorY - topShift + positions.getOrDefault(listIndex, (listIndex - activeListIndex) * lineHeightPx)
                        val frozenOffset = remember { mutableFloatStateOf(targetOffset) }
                        LaunchedEffect(isAutoScrollEnabled, targetOffset, isInitialLayout) {
                            if (isAutoScrollEnabled || isInitialLayout) frozenOffset.floatValue = targetOffset
                        }
                        val animatedOffset by animateFloatAsState(
                            targetValue = if (isAutoScrollEnabled) targetOffset else frozenOffset.floatValue,
                            animationSpec = if (isInitialLayout || !isAutoScrollEnabled) snap() 
                                            else {
                                                tween(550, (distance * LYRICS_STAGGER_DELAY_PER_DISTANCE).coerceAtMost(LYRICS_STAGGER_DELAY_MAX_MS), LyricsEaseInOut)
                                            },
                            label = "lyricStaggeredOffset_$listIndex"
                        )

                        Box(
                            modifier = Modifier.fillMaxWidth().layout { m, c -> 
                                val p = m.measure(c.copy(maxHeight = Constraints.Infinity))
                                layout(p.width, 0) { p.place(0, 0) }
                            }.offset {
                                val y = animatedOffset + userManualOffset
                                if (listIndex == 0) firstLineY.floatValue = y
                                IntOffset(0, y.roundToInt())
                            }
                        ) {
                            when (listItem) {
                                is LyricsListItem.Indicator -> {
                                    val visible =
                                        isAutoScrollEnabled &&
                                            currentEffectivePosition >= listItem.gapStartMs &&
                                            currentEffectivePosition <= listItem.gapEndMs - 650L
                                    IntervalIndicator(listItem.gapStartMs, listItem.gapEndMs - 650L, currentEffectivePosition, visible, expressiveAccent,
                                        Modifier.fillMaxWidth().onSizeChanged { itemHeights[listIndex] = it.height }.then(
                                            if (lyricsTextPosition == LyricsPosition.LEFT) Modifier.padding(horizontal = 11.dp).fillMaxWidth()
                                            else Modifier.padding(horizontal = 24.dp).wrapContentWidth(Alignment.CenterHorizontally).width(66.dp)
                                        ))
                                }
                                is LyricsListItem.Line -> {
                                    val index = listItem.index
                                    val item = listItem.entry
                                    val isActiveLine = activeLineIndices.contains(index)
                                    val pairedMainLineIndex = if (item.isBackground) (index - 1 downTo 0).firstOrNull { lines.getOrNull(it)?.isBackground == false } ?: -1 else -1
                                    
                                    val isInGapWithMain = if (item.isBackground && pairedMainLineIndex != -1) {
                                        val pairedMainLine = lines[pairedMainLineIndex]
                                        currentEffectivePosition >= pairedMainLine.time && currentEffectivePosition <= item.time
                                    } else false
                                    
                                    val bgVisible = item.isBackground && (activeLineIndices.contains(pairedMainLineIndex) || activeLineIndices.contains(index) || isInGapWithMain)
                                    
                                    Column {
                                    LyricsLine(
                                        index = index, item = item, isSynced = isSynced,
                                        translation = translations[item.text.trim()],
                                        isActiveLine = isActiveLine,
                                        bgVisible = bgVisible, isSelected = selectedIndices.contains(index),
                                        isSelectionModeActive = isSelectionModeActive, currentPositionState = currentPositionState,
                                        lyricsOffset = (currentSong?.song?.lyricsOffset ?: 0).toLong(),
                                        playerConnection = playerConnection, lyricsTextSize = LYRICS_TEXT_SIZE_SP * lyricsScale, lyricsLineSpacing = 1.3f,
                                        expressiveAccent = expressiveAccent, lyricsTextPosition = lyricsTextPosition,
                                        respectAgentPositioning = respectAgentPositioning, isAutoScrollEnabled = isAutoScrollEnabled,
                                        displayedCurrentLineIndex = deferredCurrentLineIndex,
                                        onSizeChanged = { itemHeights[listIndex] = it },
                                        onClick = {
                                            if (isSelectionModeActive) {
                                                if (selectedIndices.contains(index)) {
                                                    selectedIndices.remove(index)
                                                    if (selectedIndices.isEmpty()) isSelectionModeActive = false
                                                } else if (selectedIndices.size < maxSelectionLimit) selectedIndices.add(index)
                                                else showMaxSelectionToast = true
                                            } else if (changeLyrics) {
                                                if (item.time < playerConnection.duration + 30000L) {
                                                    playerConnection.seekTo((item.time - (currentSong?.song?.lyricsOffset ?: 0)).coerceAtLeast(0))
                                                } else {
                                                    scrollTargetIndex = index
                                                    deferredCurrentLineIndex = index
                                                }
                                                isAutoScrollEnabled = true
                                                lastPreviewTime = 0L
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionModeActive) {
                                                isSelectionModeActive = true
                                                selectedIndices.add(index)
                                            }
                                        }
                                    )
                                    if (listIndex == footerListIndex && !isSelectionModeActive) {
                                        LyricsCredits(
                                            provider = lyricsEntity?.provider?.takeIf { isLyricsProviderShown },
                                            translated = translations.isNotEmpty(),
                                            modifier = Modifier
                                                .padding(top = LYRICS_FOOTER_GAP)
                                                .padding(horizontal = if (lyricsTextPosition == LyricsPosition.LEFT) 11.dp else 24.dp),
                                        )
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LyricsActionOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            isSelectionModeActive = isSelectionModeActive, anySelected = selectedIndices.isNotEmpty(),
            onCancelSelection = { isSelectionModeActive = false; selectedIndices.clear() },
            onShareSelection = {
                val text = selectedIndices.sorted().mapNotNull { lines.getOrNull(it)?.text }.joinToString("\n")
                if (text.isNotBlank()) {
                    shareLyricsImage(text, mediaMetadata?.title ?: "", cardArtists(context, mediaMetadata?.artists?.map { it.name }.orEmpty()))
                }
                isSelectionModeActive = false; selectedIndices.clear()
            }
        )
    }

    if (showProgressDialog) {
        BasicAlertDialog(onDismissRequest = {}) {
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Box(Modifier.padding(32.dp)) { Text(stringResource(R.string.generating_image) + "\n" + stringResource(R.string.please_wait)) }
            }
        }
    }

    sharePreview?.let { (image, uri) ->
        LyricsSharePreview(
            image = image,
            onDismiss = { sharePreview = null },
            onShare = {
                sharePreview = null
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, context.getString(R.string.share_lyrics)))
            },
        )
    }

}

/** "A", "A & B", or "A, B & 2 more" for the lyrics card, so a collaboration never cuts a name. */
internal fun cardArtists(
    context: android.content.Context,
    names: List<String>,
): String =
    when (names.size) {
        0 -> ""
        1 -> names[0]
        2 -> "${names[0]} & ${names[1]}"
        else -> context.getString(R.string.er_artists_more, names[0], names[1], names.size - 2)
    }

/** Lyrics text size: about six lines fit on screen at once, rather than three large ones at 36. */
private const val LYRICS_TEXT_SIZE_SP = 26f

/** Under the last lyric line: where the lyrics come from, that a translation is machine made, whose they are. */
@Composable
private fun LyricsCredits(
    provider: String?,
    translated: Boolean,
    modifier: Modifier = Modifier,
) {
    // Its own small type: the page's lyric style has a tall line height, which spread these
    // short lines far apart.
    val style = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        lineHeight = 16.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        if (provider != null) {
            Text(
                text = stringResource(R.string.lyrics_from_provider, provider),
                style = style.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        if (translated) Text(text = stringResource(R.string.er_lyrics_translated), style = style)
        Text(text = stringResource(R.string.er_lyrics_license), style = style)
    }
}

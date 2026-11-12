/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where a dragged row was and where it was put, as indices in the list it was dragged in. */
data class Moved(val from: Int, val to: Int)

/**
 * Rows of a lazy list put into a new order by dragging them. While a row is held, the list shows
 * the order it would land in, the row stays under the pointer, and the list scrolls when the row
 * is taken to its top or bottom edge. Each row's key in the list must be [keyOf] its item.
 *
 * With [waits], a dropped row keeps its new place, faded, until the caller's list changes (the
 * move was confirmed) or [undo] (it wasn't): for moves that something else must accept first.
 */
@Stable
class Reorder<T>(
    private val list: LazyListState,
    private val scope: CoroutineScope,
    private val waits: Boolean,
    private val edge: Float,
    private val step: Float,
    private val keyOf: (T) -> Any,
) {
    private var base: List<T>? = null
    private var order by mutableStateOf<List<T>?>(null)
    private var waiting by mutableStateOf<Any?>(null)
    private var from = -1
    private var scrolling: Job? = null
    private var swappedOn: LazyListLayoutInfo? = null

    /** The row under the pointer, or settling into its place after the drop, by its key. */
    var held by mutableStateOf<Any?>(null)
        private set

    /** How far [held] is drawn from its place in the list. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** What the list shows: [items], or the order a held or dropped row puts them in. */
    fun shown(items: List<T>): List<T> = order?.takeIf { items === base } ?: items

    /** The row was dropped and its move hasn't been confirmed yet. */
    fun isWaiting(key: Any, items: List<T>): Boolean = waiting == key && items === base && order != null

    /** How a row moves when others make room: at once for the held one, which follows the pointer. */
    fun placement(key: Any): FiniteAnimationSpec<IntOffset>? = if (held == key) null else MAKE_ROOM

    fun start(items: List<T>, key: Any): Boolean {
        if (held != null || (waiting != null && items === base && order != null)) return false
        val index = items.indexOfFirst { keyOf(it) == key }
        if (index < 0) return false
        base = items
        order = items
        waiting = null
        from = index
        held = key
        offset = 0f
        swappedOn = null
        scrolling = scope.launch {
            while (true) {
                withFrameMillis { }
                scrollAtEdge()
            }
        }
        return true
    }

    fun drag(dy: Float) {
        if (scrolling == null) return
        offset += dy
        swap()
    }

    /**
     * Lets go of the row. Returns where it went, or null when it stayed, the gesture was
     * cancelled, or [items] changed meanwhile (then the list shows [items] as they are).
     */
    fun drop(items: List<T>, done: Boolean): Moved? {
        val key = held ?: return null
        scrolling?.cancel()
        scrolling = null
        val to = order?.indexOfFirst { keyOf(it) == key } ?: -1
        val moved = if (done && items === base && to >= 0 && to != from) Moved(from, to) else null
        if (moved == null) order = null else if (waits) waiting = key
        val start = offset
        scope.launch {
            try {
                animate(start, 0f, animationSpec = tween(SETTLE_MS)) { value, _ -> offset = value }
            } finally {
                offset = 0f
                held = null
            }
        }
        return moved
    }

    /** The move wasn't accepted: the rows go back to where they were. */
    fun undo() {
        order = null
        waiting = null
    }

    /** Moves the held row into the place under its middle, once the list has laid out the last move. */
    private fun swap() {
        val key = held ?: return
        val current = order ?: return
        val info = list.layoutInfo
        if (info === swappedOn) return
        val me = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val middle = (me.offset + offset + me.size / 2f).coerceIn(info.viewportStartOffset.toFloat(), info.viewportEndOffset - 1f)
        val target = info.visibleItemsInfo.firstOrNull { it.key != key && middle >= it.offset && middle < it.offset + it.size } ?: return
        val to = current.indexOfFirst { keyOf(it) == target.key }
        val at = current.indexOfFirst { keyOf(it) == key }
        if (to < 0 || at < 0) return
        val newTop = if (to > at) target.offset + target.size - me.size else target.offset
        // Keeps the list where it is: by default it follows its first row, which may be the one moving.
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        order = current.toMutableList().apply { add(to, removeAt(at)) }
        offset -= newTop - me.offset
        swappedOn = info
    }

    private suspend fun scrollAtEdge() {
        val key = held ?: return
        val info = list.layoutInfo
        val me = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val top = me.offset + offset
        val bottom = top + me.size
        val start = info.viewportStartOffset + edge
        val end = info.viewportEndOffset - edge
        val speed =
            when {
                top < start -> -step * ((start - top) / edge).coerceIn(0f, 1f)
                bottom > end -> step * ((bottom - end) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
        if (speed == 0f) return
        offset += list.scrollBy(speed)
        swap()
    }
}

@Composable
fun <T> rememberReorder(list: LazyListState, waits: Boolean = false, keyOf: (T) -> Any): Reorder<T> {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(list, waits) {
        with(density) { Reorder(list, scope, waits, REORDER_EDGE.toPx(), REORDER_STEP.toPx(), keyOf) }
    }
}

/** Draws a row where [reorder] has it: lifted under the pointer while held, faded while its move waits. */
@Composable
fun <T> Modifier.reorderRow(reorder: Reorder<T>, key: Any, items: List<T>): Modifier {
    val held = reorder.held == key
    val waiting = reorder.isWaiting(key, items)
    return zIndex(if (held) 1f else 0f)
        .graphicsLayer {
            translationY = if (reorder.held == key) reorder.offset else 0f
            alpha = if (waiting) WAITING_ALPHA else 1f
        }
        .then(if (held) Modifier.shadow(HELD_ELEVATION, ErShape.Small).background(MaterialTheme.colorScheme.surfaceContainerHigh, ErShape.Small) else Modifier)
}

/**
 * Lets the row (or handle) it is on be dragged into a new place: with the left mouse button, or
 * with any pointer when not [mouseOnly] (a handle, so a finger on the row still scrolls the list).
 */
@Composable
fun <T> Modifier.dragToReorder(reorder: Reorder<T>, key: Any, items: List<T>, mouseOnly: Boolean, onMoved: (Moved) -> Unit): Modifier {
    val currentItems by rememberUpdatedState(items)
    val moved by rememberUpdatedState(onMoved)
    val anchor = remember { Anchor() }
    return onGloballyPositioned { anchor.coordinates = it }
        .pointerInput(reorder, key, mouseOnly) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val mouse = down.type == PointerType.Mouse
                if ((mouseOnly && !mouse) || (mouse && !currentEvent.buttons.isPrimaryPressed)) return@awaitEachGesture
                // In the window's terms: the row itself moves under the pointer while it is dragged.
                var last = anchor.y(down) ?: return@awaitEachGesture
                val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                if (!reorder.start(currentItems, key)) return@awaitEachGesture
                fun follow(change: PointerInputChange) {
                    val y = anchor.y(change) ?: return
                    reorder.drag(y - last)
                    last = y
                }
                follow(slop)
                val done = verticalDrag(slop.id) { change ->
                    follow(change)
                    change.consume()
                }
                reorder.drop(currentItems, done)?.let(moved)
            }
        }
}

private class Anchor {
    var coordinates: LayoutCoordinates? = null

    fun y(change: PointerInputChange): Float? = coordinates?.takeIf { it.isAttached }?.localToRoot(change.position)?.y
}

private val MAKE_ROOM = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
private const val SETTLE_MS = 150
private const val WAITING_ALPHA = 0.5f
private val HELD_ELEVATION = 8.dp
private val REORDER_EDGE = 48.dp
private val REORDER_STEP = 14.dp

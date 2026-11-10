/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

object Motion {
    const val Short = 150
    const val Medium = 250
    const val Long = 400

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Track-change crossfade for the player background. */
    const val ArtworkCrossfade = Long

    /** Spring for the finger-driven player sheet: critically damped, no overshoot. */
    val SheetSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(Short, easing = Emphasized)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(Medium, easing = Emphasized)

    private const val SLIDE_FRACTION = 12

    /** The bottom tabs (Screens.Home, Screens.Search, Screens.Library). */
    private val TabRoutes = setOf("home", "search_input", "library")

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
        initialState.destination.route in TabRoutes && targetState.destination.route in TabRoutes

    // Switching tabs is instant, like Spotify: no fade, so there is never a frame with neither
    // page on screen (the clear nav bar showed that empty frame). Opening a page inside a tab
    // crossfades with a slight slide; the new page starts fading in at once, with no gap.
    val navEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) {
            EnterTransition.None
        } else {
            fadeIn(tween(Medium, easing = Emphasized)) +
                slideInHorizontally(tween(Medium, easing = Emphasized)) { it / SLIDE_FRACTION }
        }
    }
    val navExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) {
            ExitTransition.None
        } else {
            fadeOut(tween(Short, easing = Exit)) +
                slideOutHorizontally(tween(Medium, easing = Emphasized)) { -it / SLIDE_FRACTION }
        }
    }
    val navPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (isTabSwitch()) {
            EnterTransition.None
        } else {
            fadeIn(tween(Medium, easing = Emphasized)) +
                slideInHorizontally(tween(Medium, easing = Emphasized)) { -it / SLIDE_FRACTION }
        }
    }
    val navPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (isTabSwitch()) {
            ExitTransition.None
        } else {
            fadeOut(tween(Short, easing = Exit)) +
                slideOutHorizontally(tween(Medium, easing = Emphasized)) { it / SLIDE_FRACTION }
        }
    }
}

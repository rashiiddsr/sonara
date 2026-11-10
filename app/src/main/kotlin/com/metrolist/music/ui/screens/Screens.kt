/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrolist.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.nav_home_off,
        iconIdActive = R.drawable.nav_home_on,
        route = "home"
    )

    /**
     * Browse: new releases, charts, genres and moods, with search at its top.
     */
    object Search : Screens(
        titleId = R.string.er_browse,
        iconIdInactive = R.drawable.nav_browse_off,
        iconIdActive = R.drawable.nav_browse_on,
        route = "search_input"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.nav_library_off,
        iconIdActive = R.drawable.nav_library_on,
        route = "library"
    )

    companion object {
        val MainScreens = listOf(Home, Search, Library)
    }
}

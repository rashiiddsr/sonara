/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import androidx.navigation.NavController
import com.metrolist.music.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}

/**
 * Route that opens a YouTube Music podcast show. There is no dedicated podcast screen: a show id is
 * "MPSP" + the id of the playlist that holds its episodes, so the show opens as an online playlist.
 */
fun podcastRoute(podcastId: String): String = "online_playlist/${podcastId.removePrefix("MPSP")}"

package com.ermusic.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "com.ermusic.android"

// Stable test tags exposed as resource ids (testTagsAsResourceId) by the app.
object Tags {
    const val HOME_FEED = "home_feed"
    const val MINI_PLAYER = "mini_player"
    const val PLAYER = "player_expanded"
    const val LIBRARY_SONGS = "library_songs_list"
    const val ALBUM_LIST = "album_list"
}

// A long-lived official upload that is available on YouTube Music in most regions.
const val SAMPLE_VIDEO_ID = "dQw4w9WgXcQ"

private const val TIMEOUT = 15_000L

fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.res(Tags.HOME_FEED)), TIMEOUT)
}

fun MacrobenchmarkScope.scrollHome() {
    val feed = device.wait(Until.findObject(By.res(Tags.HOME_FEED)), TIMEOUT) ?: error("home feed not found")
    feed.setGestureMargin(device.displayWidth / 5)
    repeat(3) { feed.fling(Direction.DOWN) }
    repeat(3) { feed.fling(Direction.UP) }
}

fun MacrobenchmarkScope.openDeepLink(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage(TARGET_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivityAndWait(intent)
}

/** Starts playback through a watch deep link and waits for the mini player. */
fun MacrobenchmarkScope.ensurePlaying() {
    if (device.hasObject(By.res(Tags.MINI_PLAYER))) return
    openDeepLink("https://music.youtube.com/watch?v=$SAMPLE_VIDEO_ID")
    device.wait(Until.hasObject(By.res(Tags.MINI_PLAYER)), TIMEOUT) || error("mini player did not appear")
}

fun MacrobenchmarkScope.openAndClosePlayer() {
    repeat(3) {
        val mini = device.wait(Until.findObject(By.res(Tags.MINI_PLAYER)), TIMEOUT) ?: error("mini player not found")
        mini.click()
        device.wait(Until.hasObject(By.res(Tags.PLAYER)), TIMEOUT)
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.res(Tags.MINI_PLAYER)), TIMEOUT)
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.openLibrarySongs() {
    device.wait(Until.findObject(By.text("Library")), TIMEOUT)?.click() ?: error("Library tab not found")
    device.wait(Until.findObject(By.text("Songs")), TIMEOUT)?.click()
    device.wait(Until.hasObject(By.res(Tags.LIBRARY_SONGS)), TIMEOUT)
}

fun MacrobenchmarkScope.scrollLibrarySongs() {
    val list = device.wait(Until.findObject(By.res(Tags.LIBRARY_SONGS)), TIMEOUT) ?: error("songs list not found")
    list.setGestureMargin(device.displayWidth / 5)
    repeat(3) { list.fling(Direction.DOWN) }
    repeat(3) { list.fling(Direction.UP) }
}

fun MacrobenchmarkScope.openAlbum(browseId: String) {
    openDeepLink("https://music.youtube.com/browse/$browseId")
    device.wait(Until.hasObject(By.res(Tags.ALBUM_LIST)), TIMEOUT)
    device.waitForIdle()
}

// A 12-track album page on YouTube Music (verified reachable via the public InnerTube browse API).
const val SAMPLE_ALBUM_BROWSE_ID = "MPREb_BOEC90a78us"

/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop

import com.ermusic.desktop.data.Backup
import com.ermusic.desktop.window.Uninstall
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import com.ermusic.desktop.window.ErrorLog
import com.ermusic.desktop.data.Housekeeping
import com.ermusic.desktop.window.WindowsSession
import com.ermusic.desktop.playback.LyricsRepository
import com.ermusic.desktop.ui.OfflineStrip
import com.ermusic.desktop.playback.Connection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.ermusic.desktop.auth.Account
import com.ermusic.desktop.auth.AccountStore
import com.ermusic.desktop.auth.EdgeSignIn
import com.ermusic.desktop.auth.NoBrowserException
import com.ermusic.desktop.data.Settings
import com.ermusic.desktop.playback.Player
import com.ermusic.desktop.playback.PlayerState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import com.ermusic.desktop.ui.AlbumScreen
import com.ermusic.desktop.ui.LyricsPage
import com.ermusic.desktop.ui.MiniPlayerContent
import com.ermusic.desktop.ui.NowPlayingPanel
import com.ermusic.desktop.ui.RadioScreen
import com.ermusic.desktop.ui.SidePanel
import com.ermusic.desktop.ui.AppDialogs
import com.ermusic.desktop.ui.AppState
import com.ermusic.desktop.ui.ArtistItemsScreen
import com.ermusic.desktop.ui.ArtistScreen
import com.ermusic.desktop.ui.CollapsedPanel
import com.ermusic.desktop.ui.DownloadsScreen
import com.ermusic.desktop.ui.ShelfAllScreen
import com.ermusic.desktop.ui.BareTitleBar
import com.ermusic.desktop.ui.BrowseScreen
import com.ermusic.desktop.ui.CategoryScreen
import com.ermusic.desktop.ui.ErShape
import com.ermusic.desktop.ui.EpisodesScreen
import com.ermusic.desktop.ui.HistoryScreen
import com.ermusic.desktop.ui.HomeScreen
import com.ermusic.desktop.ui.ImmersivePlayer
import com.ermusic.desktop.ui.LibraryScreen
import com.ermusic.desktop.ui.LocalCloseWindow
import com.ermusic.desktop.ui.PlayerBar
import com.ermusic.desktop.ui.PlaylistScreen
import com.ermusic.desktop.ui.QueuePanel
import com.ermusic.desktop.ui.Route
import com.ermusic.desktop.ui.SearchPanel
import com.ermusic.desktop.ui.SearchResultsScreen
import com.ermusic.desktop.ui.SettingsScreen
import com.ermusic.desktop.ui.Sidebar
import com.ermusic.desktop.ui.Size
import com.ermusic.desktop.ui.SonaraTheme
import com.ermusic.desktop.ui.Spacing
import com.ermusic.desktop.ui.TitleBar
import com.ermusic.desktop.ui.appBackdrop
import com.ermusic.desktop.ui.handCursor
import com.ermusic.desktop.window.LinuxDesktop
import com.ermusic.desktop.window.MacApp
import com.ermusic.desktop.window.MediaKeys
import com.ermusic.desktop.window.SingleInstance
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import com.ermusic.desktop.window.WindowChrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.GraphicsEnvironment

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private const val IMAGE_CACHE_BYTES = 300L * 1024 * 1024
private val MIN_WINDOW = Dimension(1040, 660)
private const val SEEK_SMALL_MS = 5_000L
private const val SEEK_LARGE_MS = 10_000L
private const val VOLUME_STEP = 0.05f
private const val BACKGROUND_SYNC_MS = 5 * 60_000L
private const val WINDOW_SHOW_MS = 120L
private const val WINDOW_WIDTH = 1320f
private const val WINDOW_HEIGHT = 860f
private const val WINDOW_FILL = 0.94f
private const val FULLSCREEN_CHECK_MS = 500L

/** Set by a developer to look around without an account. */
private val guestAllowed = System.getenv("SONARA_GUEST") != null

/** Closing to the tray needs a tray: GNOME has none without an extension, and the window would just vanish. */
val keepsPlayingInTray: Boolean get() = !Os.isLinux || isTraySupported

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    ErrorLog.install()
    // The Windows installer, removing Sonara: ask about the data, then leave.
    if (args.firstOrNull() == Uninstall.ARGUMENT) {
        Uninstall.fromInstaller(args.getOrNull(1))
        return
    }
    LinuxDesktop.nameWindows()
    if (!SingleInstance.claim()) return
    // A restore chosen last time goes in now, before anything reads the data.
    runCatching { Backup.applyPending() }.onFailure { ErrorLog.record("restore", it) }
    // Left alone, the client sends the system's full tag (en-ID, say) as hl, and YouTube answers
    // every request with HTTP 400. The app speaks English; the country still picks the charts.
    YouTube.locale = YouTubeLocale(gl = java.util.Locale.getDefault().country.takeIf { it.length == 2 } ?: "US", hl = "en")
    val saved = AccountStore.load()
    AccountStore.use(saved)
    val player = Player(appScope)
    LyricsRepository.follow(player, appScope)
    appScope.launch(Dispatchers.IO) { Housekeeping.run() }
    MediaKeys.listen(
        onPlayPause = player::toggle,
        onNext = player::next,
        onPrevious = player::previous,
        onPlay = { if (!player.state.value.playing) player.toggle() },
        onPause = { if (player.state.value.playing) player.toggle() },
        onSeek = player::seek,
        onRaise = SingleInstance::requestShow,
    )
    if (!Os.isWindows) {
        // The Mac shows what plays in Control Center, Linux in its media controls, and both send
        // the media keys to whoever does.
        appScope.launch {
            player.state.collect { state ->
                val track = state.current
                MediaKeys.nowPlaying(track?.id, track?.title, track?.artists, track?.artUrl, state.durationMs, player.position.value, state.playing)
            }
        }
        appScope.launch {
            player.position.collect { position ->
                val track = player.state.value.current
                MediaKeys.position(position, track?.title, track?.artists)
            }
        }
    }

    application {
        // Errors are written down; a window that has just closed is no reason for an error box.
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides ErrorLog.windows) {
            val windowState =
                remember {
                    // Inside the desktop, clear of the taskbar, whatever the screen and its scaling.
                    val desktop = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                    val width = minOf(WINDOW_WIDTH, desktop.width * WINDOW_FILL)
                    val height = minOf(WINDOW_HEIGHT, desktop.height * WINDOW_FILL)
                    WindowState(
                        width = width.dp,
                        height = height.dp,
                        position = WindowPosition((desktop.x + (desktop.width - width) / 2).dp, (desktop.y + (desktop.height - height) / 2).dp),
                    )
                }
            var visible by remember { mutableStateOf(true) }
            var account by remember { mutableStateOf(saved) }
            var guest by remember { mutableStateOf(guestAllowed) }
            val app =
                remember {
                    AppState(player, appScope) {
                        player.shutdown()
                        AccountStore.clear()
                        AccountStore.use(null)
                        account = null
                        guest = false
                    }
                }

            fun close() {
                if (Settings.closeToTray.value && keepsPlayingInTray) {
                    visible = false
                } else {
                    player.shutdown()
                    exitApplication()
                }
            }

            // Brought back to the running app: from the tray, or by opening Sonara again.
            var frontRequests by remember { mutableStateOf(0) }

            fun showWindow() {
                app.miniPlayer = false
                visible = true
                windowState.isMinimized = false
                frontRequests++
            }

            val launchRequests by SingleInstance.showRequests.collectAsState()
            LaunchedEffect(launchRequests) { if (launchRequests > 0) showWindow() }

            if (Os.isMac) {
                LaunchedEffect(Unit) {
                    MacApp.install(
                        onQuit = player::shutdown,
                        onReopen = SingleInstance::requestShow,
                        onSettings = {
                            app.go(Route.Settings)
                            showWindow()
                        },
                    )
                    WindowChrome.onLeftFullscreen = { app.changeFullscreen(false) }
                }
            }

            val state by player.state.collectAsState()
            Tray(
                icon = painterResource("icon.png"),
                tooltip = state.current?.let { "${it.title} · ${it.artists}" } ?: "Sonara",
                onAction = ::showWindow,
                menu = {
                    Item("Show Sonara", onClick = ::showWindow)
                    Item(if (state.playing) "Pause" else "Play", onClick = player::toggle)
                    Item("Next", onClick = player::next)
                    Item("Previous", onClick = player::previous)
                    Separator()
                    Item("Quit") {
                        player.shutdown()
                        exitApplication()
                    }
                },
            )

            if (app.miniPlayer) {
                Window(
                    onCloseRequest = { app.miniPlayer = false },
                    title = "Sonara mini player",
                    icon = painterResource("icon.png"),
                    undecorated = true,
                    resizable = false,
                    alwaysOnTop = true,
                    state = rememberWindowState(width = 420.dp, height = 88.dp, position = WindowPosition.Aligned(Alignment.BottomEnd)),
                ) {
                    SonaraTheme(forceDark = true) { WindowDraggableArea { MiniPlayerContent(app, state) } }
                }
            }

            Window(
                onCloseRequest = ::close,
                visible = visible && !app.miniPlayer,
                title = "Sonara",
                icon = painterResource("icon.png"),
                state = windowState,
                onPreviewKeyEvent = { handleKey(app, it, ::close) },
            ) {
                if (!Os.isWindows) {
                    LaunchedEffect(app.fullscreen) {
                        while (app.fullscreen) {
                            delay(FULLSCREEN_CHECK_MS)
                            WindowChrome.checkMacFullscreen()
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    window.minimumSize = MIN_WINDOW
                    WindowChrome.install(window)
                    WindowsSession.install(window, onEnd = player::shutdown)
                    // Coming back to the app shows what was done on the phone meanwhile.
                    window.addWindowFocusListener(
                        object : WindowAdapter() {
                            override fun windowGainedFocus(event: WindowEvent?) = app.syncFromAccount()
                        },
                    )
                    while (true) {
                        delay(BACKGROUND_SYNC_MS)
                        app.syncFromAccount()
                    }
                }
                LaunchedEffect(frontRequests) {
                    if (frontRequests > 0) {
                        delay(WINDOW_SHOW_MS)
                        window.toFront()
                        WindowChrome.bringToFront()
                    }
                }
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .components { add(OkHttpNetworkFetcherFactory()) }
                        .diskCache { DiskCache.Builder().directory(AppFiles.root.resolve("images").toOkioPath()).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
                        .crossfade(true)
                        .build()
                }
                SonaraTheme {
                    CompositionLocalProvider(LocalCloseWindow provides ::close) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            if (account == null && !guest) {
                                WelcomeScreen(onSignedIn = {
                                    account = it
                                    window.toFront()
                                })
                            } else {
                                LaunchedEffect(account) { app.loadAccount() }
                                App(app)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleKey(app: AppState, event: KeyEvent, closeWindow: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val player = app.player
    val state = player.state.value
    val track = state.current
    // Command on a Mac, Control on Windows; the Mac's own window keys come first.
    val shortcut = if (Os.isMac) event.isMetaPressed else event.isCtrlPressed
    if (Os.isMac && event.isMetaPressed) {
        when {
            event.key == Key.F && event.isCtrlPressed -> app.toggleFullscreen()
            event.key == Key.W -> closeWindow()
            event.key == Key.M -> WindowChrome.minimize()
            event.key == Key.LeftBracket -> app.back()
            event.key == Key.RightBracket -> app.forward()
            else -> null
        }?.let { return true }
    }
    if (shortcut) {
        when (event.key) {
            Key.K -> app.searchFocusRequests++
            Key.L -> app.nowPlayingOpen = !app.nowPlayingOpen
            Key.S -> player.toggleShuffle()
            Key.R -> player.cycleRepeat()
            Key.D -> track?.let(app::toggleLike)
            Key.Comma -> app.go(Route.Settings)
            Key.DirectionRight -> if (app.typing) return false else player.next()
            Key.DirectionLeft -> if (app.typing) return false else player.previous()
            else -> return false
        }
        return true
    }
    if (event.isAltPressed) {
        when (event.key) {
            Key.DirectionLeft -> app.back()
            Key.DirectionRight -> app.forward()
            else -> return false
        }
        return true
    }
    when (event.key) {
        Key.F11 -> {
            app.toggleFullscreen()
            return true
        }
        Key.Escape -> {
            when {
                app.fullscreen -> app.changeFullscreen(false)
                app.nowPlayingOpen -> app.nowPlayingOpen = false
                app.sidePanel != null -> {
                    app.sidePanel = null
                    app.sidePanelDismissed = true
                }
                else -> return false
            }
            return true
        }
    }
    if (app.typing) return false
    when (event.key) {
        Key.Spacebar, Key.K -> player.toggle()
        Key.DirectionRight -> player.seekBy(SEEK_SMALL_MS)
        Key.DirectionLeft -> player.seekBy(-SEEK_SMALL_MS)
        Key.L -> player.seekBy(SEEK_LARGE_MS)
        Key.J -> player.seekBy(-SEEK_LARGE_MS)
        Key.DirectionUp -> player.setVolume(state.volume + VOLUME_STEP)
        Key.DirectionDown -> player.setVolume(state.volume - VOLUME_STEP)
        Key.M -> player.toggleMute()
        Key.F -> app.toggleFullscreen()
        Key.Q -> app.sidePanel = if (app.sidePanel == SidePanel.Queue) null else SidePanel.Queue
        else -> return false
    }
    return true
}

@Composable
private fun WelcomeScreen(onSignedIn: (Account) -> Unit) {
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    BareTitleBar()
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
    ) {
        Image(painterResource("icons/logo_mark.xml"), null, modifier = Modifier.size(WELCOME_LOGO))
        Spacer(Modifier.height(Spacing.L))
        Text("Sonara", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Spacing.S))
        Text(
            "Sign in with the Google account you use on YouTube Music.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.XL))
        if (waiting) {
            CircularProgressIndicator()
            Spacer(Modifier.height(Spacing.M))
            Text("Finish signing in in the browser window. You'll come back here by yourself.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Button(
                onClick = {
                    waiting = true
                    problem = null
                    scope.launch {
                        try {
                            EdgeSignIn.signIn()?.let { signedIn ->
                                AccountStore.save(signedIn)
                                AccountStore.use(signedIn)
                                onSignedIn(signedIn)
                            }
                        } catch (_: NoBrowserException) {
                            problem = if (Os.isLinux) "Signing in needs Chrome, Chromium, Edge, Brave or Vivaldi." else "Signing in needs Chrome, Edge, Brave or Vivaldi."
                        } catch (error: Exception) {
                            problem = "Couldn't sign in (${error.message})."
                        }
                        waiting = false
                    }
                },
                modifier = Modifier.handCursor(),
            ) { Text("Sign in with Google") }
        }
        problem?.let {
            Spacer(Modifier.height(Spacing.M))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

private val WELCOME_LOGO = 88.dp
private const val PAGE_FADE_MS = 160

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun App(app: AppState) {
    val state by app.player.state.collectAsState()
    LaunchedEffect(state.current?.id) { app.currentId = state.current?.id }

    var search by remember { mutableStateOf(TextFieldValue("")) }
    var searchOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(app.route) { searchOpen = false }
    LaunchedEffect(app.searchFocusRequests) {
        if (app.searchFocusRequests > 0) {
            focus.requestFocus()
            search = search.copy(selection = TextRange(0, search.text.length))
        }
    }

    LaunchedEffect(state.current?.id) {
        if (state.current != null && Settings.openNowPlaying.value && !app.sidePanelDismissed && app.sidePanel == null) app.sidePanel = SidePanel.NowPlaying
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Move) { WindowChrome.maximizeHovered.value = false }
            .onPointerEvent(PointerEventType.Press) { event ->
                when (event.button) {
                    PointerButton.Back -> app.back()
                    PointerButton.Forward -> app.forward()
                    else -> Unit
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            TitleBar(app, search, { search = it }, { if (it) searchOpen = true }, { searchOpen = false }, focus)
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.S), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Sidebar(app)
                CenterPane(app, state, Modifier.weight(1f).fillMaxHeight())
                if (state.current != null) {
                    when (app.sidePanel) {
                        SidePanel.NowPlaying -> NowPlayingPanel(app, state)
                        SidePanel.Queue -> QueuePanel(app, state)
                        null -> CollapsedPanel(app)
                    }
                }
            }
            PlayerBar(app, state)
        }
        if (searchOpen) {
            SearchPanel(app, search.text, onClose = {
                searchOpen = false
                focusManager.clearFocus()
            }, onPick = { query ->
                search = TextFieldValue(query, TextRange(query.length))
                app.search(query)
                searchOpen = false
                focusManager.clearFocus()
            })
        }
        app.toast?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Size.PlayerBar + Spacing.L)
                        .clip(ErShape.Pill)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = Spacing.L, vertical = Spacing.S),
            )
        }
        AnimatedVisibility(app.fullscreen && state.current != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            ImmersivePlayer(app, state)
        }
        AppDialogs(app)
    }
}

/** The middle of the window: the current page, with the lyrics over it when they are open. */
@Composable
private fun CenterPane(app: AppState, state: PlayerState, modifier: Modifier) {
    val online by Connection.online.collectAsState()
    Box(modifier.clip(ErShape.Medium).appBackdrop()) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(!online) { OfflineStrip(app) }
            AnimatedContent(
                targetState = app.route,
                transitionSpec = { fadeIn(tween(PAGE_FADE_MS)) togetherWith fadeOut(tween(PAGE_FADE_MS)) },
                label = "page",
                modifier = Modifier.weight(1f),
            ) { route ->
                when (route) {
                    Route.Home -> HomeScreen(app)
                    Route.Browse -> BrowseScreen(app)
                    Route.Library -> LibraryScreen(app)
                    Route.Settings -> SettingsScreen(app)
                    is Route.Search -> SearchResultsScreen(route.query, app)
                    is Route.Album -> AlbumScreen(route.id, app)
                    is Route.Playlist -> PlaylistScreen(route.id, app)
                    is Route.Artist -> ArtistScreen(route.id, app)
                    is Route.Category -> CategoryScreen(route, app)
                    is Route.Radio -> RadioScreen(route, app)
                    Route.Downloads -> DownloadsScreen(app)
                    Route.History -> HistoryScreen(app)
                    is Route.Episodes -> EpisodesScreen(route.list, app)
                    is Route.ShelfAll -> ShelfAllScreen(route, app)
                    is Route.ArtistItems -> ArtistItemsScreen(route, app)
                }
            }
        }
        AnimatedVisibility(
            visible = app.nowPlayingOpen && state.current != null,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) { LyricsPage(app, state) }
    }
}

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.widthIn
import com.metrolist.music.ui.theme.rememberWindowLayout
import android.content.pm.ActivityInfo
import com.metrolist.music.ui.theme.WindowLayout
import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import com.metrolist.music.ui.theme.appBackdrop
import com.metrolist.music.ui.theme.rememberUseDarkTheme
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.metrolist.music.ui.component.SheetHost
import com.metrolist.music.ui.component.LocalSheetHost
import kotlinx.coroutines.withTimeoutOrNull
import com.metrolist.music.utils.Collaborations
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.utils.YouTubePlaylistEditor
import com.metrolist.music.playback.SonaraCast
import android.view.KeyEvent
import com.metrolist.music.ui.screens.WelcomeScreen
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.ui.component.ErToastHost
import com.metrolist.music.ui.component.ErToast
import androidx.compose.ui.platform.testTag
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.ui.theme.Size as SizeTokens
import com.metrolist.music.ui.theme.Motion
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.utils.AppLanguage
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DefaultOpenTabKey
import com.metrolist.music.constants.DensityScaleKey
import com.metrolist.music.constants.DisableScreenshotKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.EnableHighRefreshRateKey
import com.metrolist.music.constants.EnableLandscapeScalingKey
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.constants.ForceBottomNavBarKey
import com.metrolist.music.constants.MiniPlayerBottomSpacing
import com.metrolist.music.constants.MiniPlayerHeight
import com.metrolist.music.constants.NavigationBarAnimationSpec
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.constants.PauseListenHistoryKey
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import com.metrolist.music.constants.StopMusicOnTaskClearKey
import com.metrolist.music.constants.UseNewMiniPlayerDesignKey
import com.metrolist.music.constants.VideoThumbnailMigrationDoneKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SearchHistory
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicService.MusicBinder
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.AppNavigationBar
import com.metrolist.music.ui.component.AppNavigationRail
import com.metrolist.music.ui.component.BottomSheetMenu
import com.metrolist.music.ui.component.BottomSheetPage
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.rememberBottomSheetState
import com.metrolist.music.ui.component.shimmer.ShimmerTheme
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.player.BottomSheetPlayer
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.screens.navigationBuilder
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.screens.settings.NavigationTab
import com.metrolist.music.ui.theme.ErMusicTheme
import com.metrolist.music.ui.utils.appBarScrollBehavior
import com.metrolist.music.ui.utils.resetHeightOffset
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.get
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.setAppLocale
import com.metrolist.music.viewmodels.HomeViewModel
import com.metrolist.music.widget.PlaylistWidgetReceiver
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    companion object {
        private const val ACTION_SEARCH = "com.metrolist.music.action.SEARCH"
        private const val ACTION_LIBRARY = "com.metrolist.music.action.LIBRARY"
        const val ACTION_OPEN_WIDGET_TARGET = "com.metrolist.music.action.OPEN_WIDGET_TARGET"
        const val EXTRA_WIDGET_TARGET_TYPE = "widget_target_type"
        const val EXTRA_WIDGET_TARGET_ID = "widget_target_id"
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var networkObserver: NetworkConnectivityObserver

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var collaborationInvite by mutableStateOf<YouTubePlaylistEditor.CollaborationInvite?>(null)
    private var collapsePlayerForIntent: (() -> Unit)? = null

    // Keep PlayerConnection as regular property - NOT mutableStateOf to prevent UI recomposition
    // when it becomes null during onStop. Only update the snapshot for Compose when needed.
    private var playerConnection: PlayerConnection? = null

    // This is the snapshot we pass to Compose - changes here trigger recomposition
    private var playerConnectionSnapshot by mutableStateOf<PlayerConnection?>(null)

    private var isServiceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (service is MusicBinder) {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playerConnectionSnapshot = playerConnection
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playerConnection?.dispose()
                // DO NOT null out playerConnection here - keep it for when service reconnects
                // DO NOT update playerConnectionSnapshot - this is the key to preventing recomposition
            }
        }

    private fun safeUnbindService(source: String) {
        if (!isServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Timber.tag("MainActivity").w(e, "Service was not bound when attempting to unbind in $source")
        } finally {
            isServiceBound = false
            playerConnection?.dispose()
            // DO NOT null out playerConnection here - keep it for reconnection
            // DO NOT update playerConnectionSnapshot - this prevents UI recomposition
        }
    }

    // While casting, the phone's volume buttons set the TV's volume, like YouTube Music.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if ((up || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && SonaraCast.state.value.casting) {
            if (event.action == KeyEvent.ACTION_DOWN) SonaraCast.stepVolume(up)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        // Coming back to the app pulls changes made on other devices (skipped within the cooldown).
        if (::syncUtils.isInitialized) syncUtils.tryAutoSync()
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        // Start the playback service explicitly once so it can outlive binding.
        // Re-issuing startForegroundService() while an existing service instance is already
        // running can trigger "did not then call startForeground" on some Android 9 devices
        // when the framework expects a fresh foreground promotion for that start request.
        if (!MusicService.isRunning) {
            val serviceIntent = Intent(this, MusicService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Cannot start foreground service from background")
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to start foreground service")
            }
        }

        // Bind to service - if already bound, this is a no-op but ensures we stay connected
        if (!isServiceBound) {
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE,
            )
            isServiceBound = true
        }
    }

    override fun onStop() {
        // Keep the service binding and PlayerConnection alive while the Activity is
        // backgrounded; the MusicService is a foreground service and keeps running.
        // Full teardown happens in onDestroy() via safeUnbindService().
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        val stopServiceOnClear = dataStore.get(StopMusicOnTaskClearKey, true) && isFinishing

        // Full cleanup - only on actual destroy
        playerConnection?.dispose()
        playerConnection = null
        playerConnectionSnapshot = null

        // Unbind before stopService: a started+bound service does not stop until all clients unbind.
        safeUnbindService("onDestroy()")

        if (stopServiceOnClear) {
            stopService(Intent(this, MusicService::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            handleShortcutIntent(intent, navController)
            handleWidgetTargetIntent(intent, navController)
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones stay in portrait; tablets and unfolded foldables (smallest width 600dp and up)
        // turn freely, with layouts made for landscape (WindowLayout).
        requestedOrientation =
            if (resources.configuration.smallestScreenWidthDp >= WindowLayout.TABLET_SMALLEST_WIDTH_DP) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Always English (AppLanguage).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            setAppLocale(this, AppLanguage.locale)
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Defer migrations to avoid blocking first frame
        lifecycleScope.launch(Dispatchers.IO) {
            val preferences = dataStore.data.first()

            if (preferences[VideoThumbnailMigrationDoneKey] != true) {
                database.repairMissingVideoThumbnails()
                safeDataStoreEdit { settings ->
                    settings[VideoThumbnailMigrationDoneKey] = true
                }
            }
        }

        setContent {
            MetrolistApp(
                playerConnection = playerConnectionSnapshot,
                database = database,
                downloadUtil = downloadUtil,
                syncUtils = syncUtils,
            )
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MetrolistApp(
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val useDarkTheme = rememberUseDarkTheme()

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val enableLandscapeScaling by rememberPreference(EnableLandscapeScalingKey, defaultValue = false)
        val userDensityScale by rememberPreference(DensityScaleKey, defaultValue = 1f)
        val pureBlack = false

        ErMusicTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
        ) {
            // Signing in is required: the library, likes and playlists all live on the account.
            val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
            val signedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
            if (!signedIn) {
                WelcomeScreen()
                return@ErMusicTheme
            }

            val currentDensity = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val containerSize = windowInfo.containerDpSize
            val smallestDimensionDp = minOf(containerSize.width, containerSize.height)

            val landscapeDensityScale =
                remember(smallestDimensionDp, enableLandscapeScaling) {
                    if (enableLandscapeScaling) {
                        when {
                            smallestDimensionDp >= 840.dp -> 1.15f
                            smallestDimensionDp >= 720.dp -> 1.1f
                            smallestDimensionDp >= 600.dp -> 1.05f
                            else -> 1.0f
                        }
                    } else {
                        1.0f
                    }
                }
            val scaledDensity: Density = remember(currentDensity, landscapeDensityScale, userDensityScale) {
                Density(
                    density = currentDensity.density * landscapeDensityScale * userDensityScale,
                    fontScale = currentDensity.fontScale,
                )
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .appBackdrop(pureBlack),
            ) {
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val cutoutInsets = WindowInsets.displayCutout
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                val navController = rememberNavController()

                val homeViewModel: HomeViewModel = hiltViewModel()
                val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val navigationItems = Screens.MainScreens
                val routeIndexMap = remember(navigationItems) {
                    navigationItems.mapIndexed { i, s -> s.route to i }.toMap()
                }
                val (forceBottomNav) = rememberPreference(ForceBottomNavBarKey, defaultValue = false)
                val (defaultOpenTabInt) = rememberPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME.name)
                val defaultOpenTab = remember(defaultOpenTabInt) {
                    try {
                        NavigationTab.valueOf(defaultOpenTabInt)
                    } catch (_: IllegalArgumentException) {
                        NavigationTab.HOME
                    }
                }
                val tabOpenedFromShortcut =
                    remember {
                        when (intent?.action) {
                            ACTION_SEARCH -> NavigationTab.SEARCH
                            ACTION_LIBRARY -> NavigationTab.LIBRARY
                            else -> null
                        }
                    }

                val topLevelScreens =
                    remember {
                        listOf(
                            Screens.Home.route,
                            Screens.Library.route,
                            "settings",
                        )
                    }

                val (query, onQueryChange) =
                    rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(TextFieldValue())
                    }

                val onSearch: (String) -> Unit =
                    remember {
                        { searchQuery ->
                            if (searchQuery.isNotEmpty()) {
                                navController.navigate(SearchRoutes.resultRoute(searchQuery))

                                if (dataStore[PauseSearchHistoryKey] != true) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            database.insert(SearchHistory(query = searchQuery))
                                        }.onFailure { throwable ->
                                            Timber
                                                .tag("MainActivity")
                                                .w(throwable, "Failed to save search history for query: %s", searchQuery)
                                        }
                                    }
                                }
                            }
                        }
                    }

                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }

                val inSearchScreen by remember {
                    derivedStateOf { currentRoute?.startsWith("search/") == true }
                }
                // Settings and the equalizer are full pages; the mini player stays out of the way there.
                val hidePlayer by remember {
                    derivedStateOf { currentRoute?.let { it.startsWith("settings") || it == "equalizer" } == true }
                }
                val navigationItemRoutes =
                    remember(navigationItems) {
                        navigationItems.map { it.route }.toSet()
                    }

                val shouldShowNavigationBar =
                    remember(currentRoute, navigationItemRoutes) {
                        currentRoute == null ||
                            navigationItemRoutes.contains(currentRoute) ||
                            currentRoute!!.startsWith("search/")
                    }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

                // The rail only in landscape; a tablet held upright keeps the bottom bar, like a phone.
                val showRail = !forceBottomNav && isLandscape && !inSearchScreen

                // On wide screens pages stop growing at ContentMaxWidth and sit in the middle; the
                // top bar takes the same margins so its title lines up with the page under it.
                val windowLayout = rememberWindowLayout()
                val railWidth = if (showRail) WindowLayout.RailWidth else 0.dp
                val contentMaxWidth = if (windowLayout.tabletLandscape) WindowLayout.ContentMaxWidth else Dp.Infinity
                val contentMargin =
                    if (windowLayout.tabletLandscape) {
                        ((windowLayout.widthDp - railWidth - WindowLayout.ContentMaxWidth) / 2).coerceAtLeast(0.dp)
                    } else {
                        0.dp
                    }

                val navPadding =
                    if (shouldShowNavigationBar && !showRail) {
                        NavigationBarHeight
                    } else {
                        0.dp
                    }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val playerBottomSheetState =
                    rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound =
                            bottomInset +
                                (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                                MiniPlayerBottomSpacing +
                                MiniPlayerHeight,
                        expandedBound = maxHeight,
                    )

                DisposableEffect(playerBottomSheetState) {
                    collapsePlayerForIntent = {
                        if (!playerBottomSheetState.isCollapsed && !playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.collapseSoft()
                        }
                    }
                    onDispose { collapsePlayerForIntent = null }
                }

                val playerReadyState =
                    playerConnection?.service?.isPlayerReady?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(false) }
                val playerReady by playerReadyState
                val activePlayerConnection = if (playerReady) playerConnection else null

                // Playing something from a list, or tapping the song that's already on, opens the player.
                LaunchedEffect(activePlayerConnection, playerBottomSheetState) {
                    activePlayerConnection?.openPlayerRequests?.collect { playerBottomSheetState.expandSoft() }
                }

                // Opening the player (from a search, a list, the mini player) puts the keyboard
                // away first; it stayed up over the player when a song was picked while typing.
                val keyboardForPlayer = LocalSoftwareKeyboardController.current
                val focusForPlayer = LocalFocusManager.current
                LaunchedEffect(playerBottomSheetState) {
                    snapshotFlow { runCatching { playerBottomSheetState.progress }.getOrDefault(0f) > PLAYER_OPENING_PROGRESS }
                        .distinctUntilChanged()
                        .collect { opening ->
                            if (opening) {
                                keyboardForPlayer?.hide()
                                focusForPlayer.clearFocus()
                            }
                        }
                }


                val playerAwareWindowInsets =
                    remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        playerBottomSheetState.isDismissed,
                        showRail,
                        hidePlayer,
                    ) {
                        var bottom = bottomInset
                        if (shouldShowNavigationBar && !showRail) {
                            bottom += NavigationBarHeight
                        }
                        if (!playerBottomSheetState.isDismissed && !hidePlayer) bottom += MiniPlayerHeight + MiniPlayerBottomSpacing
                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                    }
                appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                val topAppBarScrollBehavior =
                    appBarScrollBehavior(
                        canScroll = {
                            !inSearchScreen &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        },
                    )

                // Navigation tracking
                LaunchedEffect(navBackStackEntry) {
                    if (inSearchScreen) {
                        val searchQuery =
                            SearchRoutes.decodeQuery(
                                navBackStackEntry?.arguments?.getString("query").orEmpty(),
                            )
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length),
                            ),
                        )
                    } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }

                    // Reset scroll behavior for main navigation items
                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    // Collapse player when navigating to equalizer
                    if (navBackStackEntry?.destination?.route == "equalizer" &&
                        playerBottomSheetState.isExpanded
                    ) {
                        playerBottomSheetState.collapseSoft()
                    }

                    // Track previous tab for animations
                    navController.currentBackStackEntry?.destination?.route?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(activePlayerConnection) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                    if (player?.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                        return@LaunchedEffect
                    }

                    if (playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.collapseSoft()
                    }
                }

                DisposableEffect(activePlayerConnection, playerBottomSheetState) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                        ?: return@DisposableEffect onDispose { }
                    val listener =
                        object : Player.Listener {
                            override fun onMediaItemTransition(
                                mediaItem: MediaItem?,
                                reason: Int,
                            ) {
                                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                    mediaItem != null &&
                                    playerBottomSheetState.isDismissed
                                ) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    shouldShowTopBar = currentRoute in topLevelScreens &&
                        currentRoute != "settings"
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    if (pendingIntent != null) {
                        handleShortcutIntent(pendingIntent!!, navController)
                        handleWidgetTargetIntent(pendingIntent!!, navController)
                        handleDeepLinkIntent(pendingIntent!!, navController)
                        pendingIntent = null
                    } else {
                        handleShortcutIntent(intent, navController)
                        handleWidgetTargetIntent(intent, navController)
                        handleDeepLinkIntent(intent, navController)
                    }
                }

                DisposableEffect(Unit) {
                    val listener =
                        Consumer<Intent> { intent ->
                            handleShortcutIntent(intent, navController)
                            handleWidgetTargetIntent(intent, navController)
                            handleDeepLinkIntent(intent, navController)
                        }

                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                val currentTitleRes =
                    remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.home
                            Screens.Search.route -> R.string.search
                            Screens.Library.route -> R.string.filter_library
                            else -> null
                        }
                    }

                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
                val showHistoryButton =
                    remember(pauseListenHistory, eventCount) {
                        !(pauseListenHistory && eventCount == 0)
                    }

                val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background
                val artistNameAliases by ArtistNameAliases.aliases.collectAsStateWithLifecycle()
                val isOnline by networkObserver.networkStatus.collectAsStateWithLifecycle()

                CompositionLocalProvider(
                    LocalDatabase provides database,
                    LocalNavController provides navController,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    LocalArtistNameAliases provides artistNameAliases,
                    LocalIsOnline provides isOnline,
                ) {
                    Scaffold(
                        // Transparent: the backdrop (a gradient on Sonar Day) shows behind every page.
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            AnimatedVisibility(
                                visible = shouldShowTopBar,
                                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 200)),
                            ) {
                                Row {
                                    TopAppBar(
                                        title = {
                                            if (currentRoute == Screens.Home.route) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    androidx.compose.foundation.Image(
                                                        painter = painterResource(R.drawable.logo_mark),
                                                        contentDescription = null,
                                                    )
                                                    androidx.compose.foundation.layout.Spacer(Modifier.size(com.metrolist.music.ui.theme.Spacing.S))
                                                    Text(
                                                        text = stringResource(R.string.er_brand),
                                                        style = MaterialTheme.typography.headlineSmall,
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = currentTitleRes?.let { stringResource(it) } ?: "",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                )
                                            }
                                        },
                                        actions = {
                                            if (showHistoryButton) {
                                                IconButton(onClick = { navController.navigate("history") }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.history),
                                                        contentDescription = stringResource(R.string.history),
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { navController.navigate("settings") },
                                                modifier = Modifier.testTag("open_settings"),
                                            ) {
                                                if (accountImageUrl != null) {
                                                    AsyncImage(
                                                        model = accountImageUrl,
                                                        contentDescription = stringResource(R.string.settings),
                                                        modifier = Modifier.size(SizeTokens.Avatar).clip(CircleShape),
                                                    )
                                                } else {
                                                    Icon(
                                                        painter = painterResource(R.drawable.settings),
                                                        contentDescription = stringResource(R.string.settings),
                                                    )
                                                }
                                            }
                                        },
                                        scrollBehavior = topAppBarScrollBehavior,
                                        colors =
                                            TopAppBarDefaults.topAppBarColors(
                                                // Clear: the bar paints the backdrop's own top slice
                                                // (topBarBackdrop below), so it matches the page with
                                                // no seam and still hides what scrolls under it.
                                                containerColor = Color.Transparent,
                                                scrolledContainerColor = Color.Transparent,
                                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        modifier =
                                            Modifier.topBarBackdrop(pureBlack).windowInsetsPadding(
                                                if (showRail) {
                                                    WindowInsets(left = railWidth + contentMargin, right = contentMargin)
                                                        .add(cutoutInsets.only(WindowInsetsSides.Start))
                                                } else {
                                                    cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                                                },
                                            ),
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            val currentBackStackEntry = navController.currentBackStackEntry // reads reactively outside remember

                            val onNavItemClick: (Screens, Boolean) -> Unit =
                                remember(
                                    navController,
                                    coroutineScope,
                                    topAppBarScrollBehavior,
                                    playerBottomSheetState,
                                    currentBackStackEntry,
                                ) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        if (isSelected) {
                                            val targetEntry =
                                                try {
                                                    val route = navController.currentBackStackEntry?.destination?.route
                                                    if (route == SearchRoutes.ROUTE || route == "search_input") {
                                                        // For search screens, use search_input entry
                                                        navController.getBackStackEntry("search_input")
                                                    } else {
                                                        // For other screens, use current entry
                                                        navController.currentBackStackEntry
                                                    }
                                                } catch (e: Exception) {
                                                    null
                                                }

                                            // Use appropriate key based on screen type
                                            if (screen == Screens.Search) {
                                                val current = targetEntry?.savedStateHandle?.get<Int>("scrollToTopCount") ?: 0
                                                targetEntry?.savedStateHandle?.set("scrollToTopCount", current + 1)
                                            } else {
                                                targetEntry?.savedStateHandle?.set("scrollToTop", true)
                                            }

                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }

                            // Pre-calculate values for graphicsLayer to avoid reading state during composition
                            val navBarTotalHeight = bottomInset + NavigationBarHeight

                            if (!showRail) {
                                Box {
                                    if (activePlayerConnection != null && !hidePlayer) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )
                                    }

                                    AppNavigationBar(
                                        navigationItems = navigationItems,
                                        currentRoute = currentRoute,
                                        onItemClick = onNavItemClick,
                                        pureBlack = pureBlack,
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInset + navPadding)
                                                // Use graphicsLayer instead of offset to avoid recomposition
                                                // graphicsLayer runs during draw phase, not composition phase
                                                .graphicsLayer {
                                                    val navBarHeightPx = navigationBarHeight.toPx()
                                                    val totalHeightPx = navBarTotalHeight.toPx()

                                                    translationY =
                                                        if (navBarHeightPx == 0f) {
                                                            totalHeightPx
                                                        } else {
                                                            // Read progress only during draw phase
                                                            val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                            val slideOffset = totalHeightPx * progress
                                                            val hideOffset =
                                                                totalHeightPx * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                                            slideOffset + hideOffset
                                                        }
                                                },
                                    )

                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInsetDp)
                                                // Use graphicsLayer for background color changes
                                                .graphicsLayer {
                                                    val progress = playerBottomSheetState.progress
                                                    alpha =
                                                        if (progress > 0f ||
                                                            !shouldShowNavigationBar
                                                        ) {
                                                            0f
                                                        } else {
                                                            1f
                                                        }
                                                }.background(baseBg),
                                    )
                                }
                            } else {
                                if (activePlayerConnection != null && !hidePlayer) {
                                    BottomSheetPlayer(
                                        state = playerBottomSheetState,
                                        navController = navController,
                                        pureBlack = pureBlack,
                                    )
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                            // Use graphicsLayer for background color changes
                                            .graphicsLayer {
                                                val progress = playerBottomSheetState.progress
                                                alpha =
                                                    if (progress > 0f || !shouldShowNavigationBar) 0f else 1f
                                            }.background(baseBg),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            val onRailItemClick: (Screens, Boolean) -> Unit =
                                remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }

                                        if (isSelected) {
                                            navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }

                            if (showRail) {
                                AppNavigationRail(
                                    navigationItems = navigationItems,
                                    currentRoute = currentRoute,
                                    onItemClick = onRailItemClick,
                                    pureBlack = pureBlack,
                                )
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                // NavHost with animations (Material 3 Expressive style)
                                NavHost(
                                    navController = navController,
                                    startDestination = Screens.Home.route,
                                    enterTransition = Motion.navEnter,
                                    exitTransition = Motion.navExit,
                                    popEnterTransition = Motion.navPopEnter,
                                    popExitTransition = Motion.navPopExit,
                                    modifier =
                                        Modifier
                                            .widthIn(max = contentMaxWidth)
                                            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                                ) {
                                    navigationBuilder(
                                        navController = navController,
                                        scrollBehavior = topAppBarScrollBehavior,
                                        activity = this@MainActivity,
                                        snackbarHostState = snackbarHostState,
                                    )
                                }
                            }
                        }
                    }

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    SheetHost(LocalSheetHost.current)

                    BottomSheetPage(
                        state = LocalBottomSheetPageState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    ErToastHost(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(playerAwareWindowInsets.only(WindowInsetsSides.Bottom))
                            .padding(bottom = com.metrolist.music.ui.theme.Spacing.M),
                    )

                    collaborationInvite?.let { invite ->
                        val joinedText = stringResource(R.string.er_joined_playlist)
                        DefaultDialog(
                            onDismiss = { collaborationInvite = null },
                            title = { Text(invite.title ?: stringResource(R.string.er_invite_title)) },
                            buttons = {
                                TextButton(onClick = { collaborationInvite = null }) {
                                    Text(stringResource(R.string.er_invite_decline))
                                }
                                TextButton(
                                    onClick = {
                                        collaborationInvite = null
                                        com.metrolist.music.utils.appScope.launch {
                                            when (YouTubePlaylistEditor.joinCollaboration(invite)) {
                                                YouTubePlaylistEditor.JoinResult.JOINED -> {
                                                    Collaborations.markJoined(this@MainActivity, invite.playlistId)
                                                    withContext(Dispatchers.Main) { ErToast.show(this@MainActivity, joinedText) }
                                                    openJoinedPlaylist(invite.playlistId, navController)
                                                }
                                                YouTubePlaylistEditor.JoinResult.ALREADY_JOINED ->
                                                    alreadyJoined(invite.playlistId, navController)
                                                YouTubePlaylistEditor.JoinResult.FAILED ->
                                                    withContext(Dispatchers.Main) {
                                                        ErToast.show(this@MainActivity, R.string.er_join_failed)
                                                    }
                                            }
                                        }
                                    },
                                ) { Text(stringResource(R.string.er_invite_join)) }
                            },
                        ) {
                            Text(
                                invite.message ?: stringResource(R.string.er_invite_message),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            onDismiss = { sharedSong = null },
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
    }

    private fun handleShortcutIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val route = when (intent.action) {
            ACTION_SEARCH -> Screens.Search.route
            ACTION_LIBRARY -> Screens.Library.route
            else -> return
        }
        intent.action = null
        collapsePlayerForIntent?.invoke()
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    private sealed class WidgetTargetRoute(val route: String) {
        data class LocalPlaylist(val id: String) : WidgetTargetRoute("local_playlist/$id")
        data class OnlinePlaylist(val id: String) : WidgetTargetRoute("online_playlist/$id")
        data object LikedSongs : WidgetTargetRoute("auto_playlist/liked")
        data object DownloadedSongs : WidgetTargetRoute("auto_playlist/downloaded")
        data class TopSongs(val limit: String) : WidgetTargetRoute("top_playlist/$limit")
    }

    private fun handleWidgetTargetIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action != ACTION_OPEN_WIDGET_TARGET) return

        val targetType = intent.getStringExtra(EXTRA_WIDGET_TARGET_TYPE)
        val targetId = intent.getStringExtra(EXTRA_WIDGET_TARGET_ID)
        intent.action = null
        intent.removeExtra(EXTRA_WIDGET_TARGET_TYPE)
        intent.removeExtra(EXTRA_WIDGET_TARGET_ID)

        val normalizedTargetId = targetId?.takeIf { it.isNotBlank() }

        val targetRoute = when (targetType) {
            PlaylistWidgetReceiver.TARGET_TYPE_LOCAL ->
                normalizedTargetId?.let { WidgetTargetRoute.LocalPlaylist(it) }

            PlaylistWidgetReceiver.TARGET_TYPE_ONLINE ->
                normalizedTargetId?.let { WidgetTargetRoute.OnlinePlaylist(it) }

            PlaylistWidgetReceiver.TARGET_TYPE_LIKED ->
                WidgetTargetRoute.LikedSongs

            PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED ->
                WidgetTargetRoute.DownloadedSongs

            PlaylistWidgetReceiver.TARGET_TYPE_TOP ->
                WidgetTargetRoute.TopSongs(normalizedTargetId ?: "50")

            else -> null
        } ?: return

        collapsePlayerForIntent?.invoke()
        navController.navigate(targetRoute.route)
    }

    private suspend fun alreadyJoined(
        playlistId: String,
        navController: NavHostController,
    ) {
        Collaborations.markJoined(this, playlistId)
        withContext(Dispatchers.Main) { ErToast.show(this@MainActivity, R.string.er_already_joined) }
        openJoinedPlaylist(playlistId, navController)
    }

    /** Opens SONARA's own copy of a joined playlist (where songs can be added) once it is synced. */
    private suspend fun openJoinedPlaylist(
        playlistId: String,
        navController: NavHostController,
    ) {
        val bare = playlistId.removePrefix("VL")
        if (database.playlistByBrowseId(bare).first() == null) {
            withContext(Dispatchers.Main) { syncUtils.syncSavedPlaylists() }
        }
        val local = withTimeoutOrNull(JOINED_SYNC_WAIT_MS) {
            database.playlistByBrowseId(bare).first { it != null }
        } ?: return
        withContext(Dispatchers.Main) {
            navController.navigate("local_playlist/${local.id}") { launchSingleTop = true }
        }
    }

    private fun handleDeepLinkIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope
        // An opened link takes over whatever is on screen (full player, Settings, ...): the player
        // folds down and the link's page opens on top; a song link opens the player instead.
        val focus = { route: String ->
            collapsePlayerForIntent?.invoke()
            navController.navigate(route) { launchSingleTop = true }
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> {
                val playlistId = uri.getQueryParameter("list")
                val joinToken = uri.getQueryParameter("jct")
                if (joinToken != null && !playlistId.isNullOrBlank()) {
                    // An invite link: ask to join, like YouTube Music's "Collaborate on ..." dialog.
                    com.metrolist.music.utils.appScope.launch {
                        // Already in (joined here, or YouTube shows no invite): say so and open it.
                        val invite =
                            if (Collaborations.isJoined(this@MainActivity, playlistId)) null
                            else YouTubePlaylistEditor.invite(playlistId, joinToken)
                        if (invite != null && (invite.title != null || invite.message != null)) {
                            withContext(Dispatchers.Main) { collaborationInvite = invite }
                        } else {
                            alreadyJoined(playlistId, navController)
                        }
                    }
                }
                if (playlistId.isNullOrBlank() || playlistId.equals("null", ignoreCase = true)) {
                    ErToast.show(this, R.string.playlist_unavailable)
                    return
                }

                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .albumSongs(playlistId)
                            .onSuccess { songs ->
                                songs.firstOrNull()?.album?.id?.let { browseId ->
                                    withContext(Dispatchers.Main) {
                                        focus("album/$browseId")
                                    }
                                }
                            }.onFailure { reportException(it) }
                    }
                } else {
                    focus("online_playlist/$playlistId")
                }
            }

            "browse" -> {
                uri.lastPathSegment?.let { browseId ->
                    focus("album/$browseId")
                }
            }

            "channel", "c" -> {
                uri.lastPathSegment?.let { artistId ->
                    focus("artist/$artistId")
                }
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    focus(SearchRoutes.resultRoute(it))
                }
            }

            else -> {
                val videoId =
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }

                val playlistId = uri.getQueryParameter("list")

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .queue(listOf(videoId), playlistId)
                            .onSuccess { queue ->
                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                            queue.firstOrNull()?.toMediaMetadata(),
                                        ),
                                    )
                                    playerConnection?.openPlayer()
                                }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .queue(null, playlistId)
                            .onSuccess { queue ->
                                val firstItem = queue.firstOrNull()
                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                            firstItem?.toMediaMetadata(),
                                        ),
                                    )
                                    playerConnection?.openPlayer()
                                }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
}

/** How long to wait for a just-joined playlist to arrive with the library sync. */
private const val JOINED_SYNC_WAIT_MS = 20_000L

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalArtistNameAliases = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** Whether the device currently has a validated internet connection. */
val LocalIsOnline = compositionLocalOf { true }
val LocalIsPlayerExpanded = compositionLocalOf { false }

/** How far the player sheet is pulled up before the keyboard is put away. */
private const val PLAYER_OPENING_PROGRESS = 0.05f

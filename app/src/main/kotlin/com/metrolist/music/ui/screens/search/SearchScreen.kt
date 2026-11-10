/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import com.metrolist.music.ui.component.ErToast
import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.metrolist.music.LocalIsOnline
import com.metrolist.music.ui.component.OfflineState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import com.metrolist.music.LocalNavController
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalIsPlayerExpanded
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.constants.OfflineModeKey
import com.metrolist.music.constants.SearchSource
import com.metrolist.music.constants.SearchSourceKey
import com.metrolist.music.db.entities.SearchHistory
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    pureBlack: Boolean,
    savedStateHandle: SavedStateHandle,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lazyListState = rememberLazyListState()
    var isHandlingScrollToTop by remember { mutableStateOf(false) }

    val scrollToTopCount by savedStateHandle.getStateFlow("scrollToTopCount", 0).collectAsStateWithLifecycle(initialValue = 0)

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            isHandlingScrollToTop = true

            kotlinx.coroutines.delay(100)

            if (!isPlayerExpanded) {
                focusManager.clearFocus(force = true)
                kotlinx.coroutines.delay(50)
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {
                }
            }

            kotlinx.coroutines.delay(500)
            isHandlingScrollToTop = false
        }
    }

    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    val isOnline = LocalIsOnline.current
    val offlineMode = rememberPreference(OfflineModeKey, defaultValue = false).value || !isOnline
    // Search is YouTube Music; downloads are searched only while offline.
    val effectiveSearchSource = if (offlineMode) SearchSource.LOCAL else SearchSource.ONLINE
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    var focused by rememberSaveable { mutableStateOf(query.text.isNotEmpty()) }
    LaunchedEffect(focused) {
        if (focused && !isPlayerExpanded) {
            kotlinx.coroutines.delay(100)
            runCatching {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
    BackHandler(enabled = focused) {
        focusManager.clearFocus(force = true)
        query = TextFieldValue()
        focused = false
    }

    fun handleSearch(searchQuery: String) {
        if (searchQuery.isEmpty()) {
            return
        }

        focusManager.clearFocus()

        if (offlineMode) {
            query = TextFieldValue(searchQuery)
            return
        }

        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
            is YouTubeUrlParser.ParsedUrl.Video -> {
                playerConnection?.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(videoId = parsedUrl.id),
                    ),
                )
            }

            is YouTubeUrlParser.ParsedUrl.Playlist -> {
                navController.navigate("online_playlist/${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Album -> {
                navController.navigate("album/MPREb_${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                navController.navigate("artist/${parsedUrl.id}")
            }

            null -> {
                navController.navigate(SearchRoutes.resultRoute(searchQuery))
            }
        }

        if (!pauseSearchHistory) {
            coroutineScope.launch(Dispatchers.IO) {
                database.query {
                    insert(SearchHistory(query = searchQuery))
                }
            }
        }
    }

    val onSearch: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    val onSearchFromSuggestion: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    val context = LocalContext.current
    val voiceAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var showVoice by remember { mutableStateOf(false) }
    var showRecognize by remember { mutableStateOf(false) }
    // Both mic buttons share one permission request; this says which sheet opens once granted.
    var recognizeAfterGrant by remember { mutableStateOf(false) }
    val micPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (recognizeAfterGrant) showRecognize = true else showVoice = true
            } else {
                // Refused (or "don't ask again"): say why nothing opens instead of doing nothing.
                ErToast.show(context, R.string.er_mic_permission)
            }
        }
    val withMic: (recognize: Boolean) -> Unit = { recognize ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        recognizeAfterGrant = recognize
        when {
            !granted -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            recognize -> showRecognize = true
            else -> showVoice = true
        }
    }
    val startVoiceSearch: (() -> Unit)? =
        if (voiceAvailable && !offlineMode) {
            { withMic(false) }
        } else {
            null
        }
    // Song recognition needs Shazam online, but not Google's speech service.
    val startRecognize: (() -> Unit)? = if (!offlineMode) ({ withMic(true) }) else null
    if (showRecognize) {
        SongRecognitionSheet(
            onSearch = { found ->
                showRecognize = false
                focused = true
                query = TextFieldValue(found, TextRange(found.length))
                onSearch(found)
            },
            onDismiss = { showRecognize = false },
        )
    }
    if (showVoice) {
        VoiceSearchSheet(
            onResult = { spoken ->
                showVoice = false
                focused = true
                query = TextFieldValue(spoken, TextRange(spoken.length))
                onSearch(spoken)
            },
            onDismiss = { showVoice = false },
        )
    }

    if (!focused) {
        SearchDiscover(onFocusSearch = { focused = true }, onVoiceSearch = startVoiceSearch, onRecognize = startRecognize)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                            textStyle =
                                TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (query.text.isEmpty()) {
                                    Text(
                                        text =
                                            stringResource(
                                                when (effectiveSearchSource) {
                                                    SearchSource.LOCAL -> R.string.search_library
                                                    SearchSource.ONLINE -> R.string.er_search_field_hint
                                                },
                                            ),
                                        style =
                                            TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                fontSize = 16.sp,
                                            ),
                                    )
                                }
                                innerTextField()
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onSearch = { onSearch(query.text) },
                                ),
                        )

                        Row {
                            if (query.text.isEmpty() && startVoiceSearch != null) {
                                IconButton(onClick = startVoiceSearch) {
                                    Icon(
                                        painter = painterResource(R.drawable.mic),
                                        contentDescription = stringResource(R.string.er_voice_search),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            if (query.text.isEmpty() && startRecognize != null) {
                                IconButton(onClick = startRecognize) {
                                    Icon(
                                        painter = painterResource(R.drawable.graphic_eq),
                                        contentDescription = stringResource(R.string.er_recognize_song),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            if (query.text.isNotEmpty()) {
                                IconButton(onClick = { query = TextFieldValue("") }) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus(force = true)
                        query = TextFieldValue()
                        focused = false
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.dismiss),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = backdropTopBarColors(),
                modifier = Modifier.topBarBackdrop(pureBlack),
            )
        },
        containerColor = if (pureBlack) Color.Black else Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .padding(top = paddingValues.calculateTopPadding())
                    .fillMaxSize(),
        ) {
            when (effectiveSearchSource) {
                SearchSource.LOCAL -> if (!isOnline && query.text.isEmpty()) {
                    OfflineState(onOpenDownloads = { navController.navigate("downloads") })
                } else {
                    LocalSearchScreen(
                        query = query.text,
                        onDismiss = { navController.navigateUp() },
                        pureBlack = pureBlack,
                    )
                }

                SearchSource.ONLINE -> {
                    OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = { query = it },
                        onSearch = onSearchFromSuggestion,
                        onDismiss = { /* Don't dismiss when searching from suggestions */ },
                        pureBlack = pureBlack,
                    )
                }
            }
        }
    }

    // Handle lifecycle events to manage keyboard visibility
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (isHandlingScrollToTop) return@LifecycleEventObserver
                        // Always hide keyboard when resuming if player is expanded
                        if (isPlayerExpanded) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        if (isHandlingScrollToTop) return@LifecycleEventObserver
                        // Clear focus when pausing to prevent keyboard from showing on resume
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial check - hide keyboard if player is expanded
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

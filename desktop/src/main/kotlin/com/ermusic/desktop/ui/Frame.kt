/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ermusic.desktop.Os
import com.ermusic.desktop.data.Downloads
import com.ermusic.desktop.data.RecentSearches
import com.ermusic.desktop.window.WindowChrome
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.delay

private val CaptionButtonWidth = 46.dp
private val SearchWidth = 520.dp
private const val SUGGEST_DEBOUNCE_MS = 180L
private val GlyphStroke = 1.dp
private val GlyphSize = 10.dp

private enum class Caption { Minimize, Maximize, Restore, Close }

/** Minimize, maximize, close, drawn the way Windows 11 draws them. */
@Composable
private fun CaptionButtons() {
    val maximized by WindowChrome.maximized.collectAsState()
    Row(Modifier.fillMaxHeight()) {
        CaptionButton(Caption.Minimize, with(WindowChrome) { Modifier.captionControl() }) { WindowChrome.minimize() }
        val hovered by WindowChrome.maximizeHovered.collectAsState()
        CaptionButton(if (maximized) Caption.Restore else Caption.Maximize, with(WindowChrome) { Modifier.maximizeButtonArea() }, hoveredFromFrame = hovered) { WindowChrome.toggleMaximize() }
        CaptionButton(Caption.Close, with(WindowChrome) { Modifier.captionControl() }, onClick = LocalCloseWindow.current)
    }
}

val LocalCloseWindow = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

@Composable
private fun CaptionButton(kind: Caption, modifier: Modifier, hoveredFromFrame: Boolean = false, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hoveredHere by hover.collectIsHoveredAsState()
    val hovered = hoveredHere || hoveredFromFrame
    val glyph = if (hovered && kind == Caption.Close) Color.White else MaterialTheme.colorScheme.onSurface
    val background =
        when {
            !hovered -> Color.Transparent
            kind == Caption.Close -> CLOSE_HOVER
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.width(CaptionButtonWidth).fillMaxHeight().hoverable(hover).background(background).clickable(onClick = onClick),
    ) {
        Canvas(Modifier.size(GlyphSize)) {
            val stroke = Stroke(GlyphStroke.toPx())
            val side = size.width
            when (kind) {
                Caption.Minimize -> drawLine(glyph, Offset(0f, side / 2), Offset(side, side / 2), stroke.width)
                Caption.Maximize -> drawRect(glyph, style = stroke)
                Caption.Restore -> {
                    val back = side * RESTORE_OFFSET
                    drawRect(glyph, topLeft = Offset(0f, back), size = GeometrySize(side - back, side - back), style = stroke)
                    drawLine(glyph, Offset(back, back), Offset(back, 0f), stroke.width)
                    drawLine(glyph, Offset(back, 0f), Offset(side, 0f), stroke.width)
                    drawLine(glyph, Offset(side, 0f), Offset(side, side - back), stroke.width)
                    drawLine(glyph, Offset(side, side - back), Offset(side - back, side - back), stroke.width)
                }
                Caption.Close -> {
                    drawLine(glyph, Offset.Zero, Offset(side, side), stroke.width)
                    drawLine(glyph, Offset(side, 0f), Offset(0f, side), stroke.width)
                }
            }
        }
    }
}

private val CLOSE_HOVER = Color(0xFFC42B1C)
private const val RESTORE_OFFSET = 0.22f

/** Just the drag area and window buttons, for the sign-in screen. */
@Composable
fun BareTitleBar() {
    val density = LocalDensity.current
    LaunchedEffect(density) { WindowChrome.setCaptionHeight(with(density) { Size.TitleBar.roundToPx() }) }
    val active by WindowChrome.active.collectAsState()
    if (!active) return
    Box(with(WindowChrome) { Modifier.windowDragArea() }.fillMaxWidth().height(Size.TitleBar)) {
        // A Mac keeps its own traffic lights at the top left.
        if (!Os.isMac) Box(Modifier.align(Alignment.TopEnd)) { CaptionButtons() }
    }
}

/** The top of the window: history arrows, search, the account, and the window buttons. */
@Composable
fun TitleBar(app: AppState, search: TextFieldValue, onSearchChange: (TextFieldValue) -> Unit, onSearchFocus: (Boolean) -> Unit, onSearchDone: () -> Unit, focus: FocusRequester) {
    val density = LocalDensity.current
    LaunchedEffect(density) { WindowChrome.setCaptionHeight(with(density) { Size.TitleBar.roundToPx() }) }
    val active by WindowChrome.active.collectAsState()
    val control = with(WindowChrome) { Modifier.captionControl() }
    val controlTwo = with(WindowChrome) { Modifier.captionControl() }
    Box(with(WindowChrome) { Modifier.windowDragArea() }.fillMaxWidth().height(Size.TitleBar)) {
        val start = if (Os.isMac) Size.MacWindowButtons else Spacing.M
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart).padding(start = start)) {
            Image(painterResource("icons/logo_mark.xml"), "Sonara", modifier = Modifier.size(Size.TitleLogo))
            Spacer(Modifier.width(Spacing.S))
            Text("Sonara", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(Spacing.L))
            SymbolButton("arrow_back", "Back", control.size(Size.Control), tint = enabledTint(app.canGoBack), iconSize = Size.IconSmall, onClick = app::back)
            SymbolButton("arrow_forward", "Forward", controlTwo.size(Size.Control), tint = enabledTint(app.canGoForward), iconSize = Size.IconSmall, onClick = app::forward)
        }
        SearchField(app, search, onSearchChange, onSearchFocus, onSearchDone, focus, Modifier.align(Alignment.Center))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
            ProfileButton(app)
            Spacer(Modifier.width(Spacing.S))
            if (Os.isMac) Spacer(Modifier.width(Spacing.S)) else if (active) CaptionButtons()
        }
    }
}

@Composable
private fun enabledTint(enabled: Boolean) = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline

@Composable
private fun SearchField(app: AppState, value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit, onDone: () -> Unit, focus: FocusRequester, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            with(WindowChrome) { modifier.captionControl() }
                .width(SearchWidth)
                .height(Size.Control)
                .clip(ErShape.Pill)
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = Spacing.M),
    ) {
        Icon(icon("search"), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Size.IconSmall))
        Spacer(Modifier.width(Spacing.S))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.text.isEmpty()) {
                Text("Search songs, artists, playlists", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    app.search(value.text)
                    onDone()
                    focusManager.clearFocus()
                }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged {
                            app.typing = it.isFocused
                            onFocus(it.isFocused)
                        }.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                app.search(value.text)
                                onDone()
                                focusManager.clearFocus()
                                true
                            } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                onDone()
                                focusManager.clearFocus()
                                true
                            } else {
                                false
                            }
                        },
            )
        }
        Text("${Os.shortcutKey}K", style = MaterialTheme.typography.labelSmall, color = colors.outline)
    }
}

@Composable
private fun ProfileButton(app: AppState) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(with(WindowChrome) { Modifier.captionControl() }) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(Size.Control).clip(CircleShape).background(colors.primaryContainer).clickable { open = true }.handCursor(),
        ) {
            val url = app.profile?.thumbnailUrl
            if (url != null) Art(url, Modifier.fillMaxSize(), CircleShape, pixels = 96) else Icon(icon("person"), "Account", tint = colors.primary, modifier = Modifier.size(Size.IconSmall))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = ErShape.Medium, containerColor = colors.surfaceContainerHighest) {
            app.profile?.let { profile ->
                Column(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    profile.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1) }
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
            AppMenuItem("Settings", "settings") {
                open = false
                app.go(Route.Settings)
            }
            HorizontalDivider(color = colors.outlineVariant)
            AppMenuItem("Sign out", "logout") {
                open = false
                app.onSignOut()
            }
        }
    }
}

/** What opens under the search field: recents and shortcuts, then live suggestions as you type. */
@Composable
fun SearchPanel(app: AppState, query: String, onClose: () -> Unit, onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var suggestions by remember { mutableStateOf<SearchSuggestions?>(null) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = null
            return@LaunchedEffect
        }
        delay(SUGGEST_DEBOUNCE_MS)
        suggestions = YouTube.searchSuggestions(query).getOrNull()
    }
    Box(Modifier.fillMaxSize().padding(top = Size.TitleBar).clickable(interactionSource = null, indication = null, onClick = onClose), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .width(SearchWidth)
                .clip(ErShape.Medium)
                .background(colors.surfaceContainerHighest)
                .clickable(interactionSource = null, indication = null) {}
                .padding(Spacing.S),
        ) {
            if (query.isBlank()) {
                if (RecentSearches.queries.isNotEmpty()) {
                    PanelLabel("Recent searches")
                    RecentSearches.queries.forEach { recent ->
                        PanelRow("history", recent, onClick = { onPick(recent) }, trailing = { SymbolButton("close", "Remove", Modifier.size(Size.Control), tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { RecentSearches.remove(recent) } })
                    }
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = Spacing.XS))
                }
                PanelLabel("Explore")
                Row(Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PanelChip("Browse all") {
                        onClose()
                        app.go(Route.Browse)
                    }
                    PanelChip("Your library") {
                        onClose()
                        app.go(Route.Library)
                    }
                }
            } else {
                val current = suggestions
                PanelRow("search", "Search for “$query”", onClick = { onPick(query) })
                current?.queries?.take(SUGGESTION_QUERIES)?.forEach { suggestion -> PanelRow("search", suggestion, onClick = { onPick(suggestion) }) }
                if (!current?.recommendedItems.isNullOrEmpty()) {
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = Spacing.XS))
                    LazyColumn(Modifier.height(SUGGESTION_LIST_HEIGHT)) {
                        items(current!!.recommendedItems, key = YTItem::id, contentType = { it::class }) { item ->
                            ItemRow(item, app, onClick = {
                                onClose()
                                app.activate(item)
                            })
                        }
                    }
                }
            }
        }
    }
}

private const val SUGGESTION_QUERIES = 5
private val SUGGESTION_LIST_HEIGHT = 220.dp

@Composable
private fun PanelLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.S))
}

@Composable
private fun PanelChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(ErShape.Pill).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick).handCursor().padding(horizontal = Spacing.M, vertical = Spacing.S),
    )
}

@Composable
private fun PanelRow(symbol: String, text: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(PANEL_ROW_HEIGHT).clip(ErShape.Small).clickable(onClick = onClick).handCursor().padding(horizontal = Spacing.S),
    ) {
        Icon(icon(symbol), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Size.IconSmall))
        Spacer(Modifier.width(Spacing.M))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

private val PANEL_ROW_HEIGHT = 40.dp

// ---- Sidebar ----

@Composable
private fun NavRow(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(NAV_ROW_HEIGHT)
                .clip(ErShape.Small)
                .background(if (selected) colors.primaryContainer else Color.Transparent)
                .clickable(onClick = onClick)
                .handCursor()
                .padding(horizontal = Spacing.M),
    ) {
        Icon(icon(symbol), label, tint = if (selected) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(Size.Icon))
        Spacer(Modifier.width(Spacing.M))
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) colors.onSurface else colors.onSurfaceVariant)
    }
}

private val NAV_ROW_HEIGHT = 44.dp

private enum class LibrarySort(val label: String) { Recents("Recents"), Alphabetical("A-Z") }

@Composable
fun Sidebar(app: AppState) {
    val colors = MaterialTheme.colorScheme
    val route = app.route
    var sort by remember { mutableStateOf(LibrarySort.Recents) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    Column(Modifier.width(Size.Sidebar).fillMaxHeight().panel().padding(Spacing.S)) {
        NavRow(if (route == Route.Home) "nav_home_on" else "nav_home_off", "Home", route == Route.Home) { app.go(Route.Home) }
        NavRow("explore_outlined", "Browse", route == Route.Browse) { app.go(Route.Browse) }
        NavRow(if (route == Route.Library) "nav_library_on" else "nav_library_off", "Library", route == Route.Library) { app.go(Route.Library) }
        NavRow("history", "History", route == Route.History) { app.go(Route.History) }
        HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = Spacing.S))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = Spacing.M)) {
            Text("Your Library", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Box {
                SymbolButton("add", "Create", Modifier.size(Size.Control), tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { createOpen = true }
                DropdownMenu(expanded = createOpen, onDismissRequest = { createOpen = false }, shape = ErShape.Medium, containerColor = colors.surfaceContainerHighest) {
                    AppMenuItem("New playlist", "add") {
                        createOpen = false
                        app.newPlaylistFor = emptyList()
                    }
                    AppMenuItem("Import or join from a link", "link") {
                        createOpen = false
                        app.importing = true
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (searching) {
                PageFilter(query, "Search your library", app, Modifier.weight(1f)) { query = it }
                SymbolButton("close", "Close search", Modifier.size(Size.Control), tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) {
                    searching = false
                    query = ""
                }
            } else {
                SymbolButton("search", "Search your library", Modifier.size(Size.Control), tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { searching = true }
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(ErShape.Small).clickable { sortOpen = true }.handCursor().padding(horizontal = Spacing.S, vertical = Spacing.XS),
                    ) {
                        Text(sort.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.XS))
                        Icon(icon("sort"), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Size.IconSmall))
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }, shape = ErShape.Medium, containerColor = colors.surfaceContainerHighest) {
                        LibrarySort.entries.forEach { option ->
                            AppMenuItem(option.label, if (option == sort) "check" else "sort") {
                                sort = option
                                sortOpen = false
                            }
                        }
                    }
                }
            }
        }
        val text = query.trim()
        val matching = app.playlists.filter { text.isEmpty() || it.title.contains(text, ignoreCase = true) }
        val entries = if (sort == LibrarySort.Alphabetical) matching.sortedBy { it.title.lowercase() } else matching
        ScrollingColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (text.isEmpty()) {
                item(key = "liked", contentType = "row") {
                    SidebarEntry("Liked songs", if (app.likedIds.isEmpty()) "Playlist" else "Playlist · " + songCount(app.likedIds.size), null, "favorite", LIKED_START to LIKED_END, route == Route.Playlist(LIKED_PLAYLIST_ID), null) { app.go(Route.Playlist(LIKED_PLAYLIST_ID)) }
                }
                item(key = "downloads", contentType = "row") {
                    SidebarEntry("Downloads", songCount(Downloads.tracks.size), null, "download", DOWNLOADS_START to DOWNLOADS_END, route == Route.Downloads, null) { app.go(Route.Downloads) }
                }
            }
            items(entries, key = { it.id }, contentType = { "row" }) { playlist ->
                var menuAt by remember { mutableStateOf<IntOffset?>(null) }
                Box {
                    SidebarEntry(playlist.title, listOfNotNull("Playlist", playlist.author?.name).joinToString(" · "), playlist.thumbnail, "music_note", LIKED_START to LIKED_END, route == Route.Playlist(playlist.id), { menuAt = it }) { app.open(playlist) }
                    ItemMenu(playlist, app, menuAt) { menuAt = null }
                }
            }
        }
    }
}

@Composable
private fun SidebarEntry(title: String, subtitle: String, art: String?, symbol: String, tile: Pair<Color, Color>, selected: Boolean, onRightClick: ((IntOffset) -> Unit)?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = Spacing.S)
                .height(SIDEBAR_ROW_HEIGHT)
                .clip(ErShape.Small)
                .background(if (selected) colors.onSurface.copy(alpha = HOVER_ALPHA * 2) else Color.Transparent)
                .clickable(onClick = onClick)
                .handCursor()
                .then(onRightClick?.let { Modifier.onRightClick(it) } ?: Modifier)
                .padding(horizontal = Spacing.S),
    ) {
        if (art != null) {
            Art(art, Modifier.size(SIDEBAR_ART), ErShape.Small, pixels = 96)
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(SIDEBAR_ART).clip(ErShape.Small).background(Brush.linearGradient(listOf(tile.first, tile.second))),
            ) { Icon(icon(symbol), null, tint = Color.White, modifier = Modifier.size(Size.IconSmall)) }
        }
        Spacer(Modifier.width(Spacing.M))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val SIDEBAR_ROW_HEIGHT = 56.dp
private val SIDEBAR_ART = 44.dp
private val LIKED_START = Color(0xFFF26DB5)
private val LIKED_END = Color(0xFF9F6CF2)
private val DOWNLOADS_START = Color(0xFF1FB89C)
private val DOWNLOADS_END = Color(0xFF3A7BD5)

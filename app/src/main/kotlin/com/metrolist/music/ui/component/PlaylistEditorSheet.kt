/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import timber.log.Timber
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.metrolist.music.utils.appScope
import com.metrolist.music.utils.YouTubePlaylistEditor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.fillMaxHeight
import com.yalantis.ucrop.UCrop
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.remember
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.graphics.Bitmap
import android.content.Intent
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.utils.PlaylistDetails
import com.metrolist.music.utils.PlaylistPrivacy

private const val TITLE_MAX = 150
private const val TITLE_LINES = 2

/**
 * Moving focus from the title to the description made the keyboard go down and up again: the
 * field losing focus asked to hide it and the next one asked to show it right after (both seen in
 * the IME log). Inside the editor a hide waits [HIDE_GRACE_MS] and is dropped when a show follows,
 * so the keyboard stays up between the fields; a real hide (Done, closing) still happens.
 */
@Composable
private fun SteadyKeyboard(content: @Composable () -> Unit) {
    val base = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val steady = remember(base, scope) { base?.let { SteadyKeyboardController(it, scope) } }
    CompositionLocalProvider(LocalSoftwareKeyboardController provides steady, content = content)
}

private class SteadyKeyboardController(
    private val base: SoftwareKeyboardController,
    private val scope: CoroutineScope,
) : SoftwareKeyboardController {
    private var pendingHide: Job? = null

    override fun show() {
        pendingHide?.cancel()
        pendingHide = null
        base.show()
    }

    override fun hide() {
        Timber.tag("SonaraWeb").d("editor keyboard hide requested", Throwable("caller"))
        pendingHide?.cancel()
        pendingHide = scope.launch {
            delay(HIDE_GRACE_MS)
            base.hide()
        }
    }
}

private const val HIDE_GRACE_MS = 150L

/**
 * Tapping the other field hid the keyboard and showed it again, while the keyboard's Next did
 * not (both checked in the IME log). The difference: Next moves focus a moment after the input,
 * a tap moves it while the touch is still being handled. So a tap on a field that is not focused
 * yet is taken here and focus moves the same way Next does; taps inside the focused field (to
 * place the cursor) reach it as usual.
 */
private class TapFocus(val requester: FocusRequester = FocusRequester()) {
    var focused = false
}

private fun Modifier.focusOnTap(
    target: TapFocus,
    scope: CoroutineScope,
): Modifier =
    focusRequester(target.requester)
        .onFocusChanged { target.focused = it.isFocused }
        .pointerInput(target) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (target.focused) return@awaitEachGesture
                down.consume()
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                up.consume()
                Timber.tag("SonaraWeb").d("editor field tapped: focus moves after the touch")
                scope.launch { target.requester.requestFocus() }
            }
        }

/**
 * "Import from another playlist": off, it is a plain new playlist. On, it asks for the link and a
 * check; the check shows the playlist's name, owner and song count so the user can make sure it
 * is the right one, or says in red why it cannot be imported. Done waits for a good check.
 */
@Composable
private fun ImportSection(
    link: TextFieldState,
    check: YouTubePlaylistEditor.ImportCheck?,
    checking: Boolean,
    onCheck: () -> Unit,
) {
    // Same tap handling as the title and description, so the keyboard stays up between them.
    val linkFocus = remember { TapFocus() }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = Spacing.L)) {
        val error = check?.error
        TextField(
            state = link,
            label = { Text(stringResource(R.string.er_import_link)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            onKeyboardAction = { if (link.text.isNotBlank() && !checking) onCheck() },
            isError = error != null,
            supportingText = error?.let { { Text(stringResource(importErrorText(it))) } },
            trailingIcon = {
                if (checking) {
                    CircularProgressIndicator(strokeWidth = Spacing.XXS, modifier = Modifier.size(Size.IconSmall))
                } else {
                    IconButton(onClick = onCheck, enabled = link.text.isNotBlank()) {
                        Icon(
                            painterResource(if (check != null && error == null) R.drawable.check else R.drawable.search),
                            contentDescription = stringResource(R.string.er_import_check),
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().focusOnTap(linkFocus, scope),
        )
        check?.source?.takeIf { error == null }?.let { source ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.S)
                    .clip(ErShape.Medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(Spacing.M),
            ) {
                AsyncImage(
                    model = source.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Size.ListThumb).clip(ErShape.Small),
                )
                Spacer(Modifier.width(Spacing.M))
                Column(Modifier.weight(1f)) {
                    Text(source.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    val details = listOfNotNull(
                        source.owner?.let { stringResource(R.string.er_import_owner, it) },
                        source.trackCount?.let { pluralStringResource(R.plurals.n_song, it, it) },
                    ).joinToString(" \u2022 ")
                    if (details.isNotEmpty()) {
                        Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun importErrorText(error: YouTubePlaylistEditor.ImportError): Int =
    when (error) {
        YouTubePlaylistEditor.ImportError.NOT_A_PLAYLIST -> R.string.er_import_err_link
        YouTubePlaylistEditor.ImportError.MIX -> R.string.er_import_err_mix
        YouTubePlaylistEditor.ImportError.NOT_FOUND -> R.string.er_import_err_not_found
        YouTubePlaylistEditor.ImportError.EMPTY -> R.string.er_import_err_empty
        YouTubePlaylistEditor.ImportError.TOO_BIG -> R.string.er_import_err_too_big
        YouTubePlaylistEditor.ImportError.OFFLINE -> R.string.er_import_err_offline
    }

/** Drops line breaks (a pasted title stays on one line); Enter alone calls [onEnter]. */
private class NoLineBreaks(private val onEnter: () -> Unit) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (!asCharSequence().contains('\n')) return
        var i = length - 1
        while (i >= 0) {
            if (asCharSequence()[i] == '\n') replace(i, i + 1, "")
            i--
        }
        if (asCharSequence().toString() == originalText.toString()) onEnter()
    }
}
private const val DESCRIPTION_MAX = 5000

private fun privacyLabel(p: PlaylistPrivacy) =
    when (p) {
        PlaylistPrivacy.PUBLIC -> R.string.er_privacy_public
        PlaylistPrivacy.UNLISTED -> R.string.er_privacy_unlisted
        PlaylistPrivacy.PRIVATE -> R.string.er_privacy_private
    }

private fun privacyIcon(p: PlaylistPrivacy) =
    when (p) {
        PlaylistPrivacy.PUBLIC -> R.drawable.language
        PlaylistPrivacy.UNLISTED -> R.drawable.link
        PlaylistPrivacy.PRIVATE -> R.drawable.lock
    }

/**
 * YouTube Music's playlist editor: cover with a pencil (when editing), title, description and
 * visibility. The same sheet creates a playlist (no cover yet) and edits one. A new cover is only
 * previewed here and handed to [onDone], so nothing changes until Done. Done waits for [onDone]
 * (YouTube's answer): true closes the sheet through [onSaved], false keeps it open as it was.
 *
 * [loadDetails] fills description and visibility from the account when editing; [initialDetails]
 * (read when the playlist opened) shows them at once meanwhile. Done with nothing changed just
 * closes, without saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditorSheet(
    initialTitle: String,
    onDone: suspend (title: String, description: String, privacy: PlaylistPrivacy, newCover: Uri?) -> Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = onDismiss,
    editing: Boolean = false,
    coverUrl: String? = null,
    loadDetails: (suspend () -> PlaylistDetails?)? = null,
    initialDetails: PlaylistDetails? = null,
    defaultCovers: List<String> = emptyList(),
    collaboratePlaylistId: String? = null,
    initialCustomCover: Boolean = false,
    /** The Collaborate panel's state if the screen read it already, so it opens without waiting. */
    initialCollaboration: YouTubePlaylistEditor.Collaboration? = null,
    onCollaboration: (YouTubePlaylistEditor.Collaboration?) -> Unit = {},
    /**
     * Creating only: offers "Import from another playlist". The new playlist (the user's own name,
     * description and visibility) gets the songs of a checked source playlist.
     */
    onImport: (suspend (title: String, description: String, privacy: PlaylistPrivacy, source: YouTubePlaylistEditor.ImportSource) -> Boolean)? = null,
) {
    val context = LocalContext.current
    var newCover by remember { mutableStateOf<Uri?>(null) }
    var cropTarget by remember { mutableStateOf<Uri?>(null) }
    val cropTitle = stringResource(R.string.edit_playlist_cover)
    val cropColors = MaterialTheme.colorScheme
    val crop = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) newCover = res.data?.let(UCrop::getOutput) ?: cropTarget
    }
    fun cacheUri(name: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.FileProvider",
        java.io.File(context.cacheDir, "${name}_${System.currentTimeMillis()}.jpg"),
    )

    // Pick or shoot, then crop square, like changing a profile picture; the sheet stays open.
    fun startCrop(source: Uri) {
        val dest = cacheUri("playlist_cover")
        cropTarget = dest
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setHideBottomControls(true)
            setToolbarTitle(cropTitle)
            setToolbarColor(cropColors.surface.toArgb())
            setToolbarWidgetColor(cropColors.onSurface.toArgb())
            setRootViewBackgroundColor(cropColors.surface.toArgb())
        }
        crop.launch(
            UCrop.of(source, dest).withAspectRatio(1f, 1f).withOptions(options).getIntent(context)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { source ->
        source?.let(::startCrop)
    }
    var photoTarget by remember { mutableStateOf<Uri?>(null) }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) photoTarget?.let(::startCrop)
    }
    var coverMenu by remember { mutableStateOf(false) }
    val pickCover = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val takePhoto = {
        val target = cacheUri("playlist_photo")
        photoTarget = target
        runCatching { shoot.launch(target) }
    }

    // State-based fields: moving focus between them keeps the keyboard up. The value/onValueChange
    // fields went through Compose's older input path, which hid the keyboard when a field lost
    // focus and showed it again for the next one (seen in the IME log), so the sheet dropped and rose.
    val titleState = rememberTextFieldState(initialTitle)
    val descriptionState = rememberTextFieldState(initialDetails?.description.orEmpty())
    val title = titleState.text.toString()
    var privacy by rememberSaveable { mutableStateOf(initialDetails?.privacy ?: PlaylistPrivacy.PRIVATE) }
    // What YouTube has; null until read (only when editing a YouTube playlist).
    var baseline by remember { mutableStateOf(initialDetails) }
    var privacyMenu by rememberSaveable { mutableStateOf(false) }
    var showCollaborate by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var customCover by remember { mutableStateOf(initialDetails?.customCover ?: initialCustomCover) }
    var collaboration by remember { mutableStateOf(initialCollaboration) }
    // "Import playlist" from the New menu: the same sheet with the link and its check on top.
    val importing = !editing && onImport != null
    val importLink = rememberTextFieldState()
    var importCheck by remember { mutableStateOf<YouTubePlaylistEditor.ImportCheck?>(null) }
    var checkingImport by remember { mutableStateOf(false) }
    // A changed link has to be checked again.
    LaunchedEffect(importLink) {
        snapshotFlow { importLink.text.toString() }.collect { importCheck = null }
    }
    val importSource = importCheck?.takeIf { it.error == null }?.source

    LaunchedEffect(Unit) {
        loadDetails?.invoke()?.let { fresh ->
            // Only what the user has not changed yet takes YouTube's value.
            if (descriptionState.text.toString() == baseline?.description.orEmpty()) {
                descriptionState.setTextAndPlaceCursorAtEnd(fresh.description)
            }
            if (privacy == (baseline?.privacy ?: PlaylistPrivacy.PRIVATE)) privacy = fresh.privacy
            customCover = fresh.customCover
            baseline = fresh
        }
    }
    val detailsKnown = !editing || loadDetails == null || baseline != null
    val changed = !editing || newCover != null || title.trim() != initialTitle.trim() ||
        descriptionState.text.toString().trim() != baseline?.description.orEmpty().trim() ||
        privacy != (baseline?.privacy ?: PlaylistPrivacy.PRIVATE)
    // Read alongside the details, so the Collaborate panel opens with its state already there.
    LaunchedEffect(collaboratePlaylistId) {
        if (collaboration == null) {
            collaboratePlaylistId?.let { collaboration = YouTubePlaylistEditor.collaboration(it)?.also(onCollaboration) }
        }
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !saving }),
        shape = ErShape.SheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        // The sheet already keeps clear of the keyboard and navigation bar; padding for them again
        // here made the fields jump twice when focus moved from the title to the description.
        SteadyKeyboard {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Spacing.S)) {
                    IconButton(onClick = onDismiss, enabled = !saving) {
                        Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.er_close))
                    }
                    Text(
                        stringResource(
                            when {
                                editing -> R.string.edit_playlist
                                importing -> R.string.er_import_playlist
                                else -> R.string.create_playlist
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (saving) {
                        CircularProgressIndicator(
                            strokeWidth = Spacing.XXS,
                            modifier = Modifier
                                .padding(horizontal = Spacing.L)
                                .size(Size.IconSmall),
                        )
                    } else {
                        TextButton(
                            // While YouTube's description is not read yet, saving could replace it with
                            // an empty one, so a change waits for it; nothing changed closes at once.
                            enabled = title.isNotBlank() && (detailsKnown || !changed) && (!importing || importSource != null),
                            onClick = {
                                if (!changed) {
                                    onDismiss()
                                    return@TextButton
                                }
                                saving = true
                                // Finishes even if the screen goes away; the sheet closes only when YouTube took it.
                                appScope.launch {
                                    val description = descriptionState.text.toString().trim()
                                    val source = importSource
                                    val saved = if (importing && source != null && onImport != null) {
                                        onImport(title.trim(), description, privacy, source)
                                    } else {
                                        onDone(title.trim(), description, privacy, newCover)
                                    }
                                    withContext(Dispatchers.Main) {
                                        saving = false
                                        if (saved) onSaved()
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.er_done)) }
                    }
                }

                if (editing) {
                    Box(
                        Modifier
                            .padding(vertical = Spacing.M)
                            .align(Alignment.CenterHorizontally),
                    ) {
                        val previewModifier = Modifier
                            .size(Size.HeaderArt * 0.8f)
                            .clip(ErShape.Medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        if (newCover == RemovedCover) {
                            CoverCollage(defaultCovers, previewModifier)
                        } else {
                            AsyncImage(
                                model = newCover ?: coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = previewModifier,
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = Spacing.S, y = Spacing.S)
                                .size(Size.IconButton)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface)
                                .clickable { coverMenu = true },
                        ) {
                            Icon(
                                painterResource(R.drawable.edit),
                                contentDescription = stringResource(R.string.edit_playlist_cover),
                                tint = MaterialTheme.colorScheme.surface,
                            )
                            DropdownMenu(expanded = coverMenu, onDismissRequest = { coverMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.er_take_photo)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.photo_camera), contentDescription = null) },
                                    onClick = {
                                        coverMenu = false
                                        takePhoto()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.er_choose_photo)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.insert_photo), contentDescription = null) },
                                    onClick = {
                                        coverMenu = false
                                        pickCover()
                                    },
                                )
                                if (customCover && newCover != RemovedCover) DropdownMenuItem(
                                    text = { Text(stringResource(R.string.er_remove_cover)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.delete), contentDescription = null) },
                                    onClick = {
                                        coverMenu = false
                                        newCover = RemovedCover
                                    },
                                )
                            }
                        }
                    }
                }

                if (importing) {
                    ImportSection(
                        link = importLink,
                        check = importCheck,
                        checking = checkingImport,
                        onCheck = {
                            checkingImport = true
                            appScope.launch {
                                val result = YouTubePlaylistEditor.checkImport(importLink.text.toString())
                                withContext(Dispatchers.Main) {
                                    importCheck = result
                                    checkingImport = false
                                }
                            }
                        },
                    )
                }

                // Both fields ask the keyboard for the same kind of input, so it does not switch layout
                // either. The title takes no line breaks: Enter there moves on to the description.
                val focusManager = LocalFocusManager.current
                val scope = rememberCoroutineScope()
                val textKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                val titleFocus = remember { TapFocus() }
                val descriptionFocus = remember { TapFocus() }
                val titleInput = remember(focusManager) {
                    NoLineBreaks(onEnter = { scope.launch { focusManager.moveFocus(FocusDirection.Down) } })
                        .then(InputTransformation.maxLength(TITLE_MAX))
                }
                Column(Modifier.padding(horizontal = Spacing.L)) {
                    TextField(
                        state = titleState,
                        inputTransformation = titleInput,
                        label = { Text(stringResource(R.string.er_title)) },
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = TITLE_LINES),
                        keyboardOptions = textKeyboard,
                        supportingText = { Text("${title.length}/$TITLE_MAX") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().focusOnTap(titleFocus, scope),
                    )
                    TextField(
                        state = descriptionState,
                        inputTransformation = InputTransformation.maxLength(DESCRIPTION_MAX),
                        label = { Text(stringResource(R.string.er_description_optional)) },
                        keyboardOptions = textKeyboard,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Size.ListRow).focusOnTap(descriptionFocus, scope),
                    )
                    Spacer(Modifier.height(Spacing.S))
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Size.ListRow - Spacing.S)
                                .clip(ErShape.Small)
                                .clickable { privacyMenu = true }
                                .padding(horizontal = Spacing.S),
                        ) {
                            Icon(painterResource(privacyIcon(privacy)), contentDescription = null, modifier = Modifier.size(Size.Icon))
                            Spacer(Modifier.width(Spacing.L))
                            Text(stringResource(privacyLabel(privacy)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(painterResource(R.drawable.expand_more), contentDescription = null)
                        }
                        DropdownMenu(expanded = privacyMenu, onDismissRequest = { privacyMenu = false }) {
                            PlaylistPrivacy.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(privacyLabel(option))) },
                                    leadingIcon = { Icon(painterResource(privacyIcon(option)), contentDescription = null) },
                                    onClick = {
                                        privacy = option
                                        privacyMenu = false
                                    },
                                )
                            }
                        }
                    }
                    if (editing && collaboratePlaylistId != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Size.ListRow - Spacing.S)
                                .clip(ErShape.Small)
                                .clickable { showCollaborate = true }
                                .padding(horizontal = Spacing.S),
                        ) {
                            Icon(painterResource(R.drawable.person_add), contentDescription = null, modifier = Modifier.size(Size.Icon))
                            Spacer(Modifier.width(Spacing.L))
                            Text(stringResource(R.string.er_collaborate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(painterResource(R.drawable.navigate_next), contentDescription = null)
                        }
                    }
                    Spacer(Modifier.height(Spacing.XL))
                }
            }
        }
    }
    if (showCollaborate && collaboratePlaylistId != null) {
        CollaborateSheet(
            playlistId = collaboratePlaylistId,
            playlistTitle = title,
            initial = collaboration,
            onState = {
                collaboration = it
                onCollaboration(it)
            },
            onDismiss = { showCollaborate = false },
        )
    }
}

/** Handed to onDone as the new cover: back to the default, the first songs' covers. */
val RemovedCover: Uri = Uri.EMPTY

/** Up to four song covers in a square, like a playlist without its own cover. */
@Composable
private fun CoverCollage(
    covers: List<String>,
    modifier: Modifier,
) {
    if (covers.size < 2) {
        AsyncImage(model = covers.firstOrNull(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    Column(modifier) {
        repeat(2) { row ->
            Row(Modifier.weight(1f)) {
                repeat(2) { col ->
                    AsyncImage(
                        model = covers.getOrNull(row * 2 + col),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

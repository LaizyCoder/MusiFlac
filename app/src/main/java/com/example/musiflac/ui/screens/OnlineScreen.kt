package com.laizycoder.musiflac.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.laizycoder.musiflac.online.Extension
import com.laizycoder.musiflac.online.ExtensionManager
import com.laizycoder.musiflac.online.ExtensionType
import com.laizycoder.musiflac.online.GoBackend
import com.laizycoder.musiflac.online.OnlineTrack
import com.laizycoder.musiflac.online.OnlineDownloadResolver
import com.laizycoder.musiflac.player.MusicPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Accent: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

private val SecondaryText: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

private enum class SearchCategory(
    val label: String,
    val filter: String?
) {
    ALL("All", null),
    SONGS("Songs", "tracks"),
    ALBUMS("Albums", "albums"),
    ARTISTS("Artists", "artists"),
    PLAYLISTS("Playlists", "playlists")
}

@Composable
fun OnlineScreen(
    extensionManager: ExtensionManager,
    musicPlayer: MusicPlayer,
    paddingValues: androidx.compose.foundation.layout.PaddingValues
) {
    val context = LocalContext.current

    val providers = remember {
        extensionManager
            .getEnabled()
            .filter {
                it.types.contains(
                    ExtensionType.METADATA
                )
            }
    }

    var selectedProvider by remember(providers) {
        mutableStateOf(
            providers.firstOrNull {
                it.name.equals("Spotify Web", ignoreCase = true)
            } ?: providers.firstOrNull()
        )
    }

    var providerMenuExpanded by remember {
        mutableStateOf(false)
    }

    var query by remember {
        mutableStateOf("")
    }

    var category by remember {
        mutableStateOf(SearchCategory.SONGS)
    }

    var results by remember {
        mutableStateOf<List<OnlineSearchResult>>(emptyList())
    }

    var isSearching by remember {
        mutableStateOf(false)
    }

    var hasSearched by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var sortAscending by remember {
        mutableStateOf(true)
    }

    var selectedTrack by remember {
        mutableStateOf<OnlineTrack?>(null)
    }

    var actionRunning by remember {
        mutableStateOf(false)
    }

    var actionLabel by remember {
        mutableStateOf("")
    }

    var actionError by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    fun search() {
        val searchQuery = query.trim()
        val provider = selectedProvider

        if (searchQuery.isEmpty() || isSearching || provider == null) {
            return
        }

        scope.launch {
            isSearching = true
            hasSearched = true
            errorMessage = null

            try {
                results = withContext(Dispatchers.IO) {
                    searchOnline(
                        extension = provider,
                        query = searchQuery,
                        category = category
                    )
                }
            } catch (error: Throwable) {
                results = emptyList()
                errorMessage =
                    error.message ?: "Search failed"
            } finally {
                isSearching = false
            }
        }
    }

    val sortedResults = remember(results, sortAscending) {
        if (sortAscending) {
            results.sortedBy { it.title.lowercase() }
        } else {
            results.sortedByDescending { it.title.lowercase() }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
        ) {
            if (actionRunning) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Online ${if (actionLabel.startsWith("Downloading")) "Download" else "Playback"}") },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Accent
                            )
                            Text(
                                text = actionLabel.ifBlank { "Working..." },
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    confirmButton = {}
                )
            }

            actionError?.let { message ->
                AlertDialog(
                    onDismissRequest = { actionError = null },
                    title = { Text("Online action failed") },
                    text = { Text(message) },
                    confirmButton = {
                        Button(onClick = { actionError = null }) {
                            Text("OK")
                        }
                    }
                )
            }

            selectedTrack?.let { track ->
                OnlineTrackActionDialog(
                    track = track,
                    onDismiss = {
                        selectedTrack = null
                    },
                    onPlay = {
                        val trackToPlay = track
                        selectedTrack = null
                        actionError = null
                        actionLabel = "Resolving ${trackToPlay.title}..."
                        actionRunning = true
                        Log.d("OnlineScreen", "PLAY clicked: ${trackToPlay.title} - ${trackToPlay.artist}")

                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    OnlineDownloadResolver.resolveAndDownload(
                                        context = context,
                                        extensionManager = extensionManager,
                                        track = trackToPlay,
                                        outputDirectory = File(
                                            context.cacheDir,
                                            "online_playback"
                                        )
                                    )
                                }

                                Log.d("OnlineScreen", "PLAY resolver result: $result")

                                if (result.success && result.filePath != null) {
                                    actionLabel = "Starting playback..."
                                    musicPlayer.playOnline(
                                        trackToPlay,
                                        result.filePath
                                    )
                                    actionRunning = false
                                } else {
                                    actionRunning = false
                                    actionError = result.error ?: "Unable to play this track."
                                }
                            } catch (error: Throwable) {
                                Log.e("OnlineScreen", "PLAY failed", error)
                                actionRunning = false
                                actionError = error.message ?: "Unable to play this track."
                            }
                        }
                    },
                    onDownload = {
                        val trackToDownload = track
                        selectedTrack = null
                        actionError = null
                        actionLabel = "Downloading ${trackToDownload.title}..."
                        actionRunning = true
                        Log.d("OnlineScreen", "DOWNLOAD clicked: ${trackToDownload.title} - ${trackToDownload.artist}")

                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    OnlineDownloadResolver.resolveAndDownload(
                                        context = context,
                                        extensionManager = extensionManager,
                                        track = trackToDownload,
                                        outputDirectory = File(
                                            context.getExternalFilesDir(
                                                android.os.Environment.DIRECTORY_MUSIC
                                            ) ?: context.filesDir,
                                            "MusiFlac"
                                        )
                                    )
                                }

                                Log.d("OnlineScreen", "DOWNLOAD resolver result: $result")

                                actionRunning = false
                                if (!result.success) {
                                    actionError = result.error ?: "Download failed."
                                }
                            } catch (error: Throwable) {
                                Log.e("OnlineScreen", "DOWNLOAD failed", error)
                                actionRunning = false
                                actionError = error.message ?: "Download failed."
                            }
                        }
                    }
                )
            }

            SearchBar(
                query = query,
                selectedProvider = selectedProvider,
                providers = providers,
                providerMenuExpanded = providerMenuExpanded,
                onProviderMenuExpandedChange = {
                    providerMenuExpanded = it
                },
                onProviderSelected = { provider ->
                    selectedProvider = provider
                    providerMenuExpanded = false
                    errorMessage = null
                },
                onQueryChange = {
                    query = it
                },
                onClear = {
                    query = ""
                    results = emptyList()
                    hasSearched = false
                    errorMessage = null
                },
                onSearch = {
                    search()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchCategory.values().toList()) { searchCategory ->
                    SearchCategoryChip(
                        label = searchCategory.label,
                        selected = category == searchCategory,
                        icon = {
                            Icon(
                                imageVector = when (searchCategory) {
                                    SearchCategory.ALBUMS -> Icons.Default.Album
                                    SearchCategory.ARTISTS -> Icons.Default.Person
                                    else -> Icons.Default.Search
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Accent
                            )
                        },
                        onClick = {
                            category = searchCategory
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        hasSearched -> "${sortedResults.size} results"
                        selectedProvider != null -> selectedProvider!!.name
                        else -> "Online"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            sortAscending = !sortAscending
                        }
                        .padding(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort",
                        tint = Accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sort",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isSearching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Accent
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Searching ${selectedProvider?.name ?: "provider"}...",
                        color = SecondaryText,
                        fontSize = 14.sp
                    )
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "Search failed",
                    color = SecondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else if (hasSearched && sortedResults.isEmpty()) {
                Text(
                    text = "No results found",
                    color = SecondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else if (!hasSearched) {
                Text(
                    text = if (providers.isEmpty()) {
                        "No metadata extensions enabled"
                    } else {
                        "Search ${category.label.lowercase()} on ${selectedProvider?.name ?: "an extension"}"
                    },
                    color = SecondaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = sortedResults,
                        key = {
                            "${it.source}:${it.id}:${it.resultType}"
                        }
                    ) { result ->
                        when (result) {
                            is OnlineSearchResult.Track -> {
                                OnlineTrackItem(
                                    track = result.track,
                                    onClick = {
                                        selectedTrack = result.track
                                    }
                                )
                            }

                            is OnlineSearchResult.Album -> {
                                OnlineAlbumItem(result)
                            }

                            is OnlineSearchResult.Artist -> {
                                OnlineArtistItem(result)
                            }

                            is OnlineSearchResult.Playlist -> {
                                OnlinePlaylistItem(result)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackActionDialog(
    track: OnlineTrack,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = track.title,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = track.artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )

                track.album
                    ?.takeIf { it.isNotBlank() }
                    ?.let { album ->
                        Text(
                            text = album,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay
                ) {
                    Text("Play")
                }

                Button(
                    onClick = onDownload
                ) {
                    Text("Download")
                }
            }
        }
    )
}

@Composable
private fun SearchBar(
    query: String,
    selectedProvider: Extension?,
    providers: List<Extension>,
    providerMenuExpanded: Boolean,
    onProviderMenuExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (Extension) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    Box {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(42.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .clickable {
                            onProviderMenuExpandedChange(
                                !providerMenuExpanded
                            )
                        }
                        .padding(
                            horizontal = 6.dp,
                            vertical = 9.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search provider",
                        tint = Accent,
                        modifier = Modifier.size(28.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Choose provider",
                        tint = Accent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            horizontal = 8.dp,
                            vertical = 16.dp
                        ),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search ${selectedProvider?.name ?: "music"}",
                                    color = SecondaryText,
                                    fontSize = 20.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = providerMenuExpanded,
            onDismissRequest = {
                onProviderMenuExpandedChange(false)
            },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .width(390.dp)
        ) {
            if (providers.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "No metadata providers enabled",
                            color = SecondaryText
                        )
                    },
                    onClick = {
                        onProviderMenuExpandedChange(false)
                    }
                )
            } else {
                providers.forEach { provider ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = provider.name,
                                color = if (provider == selectedProvider) {
                                    Accent
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 18.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        trailingIcon = if (provider == selectedProvider) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Accent
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            onProviderSelected(provider)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchCategoryChip(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 11.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (selected) Accent else Color.White,
                fontSize = 17.sp
            )
        }
    }
}

private suspend fun searchOnline(
    extension: Extension,
    query: String,
    category: SearchCategory
): List<OnlineSearchResult> {

    val methodsJson = GoBackend.getExtensionMethods(extension.id)
    val methods = JSONArray(methodsJson)

    var hasCustomSearch = false
    for (index in 0 until methods.length()) {
        if (methods.optString(index) == "customSearch") {
            hasCustomSearch = true
            break
        }
    }

    val method: String
    val arguments: JSONArray

    if (hasCustomSearch) {
        method = "customSearch"

        val options = JSONObject().apply {
            put("limit", 20)
            category.filter?.let { filter ->
                put("filter", filter)
            }
        }

        arguments = JSONArray().apply {
            put(query)
            put(options)
        }
    } else {
        // Compatibility fallback for older extensions that do not expose
        // customSearch. We still use the existing provider methods when
        // available, and the parser below will normalize their response.
        method = when (category) {
            SearchCategory.ALL,
            SearchCategory.SONGS -> "searchTracks"
            SearchCategory.ALBUMS -> "searchAlbums"
            SearchCategory.ARTISTS -> "searchArtists"
            SearchCategory.PLAYLISTS -> "searchPlaylists"
        }

        var hasMethod = false
        for (index in 0 until methods.length()) {
            if (methods.optString(index) == method) {
                hasMethod = true
                break
            }
        }

        if (!hasMethod) {
            throw IllegalStateException(
                "${extension.name} does not provide method \"$method\""
            )
        }

        arguments = JSONArray().apply {
            put(query)
        }
    }

    val json = GoBackend.callExtensionMethod(
        extension.id,
        method,
        arguments.toString()
    )

    return parseSearchResults(
        json = json,
        source = extension.id,
        requestedCategory = category
    )
}

private fun parseSearchResults(
    json: String,
    source: String,
    requestedCategory: SearchCategory
): List<OnlineSearchResult> {
    val results = mutableListOf<OnlineSearchResult>()

    try {
        val root = json.trim()
        if (root.isEmpty()) return emptyList()

        if (root.startsWith("[")) {
            val array = JSONArray(root)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseResultItem(
                    item = item,
                    source = source,
                    forcedType = categoryType(requestedCategory),
                    results = results
                )
            }
        } else if (root.startsWith("{")) {
            parseResultObject(
                root = JSONObject(root),
                source = source,
                requestedCategory = requestedCategory,
                results = results
            )
        }
    } catch (error: Throwable) {
        android.util.Log.e(
            "OnlineSearch",
            "Failed to parse search results",
            error
        )
    }

    return when (requestedCategory) {
        SearchCategory.ALL -> results
        SearchCategory.SONGS -> results.filter { it.resultType == "track" }
        SearchCategory.ALBUMS -> results.filter { it.resultType == "album" }
        SearchCategory.ARTISTS -> results.filter { it.resultType == "artist" }
        SearchCategory.PLAYLISTS -> results.filter { it.resultType == "playlist" }
    }
}

private fun parseResultObject(
    root: JSONObject,
    source: String,
    requestedCategory: SearchCategory,
    results: MutableList<OnlineSearchResult>
) {
    var foundTypedCollection = false

    foundTypedCollection = parseResultCollection(
        root = root,
        key = "tracks",
        type = "track",
        source = source,
        results = results
    ) || foundTypedCollection

    foundTypedCollection = parseResultCollection(
        root = root,
        key = "albums",
        type = "album",
        source = source,
        results = results
    ) || foundTypedCollection

    foundTypedCollection = parseResultCollection(
        root = root,
        key = "artists",
        type = "artist",
        source = source,
        results = results
    ) || foundTypedCollection

    foundTypedCollection = parseResultCollection(
        root = root,
        key = "playlists",
        type = "playlist",
        source = source,
        results = results
    ) || foundTypedCollection

    // Direct { items: [...] } / { results: [...] } responses.
    if (!foundTypedCollection) {
        parseResultArrayValue(
            value = root.opt("items"),
            source = source,
            forcedType = categoryType(requestedCategory),
            results = results
        )

        if (results.isEmpty()) {
            parseResultArrayValue(
                value = root.opt("results"),
                source = source,
                forcedType = categoryType(requestedCategory),
                results = results
            )
        }

        if (results.isEmpty()) {
            parseResultArrayValue(
                value = root.opt("data"),
                source = source,
                forcedType = categoryType(requestedCategory),
                results = results
            )
        }
    }
}

private fun parseResultCollection(
    root: JSONObject,
    key: String,
    type: String,
    source: String,
    results: MutableList<OnlineSearchResult>
): Boolean {
    val value = root.opt(key) ?: return false
    val before = results.size

    parseResultArrayValue(
        value = value,
        source = source,
        forcedType = type,
        results = results
    )

    return value is JSONArray || value is JSONObject && results.size > before
}

private fun parseResultArrayValue(
    value: Any?,
    source: String,
    forcedType: String?,
    results: MutableList<OnlineSearchResult>
) {
    when (value) {
        is JSONArray -> {
            for (index in 0 until value.length()) {
                val item = value.optJSONObject(index) ?: continue
                parseResultItem(
                    item = item,
                    source = source,
                    forcedType = forcedType,
                    results = results
                )
            }
        }

        is JSONObject -> {
            val items = value.optJSONArray("items")
                ?: value.optJSONArray("results")
                ?: value.optJSONArray("data")

            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    parseResultItem(
                        item = item,
                        source = source,
                        forcedType = forcedType,
                        results = results
                    )
                }
            } else {
                // Some providers wrap the actual groups one level deeper.
                parseResultObject(
                    root = value,
                    source = source,
                    requestedCategory = when (forcedType) {
                        "track" -> SearchCategory.SONGS
                        "album" -> SearchCategory.ALBUMS
                        "artist" -> SearchCategory.ARTISTS
                        "playlist" -> SearchCategory.PLAYLISTS
                        else -> SearchCategory.ALL
                    },
                    results = results
                )
            }
        }
    }
}

private fun parseResultItem(
    item: JSONObject,
    source: String,
    forcedType: String?,
    results: MutableList<OnlineSearchResult>
) {
    val rawType = item.optString("item_type")
        .ifBlank { item.optString("type") }
        .ifBlank { item.optString("kind") }

    val type = rawType.ifBlank { forcedType ?: "" }.lowercase()

    val id = item.optString("id")
        .ifBlank { item.optString("spotify_id") }
        .ifBlank { item.optString("uuid") }

    if (id.isBlank()) return

    val artwork = extractArtwork(item)

    when (type) {
        "track", "tracks", "song" -> {
            results += OnlineSearchResult.Track(
                track = OnlineTrack(
                    id = id,
                    title = item.optString("name")
                        .ifBlank { item.optString("title", "Unknown") },
                    artist = extractArtists(item),
                    album = item.optString("album_name")
                        .ifBlank { item.optString("album") }
                        .ifBlank { null },
                    duration = when {
                        item.has("duration_ms") -> item.optLong("duration_ms")
                        item.has("duration") -> item.optLong("duration")
                        else -> null
                    },
                    artworkUrl = artwork,
                    isrc = item.optString("isrc")
                        .ifBlank { null },
                    source = source
                )
            )
        }

        "album", "albums" -> {
            results += OnlineSearchResult.Album(
                id = id,
                title = item.optString("name")
                    .ifBlank { item.optString("title", "Unknown album") },
                artist = extractArtists(item)
                    .takeUnless { it == "Unknown artist" },
                artworkUrl = artwork,
                source = source
            )
        }

        "artist", "artists" -> {
            results += OnlineSearchResult.Artist(
                id = id,
                name = item.optString("name")
                    .ifBlank { item.optString("title", "Unknown artist") },
                artworkUrl = artwork,
                source = source
            )
        }

        "playlist", "playlists" -> {
            results += OnlineSearchResult.Playlist(
                id = id,
                title = item.optString("name")
                    .ifBlank { item.optString("title", "Unknown playlist") },
                owner = item.optString("owner")
                    .ifBlank { item.optString("owner_name") }
                    .ifBlank { null },
                artworkUrl = artwork,
                source = source
            )
        }
    }
}

private fun categoryType(category: SearchCategory): String? = when (category) {
    SearchCategory.ALL -> null
    SearchCategory.SONGS -> "track"
    SearchCategory.ALBUMS -> "album"
    SearchCategory.ARTISTS -> "artist"
    SearchCategory.PLAYLISTS -> "playlist"
}

private sealed interface OnlineSearchResult {
    val id: String
    val title: String
    val source: String
    val resultType: String

    data class Track(
        val track: OnlineTrack
    ) : OnlineSearchResult {
        override val id: String = track.id
        override val title: String = track.title
        override val source: String = track.source
        override val resultType: String = "track"
    }

    data class Album(
        override val id: String,
        override val title: String,
        val artist: String?,
        val artworkUrl: String?,
        override val source: String
    ) : OnlineSearchResult {
        override val resultType: String = "album"
    }

    data class Artist(
        override val id: String,
        val name: String,
        val artworkUrl: String?,
        override val source: String
    ) : OnlineSearchResult {
        override val title: String = name
        override val resultType: String = "artist"
    }

    data class Playlist(
        override val id: String,
        override val title: String,
        val owner: String?,
        val artworkUrl: String?,
        override val source: String
    ) : OnlineSearchResult {
        override val resultType: String = "playlist"
    }
}

private fun extractArtists(item: JSONObject): String {
    val artists = item.opt("artists")

    if (artists is JSONArray) {
        val names = mutableListOf<String>()
        for (index in 0 until artists.length()) {
            val artist = artists.opt(index)
            when (artist) {
                is JSONObject -> {
                    val name = artist.optString("name")
                    if (name.isNotBlank()) names += name
                }
                is String -> {
                    if (artist.isNotBlank()) names += artist
                }
            }
        }
        if (names.isNotEmpty()) return names.joinToString(", ")
    }

    if (artists is String && artists.isNotBlank()) {
        return artists
    }

    return item.optString(
        "artist",
        "Unknown artist"
    )
}

private fun extractArtwork(item: JSONObject): String? {
    val direct = listOf(
        "cover_url",
        "coverUrl",
        "artworkUrl",
        "artwork_url",
        "image_url"
    )

    for (key in direct) {
        val value = item.optString(key)
        if (value.isNotBlank()) return value
    }

    val images = item.opt("images")
    if (images is JSONArray && images.length() > 0) {
        val first = images.opt(0)
        if (first is String && first.isNotBlank()) return first
        if (first is JSONObject) {
            val url = first.optString("url")
            if (url.isNotBlank()) return url
        }
    }

    if (images is String && images.isNotBlank()) {
        return images
    }

    return null
}

@Composable
private fun OnlineTrackItem(
    track: OnlineTrack,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = "Album artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                color = Accent,
                fontSize = 17.sp,
                maxLines = 1
            )

            Text(
                text = track.artist,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp)
            )

            track.album?.takeIf { it.isNotBlank() }?.let { album ->
                Text(
                    text = album,
                    color = SecondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Text(
            text = "›",
            color = Accent,
            fontSize = 34.sp
        )
    }
}

@Composable
private fun OnlineAlbumItem(
    result: OnlineSearchResult.Album
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!result.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.artworkUrl,
                    contentDescription = "Album artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = Accent,
                fontSize = 17.sp,
                maxLines = 1
            )
            Text(
                text = result.artist ?: "Album",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Text(
            text = "›",
            color = Accent,
            fontSize = 34.sp
        )
    }
}

@Composable
private fun OnlineArtistItem(
    result: OnlineSearchResult.Artist
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!result.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.artworkUrl,
                    contentDescription = "Artist artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = result.name,
            color = Accent,
            fontSize = 17.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "›",
            color = Accent,
            fontSize = 34.sp
        )
    }
}

@Composable
private fun OnlinePlaylistItem(
    result: OnlineSearchResult.Playlist
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!result.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.artworkUrl,
                    contentDescription = "Playlist artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                color = Accent,
                fontSize = 17.sp,
                maxLines = 1
            )
            result.owner?.takeIf { it.isNotBlank() }?.let { owner ->
                Text(
                    text = owner,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        Text(
            text = "›",
            color = Accent,
            fontSize = 34.sp
        )
    }
}


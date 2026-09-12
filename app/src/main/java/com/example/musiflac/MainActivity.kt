package com.laizycoder.musiflac

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.laizycoder.musiflac.data.MusicScanner
import com.laizycoder.musiflac.data.MusicTrack
import com.laizycoder.musiflac.player.MusicPlayer
import com.laizycoder.musiflac.online.ExtensionManager
import com.laizycoder.musiflac.online.ExtensionRegistry
import com.laizycoder.musiflac.online.GoBackend
import com.laizycoder.musiflac.ui.components.MiniPlayer
import com.laizycoder.musiflac.ui.screens.NowPlayingScreen
import com.laizycoder.musiflac.ui.screens.ExtensionScreen
import com.laizycoder.musiflac.ui.screens.OnlineScreen
import com.laizycoder.musiflac.ui.screens.SettingsScreen
import com.laizycoder.musiflac.ui.screens.ThemeOption
import com.laizycoder.musiflac.ui.theme.MusiFlacTheme
import java.io.File

private enum class AppTab(
    val title: String,
    val symbol: String
) {
    Home("Home", "⌂"),
    Online("Online", "⌕"),
    Library("Library", "♫"),
    Settings("Settings", "⚙")
}

@Composable
private fun MusiFlacApp(
    musicTracks: List<MusicTrack>,
    musicPlayer: MusicPlayer,
    extensionManager: ExtensionManager,
    currentTrack: MusicTrack?,
    onTrackSelected: (MusicTrack) -> Unit
) {
    var selectedTab by remember {
        mutableStateOf(AppTab.Home)
    }

    val appContext = LocalContext.current.applicationContext

    val themePreferences =
        remember {
            appContext.getSharedPreferences(
                "musiflac_settings",
                android.content.Context.MODE_PRIVATE
            )
        }

    var selectedTheme by remember {
        val savedTheme =
            themePreferences.getString(
                "appearance_theme",
                ThemeOption.SYSTEM.name
            )

        mutableStateOf(
            ThemeOption.entries.firstOrNull {
                it.name == savedTheme
            } ?: ThemeOption.SYSTEM
        )
    }

    var showNowPlaying by remember {
        mutableStateOf(false)
    }

    var showExtensions by remember {
        mutableStateOf(false)
    }

    val isDarkTheme = when (selectedTheme) {
        ThemeOption.SYSTEM ->
            isSystemInDarkTheme()

        ThemeOption.LIGHT ->
            false

        ThemeOption.DARK ->
            true

        ThemeOption.AMOLED ->
            true
    }

    val isAmoledTheme =
        selectedTheme == ThemeOption.AMOLED

    MusiFlacTheme(
        darkTheme = isDarkTheme,
        amoled = isAmoledTheme,
        dynamicColor = false
    ) {

        if (
            showNowPlaying &&
            currentTrack != null
        ) {

            NowPlayingScreen(
                track = currentTrack,
                musicPlayer = musicPlayer,

                onPrevious = {
                    musicPlayer.previous()
                },

                onNext = {
                    musicPlayer.next()
                },

                onBack = {
                    showNowPlaying = false
                }
            )

        } else if (
            selectedTab == AppTab.Settings &&
            showExtensions
        ) {

            ExtensionScreen(
                extensionManager =
                    extensionManager,

                onBack = {
                    showExtensions = false
                }
            )

        } else if (
            selectedTab == AppTab.Settings
        ) {

            SettingsScreen(
                selectedTheme = selectedTheme,

                onThemeSelected = {
                    selectedTheme = it

                    themePreferences.edit()
                        .putString(
                            "appearance_theme",
                            it.name
                        )
                        .apply()
                },

                onExtensions = {
                    showExtensions = true
                },

                onBack = {
                    selectedTab = AppTab.Home
                }
            )

        } else {

            Scaffold(

                modifier =
                    Modifier.fillMaxSize(),

                bottomBar = {

                    Column {

                        currentTrack?.let { track ->

                            MiniPlayer(
                                track = track,

                                musicPlayer =
                                    musicPlayer,

                                onOpen = {
                                    showNowPlaying = true
                                },

                                onPrevious = {
                                    musicPlayer.previous()?.let {
                                        onTrackSelected(it)
                                    }
                                },

                                onNext = {
                                    musicPlayer.next()?.let {
                                        onTrackSelected(it)
                                    }
                                }
                            )
                        }

                        NavigationBar {

                            AppTab.entries.forEach { tab ->

                                NavigationBarItem(

                                    selected =
                                        selectedTab == tab,

                                    onClick = {
                                        selectedTab = tab
                                    },

                                    icon = {

                                        Text(
                                            text =
                                                tab.symbol
                                        )
                                    },

                                    label = {

                                        Text(
                                            text =
                                                tab.title
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

            ) { innerPadding ->

                when (selectedTab) {

                    AppTab.Home -> {

                        PlaceholderScreen(
                            title = "Home",

                            paddingValues =
                                innerPadding
                        )
                    }

                    AppTab.Online -> {

                        OnlineScreen(
                            extensionManager =
                                extensionManager,
                            musicPlayer =
                                musicPlayer,
                            paddingValues = innerPadding
                        )
                    }

                    AppTab.Library -> {

                        LibraryScreen(
                            musicTracks =
                                musicTracks,

                            paddingValues =
                                innerPadding,

                            onTrackSelected =
                                onTrackSelected
                        )
                    }

                    AppTab.Settings -> {
                        // Handled above.
                    }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private var musicTracks =
        mutableStateOf<List<MusicTrack>>(
            emptyList()
        )

    private var currentTrack =
        mutableStateOf<MusicTrack?>(null)

    private lateinit var musicPlayer: MusicPlayer

    private lateinit var extensionManager: ExtensionManager

    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                scanMusic()
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        enableEdgeToEdge()

        musicPlayer =
            MusicPlayer(this)

        extensionManager =
            ExtensionManager(this)

        ExtensionRegistry.registerBuiltInExtensions(
            extensionManager
        )

        // Temporary Go backend runtime test.
        try {
            val backendVersion =
                GoBackend.version()

            Log.d(
                "MusiFlacGo",
                "Go backend loaded: $backendVersion"
            )

            val jsResult =
                GoBackend.evaluateJavaScript(
                    """
                    const a = 21;
                    const b = 21;
                    a + b;
                    """.trimIndent()
                )

            Log.d(
                "MusiFlacGo",
                "Goja result: $jsResult"
            )

            val extensionDirectory =
                File(
                    filesDir,
                    "extensions"
                )

            Log.d(
                "MusiFlacGo",
                "Extension directory: ${extensionDirectory.absolutePath}"
            )

            if (!extensionDirectory.exists()) {
                Log.d(
                    "MusiFlacGo",
                    "Extensions directory does not exist. Creating it."
                )

                extensionDirectory.mkdirs()
            }

            val extensionFiles =
                extensionDirectory.listFiles()
                    ?.toList()
                    ?: emptyList()

            Log.d(
                "MusiFlacGo",
                "Extension files: ${
                    if (extensionFiles.isEmpty()) {
                        "none"
                    } else {
                        extensionFiles.joinToString {
                            it.name
                        }
                    }
                }"
            )

            val sflxFiles =
                extensionFiles.filter {
                    it.isFile &&
                            it.name.endsWith(
                                ".sflx",
                                ignoreCase = true
                            )
                }

            if (sflxFiles.isEmpty()) {

                Log.d(
                    "MusiFlacGo",
                    "No .sflx packages found. Skipping Go extension loader."
                )

            } else {

                val loadedExtensions =
                    GoBackend.loadExtensionsFromDirectory(
                        extensionDirectory.absolutePath
                    )

                Log.d(
                    "MusiFlacGo",
                    "Loaded extensions: $loadedExtensions"
                )

            }

        } catch (error: Throwable) {

            Log.e(
                "MusiFlacGo",
                "Extension runtime test failed",
                error
            )
        }

        musicPlayer.onTrackChanged = { track ->
            currentTrack.value = track
        }

        if (
            checkSelfPermission(
                Manifest.permission.READ_MEDIA_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            audioPermissionLauncher.launch(
                Manifest.permission.READ_MEDIA_AUDIO
            )

        } else {

            scanMusic()
        }

        setContent {

            MusiFlacApp(

                musicTracks =
                    musicTracks.value,

                musicPlayer =
                    musicPlayer,

                extensionManager =
                    extensionManager,

                currentTrack =
                    currentTrack.value,

                onTrackSelected = { track ->

                    currentTrack.value =
                        track

                    musicPlayer.setQueue(
                        musicTracks.value,
                        track
                    )

                    musicPlayer.play(
                        track
                    )
                }
            )
        }
    }

    private fun scanMusic() {

        val scanner =
            MusicScanner(this)

        val tracks =
            scanner.scan()

        musicTracks.value =
            tracks
    }

    override fun onDestroy() {

        musicPlayer.release()

        super.onDestroy()
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                paddingValues
            ),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = title
        )
    }
}

@Composable
private fun LibraryScreen(
    musicTracks: List<MusicTrack>,
    paddingValues: PaddingValues,
    onTrackSelected: (MusicTrack) -> Unit
) {
    LazyColumn(

        modifier = Modifier
            .fillMaxSize()
            .padding(
                paddingValues
            ),

        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 20.dp,
                bottom = 20.dp
            )
    ) {

        item {

            Text(
                text = "Library",

                fontSize = 28.sp
            )

            Text(
                text =
                    "${musicTracks.size} songs",

                color =
                    Color(0xFF888888),

                fontSize = 14.sp,

                modifier =
                    Modifier.padding(
                        top = 4.dp
                    )
            )

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )
        }

        items(

            items = musicTracks,

            key = { track ->
                track.id
            }

        ) { track ->

            SongItem(

                track = track,

                onClick = {
                    onTrackSelected(
                        track
                    )
                }
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )
        }
    }
}

@Composable
private fun SongItem(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Row(

        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(12.dp)
            )
            .clickable(
                onClick = onClick
            )
            .background(
                Color(0xFF1E1E1E)
            )
            .padding(
                horizontal = 12.dp,
                vertical = 10.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        AlbumArtwork(
            artworkUri =
                track.artworkUri
        )

        Spacer(
            modifier =
                Modifier.width(14.dp)
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .background(
                        Color.Transparent
                    )
        ) {

            Text(
                text = track.title,

                color = Color.White,

                fontSize = 16.sp,

                maxLines = 1
            )

            Text(
                text = track.artist,

                color =
                    Color(0xFFAAAAAA),

                fontSize = 14.sp,

                maxLines = 1,

                modifier =
                    Modifier.padding(
                        top = 3.dp
                    )
            )

            Text(
                text = track.album,

                color =
                    Color(0xFF777777),

                fontSize = 12.sp,

                maxLines = 1,

                modifier =
                    Modifier.padding(
                        top = 2.dp
                    )
            )
        }

        Text(
            text =
                formatDuration(
                    track.duration
                ),

            color =
                Color(0xFF888888),

            fontSize = 12.sp,

            modifier =
                Modifier.padding(
                    horizontal = 6.dp
                )
        )

        Box(

            modifier =
                Modifier.size(40.dp),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "⋮",

                color =
                    Color.White,

                fontSize = 24.sp
            )
        }
    }
}

@Composable
private fun AlbumArtwork(
    artworkUri: String?
) {
    Box(

        modifier = Modifier
            .size(56.dp)
            .clip(
                RoundedCornerShape(8.dp)
            )
            .background(
                Color(0xFF303030)
            ),

        contentAlignment =
            Alignment.Center
    ) {

        if (artworkUri != null) {

            AsyncImage(

                model =
                    artworkUri,

                contentDescription =
                    "Album artwork",

                modifier =
                    Modifier.fillMaxSize(),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Text(

                text = "♫",

                color =
                    Color(0xFF888888),

                fontSize = 24.sp
            )
        }
    }
}

private fun formatDuration(
    durationMillis: Long
): String {

    val totalSeconds =
        durationMillis / 1000

    val minutes =
        totalSeconds / 60

    val seconds =
        totalSeconds % 60

    return "%d:%02d".format(
        minutes,
        seconds
    )
}
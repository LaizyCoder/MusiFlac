package com.laizycoder.musiflac

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.laizycoder.musiflac.ui.screens.ScanningScreen
import com.laizycoder.musiflac.ui.screens.FilesFoldersScreen
import com.laizycoder.musiflac.ui.screens.LibraryScreen
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
    onTrackSelected: (MusicTrack) -> Unit,
    onDownloadComplete: () -> Unit,
    onRescan: () -> Unit,
    onDeepRescan: () -> Unit,
    rescanResultCount: Int?,
    deepScanning: Boolean,
    deepFoundCount: Int,
    deepTotalCount: Int,
    onDismissScanResult: () -> Unit
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

    var showScanning by remember {
        mutableStateOf(false)
    }

    var showFilesFolders by remember {
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
            selectedTab == AppTab.Settings &&
            showScanning
        ) {

            ScanningScreen(
                onBack = {
                    showScanning = false
                },
                onRescan = onRescan,
                onDeepRescan = onDeepRescan,
                rescanResultCount = rescanResultCount,
                deepScanning = deepScanning,
                deepFoundCount = deepFoundCount,
                deepTotalCount = deepTotalCount,
                onDismissScanResult = onDismissScanResult
            )

        } else if (
            selectedTab == AppTab.Settings &&
            showFilesFolders
        ) {

            FilesFoldersScreen(
                onBack = {
                    showFilesFolders = false
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

                onScanning = {
                    showScanning = true
                },

                onFilesAndFolders = {
                    showFilesFolders = true
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
                            paddingValues = innerPadding,
                            onDownloadComplete = onDownloadComplete
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

    private var rescanResultCount =
        mutableStateOf<Int?>(null)

    private var deepScanning =
        mutableStateOf(false)

    private var deepFoundCount =
        mutableStateOf(0)

    private var deepTotalCount =
        mutableStateOf(0)

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

        } else if (shouldRescanOnLaunch()) {

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
                },

                onDownloadComplete = {
                    scanMusic(showResult = false)
                },

                onRescan = {
                    scanMusic(showResult = true)
                },

                onDeepRescan = {
                    deepScanMusic()
                },

                rescanResultCount =
                    rescanResultCount.value,

                deepScanning =
                    deepScanning.value,

                deepFoundCount =
                    deepFoundCount.value,

                deepTotalCount =
                    deepTotalCount.value,

                onDismissScanResult = {
                    rescanResultCount.value = null
                }
            )
        }
    }

    private fun scanMusic(showResult: Boolean = false) {
        Thread {
            val scanner = MusicScanner(this)
            val tracks = scanner.scan()

            runOnUiThread {
                musicTracks.value = tracks
                if (showResult) {
                    rescanResultCount.value = tracks.size
                }

                Log.d(
                    "MusiFlacScanner",
                    "Music scan completed: ${tracks.size} tracks found"
                )
            }
        }.start()
    }

    private fun deepScanMusic() {
        if (deepScanning.value) return

        deepScanning.value = true
        deepFoundCount.value = 0
        deepTotalCount.value = 0
        rescanResultCount.value = null

        Thread {
            val scanner = MusicScanner(this)
            val tracks = scanner.scanWithProgress { found, total ->
                runOnUiThread {
                    deepFoundCount.value = found
                    deepTotalCount.value = total
                }
            }

            runOnUiThread {
                musicTracks.value = tracks
                deepFoundCount.value = tracks.size
                deepTotalCount.value = tracks.size
                deepScanning.value = false
                rescanResultCount.value = tracks.size

                Log.d(
                    "MusiFlacScanner",
                    "Deep music scan completed: ${tracks.size} tracks found"
                )
            }
        }.start()
    }

    private fun shouldRescanOnLaunch(): Boolean {

        return getSharedPreferences(
            "scanning_settings",
            MODE_PRIVATE
        ).getBoolean(
            "rescan_on_launch",
            true
        )
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
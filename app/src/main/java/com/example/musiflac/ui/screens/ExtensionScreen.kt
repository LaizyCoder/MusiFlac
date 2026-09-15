package com.laizycoder.musiflac.ui.screens
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.laizycoder.musiflac.online.Extension
import com.laizycoder.musiflac.online.ExtensionInstaller
import com.laizycoder.musiflac.online.ExtensionManager
import com.laizycoder.musiflac.online.ExtensionRepositoryEntry
import com.laizycoder.musiflac.online.ExtensionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


// Normal MusiFlac dark palette. AMOLED is reserved for the explicit AMOLED theme.
private val ExtensionNormalDarkBackground = Color(0xFF120F15)
private val ExtensionNormalDarkSurface = Color(0xFF1E1E1E)
private val ExtensionNormalDarkSurfaceVariant = Color(0xFF242225)


@Composable
fun ExtensionScreen(
    extensionManager: ExtensionManager,
    onBack: () -> Unit
) {
    // ExtensionManager exposes a SnapshotStateList, so this read is live.
    // Installing/removing/toggling an extension mutates that list and Compose
    // automatically recomposes this screen and any provider UI using it.
    val extensions =
        extensionManager.getAll()

    val context = LocalContext.current

    var liveStatuses by remember {
        mutableStateOf<Map<String, ExtensionLiveState>>(emptyMap())
    }

    LaunchedEffect(extensions.map { it.id to it.isEnabled() }) {
        liveStatuses =
            extensions.associate { it.id to ExtensionLiveState.CHECKING }

        liveStatuses = coroutineScope {
            extensions.map { extension ->
                async {
                    extension.id to checkExtensionLiveStatus(
                        context = context,
                        extension = extension
                    )
                }
            }.awaitAll().toMap()
        }
    }

    var selectedExtension by remember {
        mutableStateOf<Extension?>(null)
    }

    var removedExtensionIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    val visibleExtensions =
        extensions.filterNot {
            it.id in removedExtensionIds
        }

    val listState =
        rememberLazyListState()

    var showMetadataPriority by remember {
        mutableStateOf(false)
    }

    var showDownloadPriority by remember {
        mutableStateOf(false)
    }

    var showFallbackExtensions by remember {
        mutableStateOf(false)
    }

    var showSearchProvider by remember {
        mutableStateOf(false)
    }

    var showHomeFeedProvider by remember {
        mutableStateOf(false)
    }

    var showExtensionRepository by remember {
        mutableStateOf(false)
    }

    var repositoryLoading by remember {
        mutableStateOf(false)
    }

    var repositoryError by remember {
        mutableStateOf<String?>(null)
    }

    var repositoryEntries by remember {
        mutableStateOf<List<ExtensionRepositoryEntry>>(
            emptyList()
        )
    }

    var installingExtensionId by remember {
        mutableStateOf<String?>(null)
    }

    var installMessage by remember {
        mutableStateOf<String?>(null)
    }

    val extensionInstaller =
        remember(context) {
            ExtensionInstaller(context)
        }

    val repository =
        remember {
            com.laizycoder.musiflac.online.ExtensionRepository()
        }

    val scope =
        rememberCoroutineScope()

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 100
        }
    }

    val searchProviderName =
        extensionManager
            .getPriority(
                key = "search",
                defaultIds = extensions
                    .filter {
                        ExtensionType.METADATA in it.types
                    }
                    .map {
                        it.id
                    }
            )
            .firstOrNull()
            ?.let { providerId ->
                extensions.firstOrNull {
                    it.id == providerId
                }?.name
            }
            ?: "Auto"

    val homeFeedMode =
        extensionManager
            .getPriority(
                key = "home_feed_mode",
                defaultIds = listOf("auto")
            )
            .firstOrNull()
            ?: "auto"

    val homeFeedProviderName =
        when (homeFeedMode) {
            "off" -> "Off"
            "auto" -> "Auto"
            else -> extensions.firstOrNull {
                it.id == homeFeedMode && it.isEnabled()
            }?.name ?: "Auto"
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
            .statusBarsPadding()
    ) {

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 26.dp,
                end = 26.dp,
                top = 8.dp,
                bottom = 28.dp
            )
        ) {

            item {

                Spacer(
                    modifier = Modifier.height(66.dp)
                )

                Text(
                    text = "Extensions",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(70.dp)
                )

                Text(
                    text = "Provider Priority",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = 28.dp
                    )
                )

                Spacer(
                    modifier = Modifier.height(22.dp)
                )
            }

            item {

                PriorityCard(
                    onMetadataPriority = {
                        showMetadataPriority = true
                    },
                    onDownloadPriority = {
                        showDownloadPriority = true
                    },
                    onFallbackExtensions = {
                        showFallbackExtensions = true
                    },
                    onSearchProvider = {
                        showSearchProvider = true
                    },
                    onHomeFeedProvider = {
                        showHomeFeedProvider = true
                    },
                    searchProviderName = searchProviderName,
                    homeFeedProviderName = homeFeedProviderName
                )

                Spacer(
                    modifier = Modifier.height(42.dp)
                )
            }

            item {

                Text(
                    text = "Installed Extensions",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = 28.dp
                    )
                )

                Spacer(
                    modifier = Modifier.height(22.dp)
                )
            }

            if (visibleExtensions.isEmpty()) {

                item {

                    Text(
                        text = "No extensions installed",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(
                            horizontal = 28.dp
                        )
                    )
                }

            } else {

                item {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(28.dp)
                            )
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(
                                horizontal = 28.dp,
                                vertical = 10.dp
                            )
                    ) {

                        visibleExtensions.forEachIndexed {
                                index,
                                extension ->

                            InstalledExtensionItem(
                                extension = extension,
                                liveStatus = liveStatuses[extension.id]
                                    ?: ExtensionLiveState.CHECKING,
                                onClick = {
                                    selectedExtension = extension
                                }
                            )

                            if (
                                index !=
                                extensions.lastIndex
                            ) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.035f
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }

            item {

                Spacer(
                    modifier = Modifier.height(34.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(24.dp)
                        )
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {

                            showExtensionRepository =
                                true

                            repositoryLoading =
                                true

                            repositoryError =
                                null

                            scope.launch {

                                try {

                                    repositoryEntries =
                                        repository.fetch()

                                } catch (
                                    error: Exception
                                ) {

                                    repositoryError =
                                        error.message
                                            ?: "Unable to load extension repository."

                                } finally {

                                    repositoryLoading =
                                        false
                                }
                            }
                        }
                        .padding(
                            vertical = 22.dp
                        ),
                    horizontalArrangement =
                        Arrangement.Center,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = "+",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Normal
                    )

                    Spacer(
                        modifier = Modifier.size(10.dp)
                    )

                    Text(
                        text = "Install Extension",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(
                    modifier = Modifier.height(42.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(24.dp)
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(
                            horizontal = 28.dp,
                            vertical = 22.dp
                        ),
                    verticalAlignment =
                        Alignment.Top
                ) {

                    Text(
                        text = "ⓘ",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 25.sp
                    )

                    Spacer(
                        modifier = Modifier.size(18.dp)
                    )

                    Text(
                        text =
                            "Extensions can add new metadata and download " +
                                    "providers. Only install extensions from trusted " +
                                    "sources.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isScrolled) {
                        MaterialTheme.colorScheme.background
                            .copy(alpha = 0.96f)
                    } else {
                        MaterialTheme.colorScheme.background
                            .copy(alpha = 0.0f)
                    }
                )
                .padding(
                    start = 28.dp,
                    end = 28.dp,
                    top = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            MusiFlacBackButton(
                onClick = onBack
            )

            if (isScrolled) {

                Spacer(
                    modifier = Modifier.size(10.dp)
                )

                Text(
                    text = "Extensions",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        selectedExtension?.let { extension ->
            ExtensionDetailsScreen(
                context = context,
                extension = extension,
                onBack = {
                    selectedExtension = null
                },
                onRemove = {
                    val packageDir =
                        File(context.filesDir, "extensions")

                    val packageFile =
                        packageDir
                            .listFiles()
                            ?.firstOrNull { file ->
                                file.isFile &&
                                        file.name.startsWith("${extension.id}-") &&
                                        file.name.endsWith(".sflx")
                            }

                    extension.setEnabled(false)

                    packageFile?.delete()

                    File(
                        packageDir,
                        "storage/${extension.id}.json"
                    ).delete()

                    removedExtensionIds =
                        removedExtensionIds + extension.id

                    selectedExtension = null
                }
            )
        }

        if (showMetadataPriority) {

            MetadataPriorityDialog(
                extensionManager = extensionManager,
                onDismiss = {
                    showMetadataPriority = false
                }
            )
        }

        if (showDownloadPriority) {

            DownloadPriorityDialog(
                extensionManager = extensionManager,
                onDismiss = {
                    showDownloadPriority = false
                }
            )
        }

        if (showFallbackExtensions) {

            FallbackExtensionsDialog(
                extensionManager = extensionManager,
                onDismiss = {
                    showFallbackExtensions = false
                }
            )
        }

        if (showSearchProvider) {

            SearchProviderDialog(
                extensionManager = extensionManager,
                onDismiss = {
                    showSearchProvider = false
                }
            )
        }

        if (showHomeFeedProvider) {

            HomeFeedProviderSheet(
                context = context,
                extensionManager = extensionManager,
                onDismiss = {
                    showHomeFeedProvider = false
                }
            )
        }

        if (showExtensionRepository) {

            ExtensionRepositoryDialog(
                extensionManager = extensionManager,
                entries = repositoryEntries,
                loading = repositoryLoading,
                error = repositoryError,
                installingExtensionId =
                    installingExtensionId,
                installMessage =
                    installMessage,
                onInstall = { entry ->

                    installingExtensionId =
                        entry.id

                    installMessage =
                        null

                    scope.launch {

                        try {

                            extensionInstaller.install(
                                entry
                            )

                            val extensionDirectory =
                                File(
                                    context.filesDir,
                                    "extensions"
                                )

                            val installedPackage =
                                extensionDirectory
                                    .listFiles()
                                    ?.firstOrNull { file ->
                                        file.isFile &&
                                                file.name.startsWith("${entry.id}-") &&
                                                file.name.endsWith(".sflx", ignoreCase = true)
                                    }

                            if (installedPackage == null) {
                                throw IllegalStateException(
                                    "Installed extension package was not found."
                                )
                            }

                            // The Kotlin manager and the Go/Goja runtime are both
                            // initialized during app startup. Installing a package
                            // after startup therefore requires registering it with
                            // both live systems instead of waiting for a restart.
                            val runtimeLoadResult =
                                withContext(Dispatchers.IO) {
                                    com.laizycoder.musiflac.online.GoBackend.loadExtension(
                                        installedPackage.absolutePath
                                    )
                                }

                            if (runtimeLoadResult.isBlank()) {
                                throw IllegalStateException(
                                    "Extension runtime did not load the installed package."
                                )
                            }

                            extensionManager.refreshInstalledExtensions()

                            installMessage =
                                "${entry.displayName} installed and activated."

                        } catch (
                            error: Exception
                        ) {

                            installMessage =
                                "${error::class.simpleName}: " +
                                        (
                                                error.message
                                                    ?: "Installation failed."
                                                )

                        } finally {

                            installingExtensionId =
                                null
                        }
                    }
                },
                onDismiss = {

                    showExtensionRepository =
                        false

                    installMessage =
                        null
                }
            )
        }
    }
}

@Composable
private fun PriorityCard(
    onMetadataPriority: () -> Unit = {},
    onDownloadPriority: () -> Unit = {},
    onFallbackExtensions: () -> Unit = {},
    onSearchProvider: () -> Unit = {},
    onHomeFeedProvider: () -> Unit = {},
    searchProviderName: String = "Auto",
    homeFeedProviderName: String = "Auto"
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(28.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                horizontal = 28.dp,
                vertical = 10.dp
            )
    ) {

        PriorityItem(
            icon = "↓",
            title = "Download Priority",
            description = "Set download service order",
            onClick = onDownloadPriority
        )

        PriorityItem(
            icon = "⑂",
            title = "Fallback Extensions",
            description =
                "Choose which installed download extensions\ncan be used as fallback",
            onClick = onFallbackExtensions
        )

        PriorityItem(
            icon = "⌕",
            title = "Metadata Priority",
            description =
                "Set search & metadata source order",
            onClick = onMetadataPriority
        )

        PriorityItem(
            icon = "☷⌕",
            title = "Search Provider",
            description = searchProviderName,
            onClick = onSearchProvider
        )

        PriorityItem(
            icon = "◈",
            title = "Home Feed Provider",
            description = homeFeedProviderName,
            onClick = onHomeFeedProvider
        )
    }
}

@Composable
private fun PriorityItem(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit = {}
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(
                vertical = 18.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = icon,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 31.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.size(32.dp)
        )

        Spacer(
            modifier = Modifier.size(28.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }

        Text(
            text = "›",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 34.sp
        )
    }
}

private enum class ExtensionLiveState(
    val label: String
) {
    CHECKING("Checking…"),
    ONLINE("Online"),
    OFFLINE("Offline"),
    DISABLED("Disabled")
}

private suspend fun checkExtensionLiveStatus(
    context: android.content.Context,
    extension: Extension
): ExtensionLiveState = withContext(Dispatchers.IO) {
    if (!extension.isEnabled()) {
        return@withContext ExtensionLiveState.DISABLED
    }

    val details = runCatching {
        loadExtensionDetails(context, extension)
    }.getOrNull() ?: return@withContext ExtensionLiveState.OFFLINE

    val hosts = details.networkPermissions
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { permission ->
            permission
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore('/')
                .removePrefix("*.")
                .substringBefore(':')
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(6)
        .toList()

    if (hosts.isEmpty()) {
        return@withContext ExtensionLiveState.ONLINE
    }

    for (host in hosts) {
        val reachable = runCatching {
            val connection =
                java.net.URL("https://$host")
                    .openConnection() as java.net.HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "MusiFlac/1.0"
                )
                connection.setRequestProperty(
                    "Range",
                    "bytes=0-0"
                )
                connection.connect()
                connection.responseCode in 100..599
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)

        if (reachable) {
            return@withContext ExtensionLiveState.ONLINE
        }
    }

    ExtensionLiveState.OFFLINE
}

@Composable
private fun InstalledExtensionItem(
    extension: Extension,
    liveStatus: ExtensionLiveState,
    onClick: () -> Unit
) {

    var enabled by remember(extension.id) {
        mutableStateOf(
            extension.isEnabled()
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(
                vertical = 16.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(
                    RoundedCornerShape(18.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "✣",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 39.sp
            )
        }

        Spacer(
            modifier = Modifier.size(28.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = extension.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text =
                    "v${extension.version} • ${liveStatus.label}",
                color = when (liveStatus) {
                    ExtensionLiveState.ONLINE -> MaterialTheme.colorScheme.primary
                    ExtensionLiveState.OFFLINE -> MaterialTheme.colorScheme.error
                    ExtensionLiveState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                    ExtensionLiveState.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 14.sp,
                maxLines = 1
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = { checked ->

                enabled =
                    checked

                extension.setEnabled(
                    checked
                )
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor =
                    MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor =
                    MaterialTheme.colorScheme.primary,
                checkedBorderColor =
                    MaterialTheme.colorScheme.primary,
                uncheckedThumbColor =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor =
                    MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor =
                    MaterialTheme.colorScheme.outline
            )
        )
    }
}


private data class ExtensionDetails(
    val displayName: String,
    val description: String,
    val homepage: String?,
    val id: String,
    val version: String,
    val types: List<String>,
    val networkPermissions: List<String>,
    val storageEnabled: Boolean,
    val fileEnabled: Boolean,
    val searchEnabled: Boolean,
    val searchPlaceholder: String?,
    val urlHandlerEnabled: Boolean,
    val urlPatterns: List<String>,
    val capabilityLabels: List<Pair<String, Boolean>>,
    val homeFeedEnabled: Boolean
)

private suspend fun loadExtensionDetails(
    context: android.content.Context,
    extension: Extension
): ExtensionDetails = withContext(Dispatchers.IO) {
    val packageDir =
        File(context.filesDir, "extensions")

    val packageFile =
        packageDir
            .listFiles()
            ?.firstOrNull { file ->
                file.isFile &&
                        file.name.startsWith("${extension.id}-") &&
                        file.name.endsWith(".sflx")
            }

    if (packageFile == null) {
        return@withContext ExtensionDetails(
            displayName = extension.name,
            description = "Installed extension.",
            homepage = null,
            id = extension.id,
            version = extension.version,
            types = extension.types.map { it.name.lowercase() },
            networkPermissions = emptyList(),
            storageEnabled = false,
            fileEnabled = false,
            searchEnabled = false,
            searchPlaceholder = null,
            urlHandlerEnabled = false,
            urlPatterns = emptyList(),
            capabilityLabels = listOf(
                "Metadata Provider" to (ExtensionType.METADATA in extension.types),
                "Download Provider" to (ExtensionType.DOWNLOAD in extension.types),
                "Lyrics Provider" to (ExtensionType.LYRICS in extension.types),
                "Search Provider" to false,
                "URL Handler" to false
            ),
            homeFeedEnabled = false
        )
    }

    val manifestJson =
        ZipFile(packageFile).use { zip ->
            zip.getInputStream(
                zip.getEntry("manifest.json")
            ).bufferedReader().use { it.readText() }
        }

    val root = JSONObject(manifestJson)

    val permissions =
        root.optJSONObject("permissions")

    val network =
        mutableListOf<String>()

    permissions
        ?.optJSONArray("network")
        ?.let { array ->
            for (i in 0 until array.length()) {
                network += array.optString(i)
            }
        }

    val types =
        mutableListOf<String>()

    root.optJSONArray("type")?.let { array ->
        for (i in 0 until array.length()) {
            types += array.optString(i)
        }
    }

    val search =
        root.optJSONObject("searchBehavior")

    val urlHandler =
        root.optJSONObject("urlHandler")

    val capabilities =
        root.optJSONObject("capabilities")

    val typeMetadata =
        types.any {
            it.equals("metadata_provider", ignoreCase = true)
        }

    val typeDownload =
        types.any {
            it.equals("download_provider", ignoreCase = true)
        }

    val typeLyrics =
        types.any {
            it.equals("lyrics_provider", ignoreCase = true)
        }

    val searchEnabled =
        search?.optBoolean("enabled", false) == true

    val urlHandlerEnabled =
        urlHandler?.optBoolean("enabled", false) == true

    val urlPatterns =
        mutableListOf<String>()

    urlHandler?.optJSONArray("patterns")?.let { array ->
        for (i in 0 until array.length()) {
            urlPatterns += array.optString(i)
        }
    }

    val capabilityLabels =
        listOf(
            "Metadata Provider" to typeMetadata,
            "Download Provider" to typeDownload,
            "Lyrics Provider" to typeLyrics,
            "Search Provider" to searchEnabled,
            "URL Handler" to urlHandlerEnabled,
            "Home Feed" to (capabilities?.optBoolean("homeFeed", false) == true),
            "Browse Categories" to (capabilities?.optBoolean("browseCategories", false) == true)
        )

    ExtensionDetails(
        displayName =
            root.optString(
                "displayName",
                extension.name
            ),
        description =
            root.optString(
                "description",
                "Installed extension."
            ),
        homepage =
            root.optString(
                "homepage",
                ""
            ).ifBlank { null },
        id = root.optString("name", extension.id),
        version =
            root.optString(
                "version",
                extension.version
            ),
        types = types,
        networkPermissions = network,
        storageEnabled =
            permissions?.optBoolean(
                "storage",
                false
            ) == true,
        fileEnabled =
            permissions?.optBoolean(
                "file",
                false
            ) == true,
        searchEnabled = searchEnabled,
        searchPlaceholder =
            search?.optString(
                "placeholder",
                ""
            )?.ifBlank { null },
        urlHandlerEnabled = urlHandlerEnabled,
        urlPatterns = urlPatterns,
        capabilityLabels = capabilityLabels,
        homeFeedEnabled = capabilities?.optBoolean("homeFeed", false) == true
    )
}

@Composable
private fun ExtensionDetailsScreen(
    context: android.content.Context,
    extension: Extension,
    onBack: () -> Unit,
    onRemove: () -> Unit
) {
    var details by remember(extension.id) {
        mutableStateOf<ExtensionDetails?>(null)
    }

    var showRemoveConfirmation by remember {
        mutableStateOf(false)
    }

    var liveStatus by remember(extension.id) {
        mutableStateOf(ExtensionLiveState.CHECKING)
    }

    LaunchedEffect(extension.id, extension.isEnabled()) {
        liveStatus = ExtensionLiveState.CHECKING
        liveStatus = checkExtensionLiveStatus(
            context = context,
            extension = extension
        )
    }

    LaunchedEffect(extension.id) {
        details =
            loadExtensionDetails(
                context,
                extension
            )
    }

    val info = details

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 27.dp,
                end = 27.dp,
                top = 8.dp,
                bottom = 34.dp
            )
        ) {
            item {
                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                MusiFlacBackButton(
                    onClick = onBack
                )

                Spacer(
                    modifier = Modifier.height(52.dp)
                )

                Text(
                    text = info?.displayName ?: extension.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(30.dp)
                )
            }

            item {
                if (info == null) {
                    Text(
                        text = "Loading extension details…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                } else {
                    ExtensionInfoCard(
                        details = info,
                        enabled = extension.isEnabled(),
                        onEnabledChange = {
                            extension.setEnabled(it)
                        }
                    )

                    Spacer(
                        modifier = Modifier.height(34.dp)
                    )

                    ExtensionDetailSectionTitle(
                        "Service Status"
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    ExtensionStatusCard(
                        status = liveStatus
                    )

                    Spacer(
                        modifier = Modifier.height(30.dp)
                    )

                    if (
                        info.networkPermissions.isNotEmpty() ||
                        info.storageEnabled ||
                        info.fileEnabled
                    ) {
                        ExtensionDetailSectionTitle(
                            "Permissions"
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        ExtensionPermissionsCard(
                            details = info
                        )

                        Spacer(
                            modifier = Modifier.height(30.dp)
                        )
                    }

                    ExtensionDetailSectionTitle(
                        "Capabilities"
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    ExtensionCapabilitiesCard(
                        details = info
                    )

                    Spacer(
                        modifier = Modifier.height(30.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            showRemoveConfirmation = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "▢  Remove Extension",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showRemoveConfirmation = false
            },
            title = {
                Text(
                    text = "Remove ${extension.name}?"
                )
            },
            text = {
                Text(
                    text =
                        "This will remove the installed extension package and its local extension storage."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmation = false
                        onRemove()
                    }
                ) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmation = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExtensionInfoCard(
    details: ExtensionDetails,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(28.dp)
            )
            .background(
                MaterialTheme.colorScheme.surface
            )
            .padding(28.dp)
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(94.dp)
                    .clip(
                        RoundedCornerShape(20.dp)
                    )
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(24.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = details.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v${details.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor =
                        MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor =
                        MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor =
                        MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Spacer(
            modifier = Modifier.height(26.dp)
        )

        Text(
            text = details.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 25.sp
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        ExtensionKeyValue(
            "ID",
            details.id
        )

        ExtensionKeyValue(
            "Version",
            details.version
        )

        details.homepage?.let {
            ExtensionKeyValue(
                "Homepage",
                it
            )
        }
    }
}

@Composable
private fun ExtensionStatusCard(
    status: ExtensionLiveState
) {
    val title = when (status) {
        ExtensionLiveState.ONLINE -> "Online"
        ExtensionLiveState.OFFLINE -> "Offline"
        ExtensionLiveState.DISABLED -> "Disabled"
        ExtensionLiveState.CHECKING -> "Checking…"
    }

    val description = when (status) {
        ExtensionLiveState.ONLINE ->
            "At least one configured extension service is reachable"
        ExtensionLiveState.OFFLINE ->
            "None of the configured extension services are reachable"
        ExtensionLiveState.DISABLED ->
            "Extension is currently disabled"
        ExtensionLiveState.CHECKING ->
            "Checking configured extension services…"
    }

    val indicator = when (status) {
        ExtensionLiveState.ONLINE -> "✓"
        ExtensionLiveState.OFFLINE -> "×"
        ExtensionLiveState.DISABLED -> "×"
        ExtensionLiveState.CHECKING -> "…"
    }

    val indicatorColor = when (status) {
        ExtensionLiveState.ONLINE -> MaterialTheme.colorScheme.primary
        ExtensionLiveState.OFFLINE -> MaterialTheme.colorScheme.error
        ExtensionLiveState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        ExtensionLiveState.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(22.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = indicator,
                color = indicatorColor,
                fontSize = 30.sp
            )

            Spacer(modifier = Modifier.width(18.dp))

            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ExtensionPermissionsCard(
    details: ExtensionDetails
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(28.dp)
            )
            .background(
                MaterialTheme.colorScheme.surface
            )
            .padding(
                horizontal = 28.dp,
                vertical = 8.dp
            )
    ) {
        details.networkPermissions.forEach {
            ExtensionPermissionRow(
                text = "Network access to: $it"
            )
        }

        if (details.storageEnabled) {
            ExtensionPermissionRow(
                text = "Storage access: enabled"
            )
        }

        if (details.fileEnabled) {
            ExtensionPermissionRow(
                text = "File access: enabled"
            )
        }
    }
}

@Composable
private fun ExtensionCapabilitiesCard(
    details: ExtensionDetails
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(28.dp)
            )
            .background(
                MaterialTheme.colorScheme.surface
            )
            .padding(
                horizontal = 28.dp,
                vertical = 8.dp
            )
    ) {
        details.capabilityLabels.forEach { (label, supported) ->
            ExtensionCapabilityRow(
                label = label,
                supported = supported
            )
        }

        if (details.searchEnabled) {
            details.searchPlaceholder?.let {
                ExtensionPermissionRow(
                    text = "Search: $it"
                )
            }
        }

        if (details.urlHandlerEnabled) {
            ExtensionPermissionRow(
                text =
                    "URL patterns: " +
                            details.urlPatterns.joinToString(", ")
            )
        }
    }
}

@Composable
private fun ExtensionPermissionRow(
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 14.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(
            modifier = Modifier.width(18.dp)
        )

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun ExtensionCapabilityRow(
    label: String,
    supported: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 14.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = if (supported) "✓" else "×",
            color =
                if (supported) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ExtensionKeyValue(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 6.dp
            )
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ExtensionDetailSectionTitle(
    text: String
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SearchProviderDialog(
    extensionManager: ExtensionManager,
    onDismiss: () -> Unit
) {

    val metadataProviders =
        extensionManager
            .getAll()
            .filter {
                ExtensionType.METADATA in it.types
            }
            .filter {
                it.isEnabled()
            }

    var selectedProviderId by remember {
        mutableStateOf(
            extensionManager
                .getPriority(
                    key = "search",
                    defaultIds =
                        metadataProviders.map {
                            it.id
                        }
                )
                .firstOrNull()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Search Provider",
                color = MaterialTheme.colorScheme.onSurface
            )
        },

        text = {

            if (metadataProviders.isEmpty()) {

                Text(
                    text =
                        "No search providers installed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            } else {

                Column {

                    Text(
                        text =
                            "Choose the provider used for online search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                    metadataProviders.forEach { provider ->

                        val selected =
                            selectedProviderId ==
                                    provider.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {

                                    selectedProviderId =
                                        provider.id
                                }
                                .padding(
                                    vertical = 12.dp,
                                    horizontal = 8.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text =
                                    if (selected) {
                                        "●"
                                    } else {
                                        "○"
                                    },
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                fontSize = 22.sp
                            )

                            Spacer(
                                modifier =
                                    Modifier.size(14.dp)
                            )

                            Column(
                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                Text(
                                    text =
                                        provider.name,
                                    color =
                                        MaterialTheme.colorScheme.onSurface,
                                    fontSize =
                                        16.sp,
                                    fontWeight =
                                        FontWeight.Medium
                                )

                                Text(
                                    text =
                                        "v${provider.version}",
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize =
                                        12.sp
                                )
                            }
                        }
                    }
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick = {

                    selectedProviderId?.let {
                        extensionManager.setPriority(
                            key = "search",
                            ids = listOf(it)
                        )
                    }

                    onDismiss()
                }
            ) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedProviderSheet(
    context: android.content.Context,
    extensionManager: ExtensionManager,
    onDismiss: () -> Unit
) {
    val extensions = extensionManager.getAll()

    var selectedMode by remember {
        mutableStateOf(
            extensionManager
                .getPriority(
                    key = "home_feed_mode",
                    defaultIds = listOf("auto")
                )
                .firstOrNull()
                ?: "auto"
        )
    }

    var providers by remember {
        mutableStateOf<List<Pair<Extension, ExtensionDetails>>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(extensions.map { it.id to it.isEnabled() }) {
        loading = true
        providers = extensions
            .filter { it.isEnabled() }
            .mapNotNull { extension ->
                runCatching {
                    loadExtensionDetails(context, extension)
                }
                    .getOrNull()
                    ?.takeIf { it.homeFeedEnabled }
                    ?.let { details ->
                        extension to details
                    }
            }
        loading = false

        if (
            selectedMode != "auto" &&
            selectedMode != "off" &&
            providers.none { it.first.id == selectedMode }
        ) {
            selectedMode = "auto"
            extensionManager.setPriority(
                key = "home_feed_mode",
                ids = listOf("auto")
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp, 32.dp, 0.dp, 0.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Home Feed Provider",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Choose which extension provides the home feed on the main screen",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                HomeFeedProviderOption(
                    title = "Auto",
                    description = "Automatically select the best available",
                    icon = "✦",
                    selected = selectedMode == "auto",
                    onClick = {
                        selectedMode = "auto"
                        extensionManager.setPriority(
                            key = "home_feed_mode",
                            ids = listOf("auto")
                        )
                    }
                )

                HomeFeedProviderOption(
                    title = "Off",
                    description = "Do not show the home feed on the main screen",
                    icon = "⊘",
                    selected = selectedMode == "off",
                    iconIsError = true,
                    onClick = {
                        selectedMode = "off"
                        extensionManager.setPriority(
                            key = "home_feed_mode",
                            ids = listOf("off")
                        )
                    }
                )

                if (loading) {
                    Text(
                        text = "Checking installed extensions…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else if (providers.isEmpty()) {
                    Text(
                        text = "No enabled extensions support a home feed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    providers.forEach { (extension, details) ->
                        HomeFeedProviderOption(
                            title = details.displayName.ifBlank { extension.name },
                            description = details.description.ifBlank { "Use ${extension.name} home feed" },
                            selected = selectedMode == extension.id,
                            onClick = {
                                selectedMode = extension.id
                                extensionManager.setPriority(
                                    key = "home_feed_mode",
                                    ids = listOf(extension.id)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFeedProviderOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: String? = null,
    iconIsError: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Text(
                text = icon,
                color = if (iconIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontSize = 28.sp,
                modifier = Modifier.width(42.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .width(42.dp)
                    .size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 2
            )
        }

        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.outline
            )
        )
    }

    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun ProviderPriorityPage(
    title: String,
    description: String,
    priorityKey: String,
    providers: List<Extension>,
    extensionManager: ExtensionManager,
    onDismiss: () -> Unit
) {
    var orderedProviders by remember(providers) {
        mutableStateOf(
            extensionManager.getPriority(
                key = priorityKey,
                defaultIds = providers.map { it.id }
            ).mapNotNull { id -> providers.firstOrNull { it.id == id } }
        )
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingProviderId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    // Keep the pointer gesture alive while the list changes underneath it.
    // Re-keying pointerInput with the list/index cancels the gesture after
    // the first reorder, which is why dragging previously stopped.
    val latestOrderedProviders by rememberUpdatedState(orderedProviders)

    val cardHeight = 64.dp
    val cardGap = 6.dp
    val slotSizePx = with(density) {
        (cardHeight + cardGap).toPx()
    }
    val swapThresholdPx = slotSizePx * 0.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ExtensionNormalDarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            MusiFlacBackButton(
                onClick = onDismiss
            )

            Spacer(modifier = Modifier.height(42.dp))

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            orderedProviders.forEachIndexed { index, provider ->
                val currentDraggingIndex = draggingIndex
                val isDragging = currentDraggingIndex == index

                // Move surrounding rows smoothly when the dragged item crosses
                // their center. The dragged card itself follows the finger.
                val targetShift = when {
                    currentDraggingIndex == null -> 0f

                    index == currentDraggingIndex -> 0f

                    currentDraggingIndex < index &&
                            dragOffset > (index - currentDraggingIndex - 0.5f) * slotSizePx ->
                        -slotSizePx

                    currentDraggingIndex > index &&
                            dragOffset < -(currentDraggingIndex - index - 0.5f) * slotSizePx ->
                        slotSizePx

                    else -> 0f
                }

                val animatedShift by animateFloatAsState(
                    targetValue = targetShift,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "providerShift"
                )

                val liftScale by animateFloatAsState(
                    targetValue = if (isDragging) 1.025f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "providerScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .graphicsLayer {
                            translationY =
                                if (isDragging) dragOffset else animatedShift
                            scaleX = liftScale
                            scaleY = liftScale
                            shadowElevation = if (isDragging) 10f else 0f
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .background(ExtensionNormalDarkSurface)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingProviderId = provider.id
                                    draggingIndex = latestOrderedProviders.indexOfFirst {
                                        it.id == provider.id
                                    }.takeIf { it >= 0 }
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingProviderId = null
                                    draggingIndex = null
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingProviderId = null
                                    draggingIndex = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()

                                    val currentIndex =
                                        latestOrderedProviders.indexOfFirst {
                                            it.id == draggingProviderId
                                        }

                                    if (currentIndex < 0) {
                                        return@detectDragGesturesAfterLongPress
                                    }

                                    draggingIndex = currentIndex
                                    dragOffset += amount.y

                                    var newIndex = currentIndex

                                    if (dragOffset > swapThresholdPx &&
                                        currentIndex < latestOrderedProviders.lastIndex
                                    ) {
                                        newIndex = currentIndex + 1
                                    } else if (
                                        dragOffset < -swapThresholdPx &&
                                        currentIndex > 0
                                    ) {
                                        newIndex = currentIndex - 1
                                    }

                                    if (newIndex != currentIndex) {
                                        val list = latestOrderedProviders.toMutableList()
                                        val item = list.removeAt(currentIndex)
                                        list.add(newIndex, item)
                                        orderedProviders = list

                                        extensionManager.setPriority(
                                            key = priorityKey,
                                            ids = list.map { it.id }
                                        )

                                        // Keep the dragged card visually attached
                                        // to the finger across every consecutive swap.
                                        dragOffset +=
                                            if (newIndex > currentIndex) {
                                                -slotSizePx
                                            } else {
                                                slotSizePx
                                            }

                                        draggingIndex = newIndex
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == 0) MaterialTheme.colorScheme.onSurface
                                    else ExtensionNormalDarkSurfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(22.dp))

                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(22.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = provider.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = "Extension",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Text(
                            text = "☷",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 22.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(end = 2.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }

                if (index < orderedProviders.lastIndex) {
                    Spacer(modifier = Modifier.height(cardGap))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ⓘ",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = if (priorityKey == "metadata") {
                            "If metadata is not available on the first provider, the app will automatically try the next one."
                        } else {
                            "If a track is not available on the first provider, the app will automatically try the next one."
                        },
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


@Composable
private fun MetadataPriorityDialog(
    extensionManager: ExtensionManager,
    onDismiss: () -> Unit
) {
    val metadataProviders = extensionManager
        .getAll()
        .filter { ExtensionType.METADATA in it.types }

    ProviderPriorityPage(
        title = "Metadata",
        description = "Drag to reorder metadata providers. The app will try providers from top to bottom when resolving metadata.",
        priorityKey = "metadata",
        providers = metadataProviders,
        extensionManager = extensionManager,
        onDismiss = onDismiss
    )
}

@Composable
private fun DownloadPriorityDialog(
    extensionManager: ExtensionManager,
    onDismiss: () -> Unit
) {
    val downloadProviders = extensionManager
        .getAll()
        .filter { ExtensionType.DOWNLOAD in it.types }

    ProviderPriorityPage(
        title = "Download",
        description = "Drag to reorder download providers. The app will try providers from top to bottom when downloading tracks.",
        priorityKey = "download",
        providers = downloadProviders,
        extensionManager = extensionManager,
        onDismiss = onDismiss
    )
}

@Composable
private fun FallbackExtensionsDialog(
    extensionManager: ExtensionManager,
    onDismiss: () -> Unit
) {

    val downloadProviders =
        extensionManager
            .getAll()
            .filter {
                ExtensionType.DOWNLOAD in it.types
            }

    var enabledIds by remember(
        downloadProviders
    ) {
        mutableStateOf(
            extensionManager
                .getFallbackProviders(
                    defaultIds =
                        downloadProviders.map {
                            it.id
                        }
                )
                .toSet()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Fallback Extensions",
                color = MaterialTheme.colorScheme.onSurface
            )
        },

        text = {

            if (downloadProviders.isEmpty()) {

                Text(
                    text =
                        "No download providers installed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            } else {

                Column {

                    Text(
                        text =
                            "Choose which download providers can be used as fallback.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                    downloadProviders
                        .forEach { provider ->

                            val checked =
                                provider.id in enabledIds

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 6.dp
                                        ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Column(
                                    modifier =
                                        Modifier.weight(1f)
                                ) {

                                    Text(
                                        text =
                                            provider.name,
                                        color =
                                            MaterialTheme.colorScheme.onSurface,
                                        fontSize =
                                            16.sp,
                                        fontWeight =
                                            FontWeight.Medium
                                    )

                                    Text(
                                        text =
                                            "v${provider.version}",
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize =
                                            12.sp
                                    )
                                }

                                Switch(
                                    checked =
                                        checked,
                                    onCheckedChange = {
                                            enabled ->

                                        enabledIds =
                                            if (enabled) {
                                                enabledIds +
                                                        provider.id
                                            } else {
                                                enabledIds -
                                                        provider.id
                                            }

                                        extensionManager
                                            .setFallbackProviders(
                                                ids =
                                                    enabledIds
                                                        .toList()
                                            )
                                    },
                                    colors =
                                        SwitchDefaults.colors(
                                            checkedThumbColor =
                                                MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor =
                                                MaterialTheme.colorScheme.primary,
                                            checkedBorderColor =
                                                MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor =
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor =
                                                MaterialTheme.colorScheme.surfaceVariant,
                                            uncheckedBorderColor =
                                                MaterialTheme.colorScheme.outline
                                        )
                                )
                            }
                        }
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ExtensionRepositoryDialog(
    extensionManager: ExtensionManager,
    entries: List<ExtensionRepositoryEntry>,
    loading: Boolean,
    error: String?,
    installingExtensionId: String?,
    installMessage: String?,
    onInstall: (
        ExtensionRepositoryEntry
    ) -> Unit,
    onDismiss: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Extension Repository",
                color = MaterialTheme.colorScheme.onSurface
            )
        },

        text = {

            when {

                loading -> {

                    Text(
                        text =
                            "Loading extensions...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                error != null -> {

                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                entries.isEmpty() -> {

                    Text(
                        text =
                            "No extensions found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {

                    Column {

                        Text(
                            text =
                                "${entries.size} extensions available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        LazyColumn(
                            modifier =
                                Modifier.height(420.dp)
                        ) {

                            items(entries) { entry ->

                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                vertical = 10.dp
                                            )
                                ) {

                                    Text(
                                        text =
                                            entry.displayName,
                                        color =
                                            MaterialTheme.colorScheme.onSurface,
                                        fontSize =
                                            17.sp,
                                        fontWeight =
                                            FontWeight.Medium
                                    )

                                    Text(
                                        text =
                                            "v${entry.version} • ${entry.category}",
                                        color =
                                            MaterialTheme.colorScheme.primary,
                                        fontSize =
                                            12.sp
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.height(4.dp)
                                    )

                                    Text(
                                        text =
                                            entry.description,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize =
                                            13.sp,
                                        lineHeight =
                                            18.sp
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.height(8.dp)
                                    )

                                    val installedPackage =
                                        extensionManager.getInstalledPackage(
                                            entry.id
                                        )

                                    val isInstalled =
                                        installedPackage != null

                                    TextButton(
                                        enabled =
                                            installingExtensionId ==
                                                    null &&
                                                    !isInstalled,
                                        onClick = {
                                            onInstall(
                                                entry
                                            )
                                        }
                                    ) {

                                        Text(
                                            text =
                                                when {
                                                    installingExtensionId ==
                                                            entry.id -> {
                                                        "Installing..."
                                                    }

                                                    isInstalled -> {
                                                        "Installed"
                                                    }

                                                    else -> {
                                                        "Install"
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },

        confirmButton = {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                if (
                    installMessage != null
                ) {

                    Text(
                        text =
                            installMessage,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize =
                            12.sp,
                        modifier =
                            Modifier.weight(1f)
                    )
                }

                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Done")
                }
            }
        }
    )
}

@Composable
private fun MusiFlacBackButton(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(34.dp, 28.dp)
        ) {
            val stroke = 6.dp.toPx()
            val centerY = size.height / 2f
            val headX = 4.dp.toPx()
            val endX = size.width - 2.dp.toPx()
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(headX, centerY),
                end = androidx.compose.ui.geometry.Offset(endX, centerY),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Square
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(headX, centerY),
                end = androidx.compose.ui.geometry.Offset(14.dp.toPx(), 5.dp.toPx()),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Square
            )
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(headX, centerY),
                end = androidx.compose.ui.geometry.Offset(14.dp.toPx(), size.height - 5.dp.toPx()),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Square
            )
        }
    }
}

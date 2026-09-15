package com.laizycoder.musiflac.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner


private const val PREFS_NAME = "files_folders_settings"
private const val KEY_DOWNLOAD_TREE_URI = "download_tree_uri"
private const val KEY_DOWNLOAD_PATH = "download_path"
private const val KEY_FILENAME_FORMAT = "filename_format"
private const val KEY_SINGLE_FILENAME_FORMAT = "single_filename_format"
private const val KEY_FILENAME_ADVANCED = "filename_advanced"
private const val KEY_SINGLE_FILENAME_ADVANCED = "single_filename_advanced"

object DownloadFolderSettings {

    private const val DEFAULT_PATH = "/storage/emulated/0/Music"
    private const val DEFAULT_FORMAT = "{title} - {artist}"

    fun getPath(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOAD_PATH, DEFAULT_PATH) ?: DEFAULT_PATH

    fun getTreeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DOWNLOAD_TREE_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun saveTreeUri(context: Context, uri: Uri) {
        val path = uriToPrimaryStoragePath(uri)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOAD_TREE_URI, uri.toString())
            .putString(KEY_DOWNLOAD_PATH, path ?: uri.toString())
            .apply()
    }

    fun getFilenameFormat(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FILENAME_FORMAT, DEFAULT_FORMAT) ?: DEFAULT_FORMAT

    fun getSingleFilenameFormat(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SINGLE_FILENAME_FORMAT, DEFAULT_FORMAT) ?: DEFAULT_FORMAT

    fun saveFilenameFormat(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FILENAME_FORMAT, value).apply()
    }

    fun saveSingleFilenameFormat(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SINGLE_FILENAME_FORMAT, value).apply()
    }

    fun getFilenameAdvanced(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FILENAME_ADVANCED, false)

    fun getSingleFilenameAdvanced(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SINGLE_FILENAME_ADVANCED, false)

    fun saveFilenameAdvanced(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FILENAME_ADVANCED, value).apply()
    }

    fun saveSingleFilenameAdvanced(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SINGLE_FILENAME_ADVANCED, value).apply()
    }

    private fun uriToPrimaryStoragePath(uri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        if (documentId == "primary:") return "/storage/emulated/0"
        if (documentId.startsWith("primary:")) {
            val relative = documentId.removePrefix("primary:")
            return if (relative.isBlank()) "/storage/emulated/0"
            else "/storage/emulated/0/$relative"
        }
        return null
    }
}

private val filenameTags = listOf(
    "{artist}", "{title}", "{album}", "{track}",
    "{year}", "{date}", "{disc}", "{playlist_position}"
)

@Composable
fun FilesFoldersScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var downloadPath by remember { mutableStateOf(DownloadFolderSettings.getPath(context)) }
    var filenameFormat by remember { mutableStateOf(DownloadFolderSettings.getFilenameFormat(context)) }
    var singleFilenameFormat by remember {
        mutableStateOf(DownloadFolderSettings.getSingleFilenameFormat(context))
    }
    var showFilenameDialog by remember { mutableStateOf(false) }
    var showSingleFilenameDialog by remember { mutableStateOf(false) }
    var allFilesAccess by remember {
        mutableStateOf(Environment.isExternalStorageManager())
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allFilesAccess = Environment.isExternalStorageManager()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        DownloadFolderSettings.saveTreeUri(context, uri)
        downloadPath = DownloadFolderSettings.getPath(context)
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        FilesFoldersHeader(onBack)

        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 28.dp)
        ) {
            FilesFoldersSectionTitle("Download Location")
            FilesFoldersCard(
                icon = "□",
                title = "Download Directory",
                description = downloadPath,
                onClick = { folderPicker.launch(null) }
            )

            Spacer(Modifier.height(22.dp))

            FilesFoldersSectionTitle("File Settings")
            FilesFoldersCard(
                icon = "Tᵀ",
                title = "Filename Format",
                description = filenameFormat,
                onClick = { showFilenameDialog = true }
            )
            FilesFoldersCard(
                icon = "♫",
                title = "Single Filename Format",
                description = singleFilenameFormat,
                onClick = { showSingleFilenameDialog = true }
            )

            Spacer(Modifier.height(22.dp))

            FilesFoldersSectionTitle("Storage Access")
            FilesFoldersSwitchCard(
                icon = "▣",
                title = "All Files Access",
                description = if (allFilesAccess) {
                    "Full access to files and folders is enabled"
                } else {
                    "Allow access to manage all files on this device"
                },
                checked = allFilesAccess,
                onCheckedChange = {
                    if (!allFilesAccess) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )

                        runCatching {
                            context.startActivity(intent)
                        }.onFailure {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                )
                            )
                        }
                    } else {
                        // Android does not provide a normal in-app toggle for
                        // revoking MANAGE_EXTERNAL_STORAGE. The system settings
                        // page is the authoritative place for this permission.
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )

                        runCatching {
                            context.startActivity(intent)
                        }
                    }
                }
            )
        }
    }

    if (showFilenameDialog) {
        FilenameFormatDialog(
            title = "Filename Format",
            description = "Use {artist}, {title}, {album}, {track}, {year}, {date}, {disc} as placeholders.",
            initialValue = filenameFormat,
            initialAdvanced = DownloadFolderSettings.getFilenameAdvanced(context),
            onDismiss = { showFilenameDialog = false },
            onSave = { value, advanced ->
                filenameFormat = value
                DownloadFolderSettings.saveFilenameFormat(context, value)
                DownloadFolderSettings.saveFilenameAdvanced(context, advanced)
                showFilenameDialog = false
            }
        )
    }

    if (showSingleFilenameDialog) {
        FilenameFormatDialog(
            title = "Single Filename Format",
            description = "Filename pattern for singles and EPs. Uses the same tags as the album format.",
            initialValue = singleFilenameFormat,
            initialAdvanced = DownloadFolderSettings.getSingleFilenameAdvanced(context),
            onDismiss = { showSingleFilenameDialog = false },
            onSave = { value, advanced ->
                singleFilenameFormat = value
                DownloadFolderSettings.saveSingleFilenameFormat(context, value)
                DownloadFolderSettings.saveSingleFilenameAdvanced(context, advanced)
                showSingleFilenameDialog = false
            }
        )
    }
}

@Composable
private fun FilenameFormatDialog(
    title: String,
    description: String,
    initialValue: String,
    initialAdvanced: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var fieldValue by remember(initialValue) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initialValue))
    }
    var advanced by remember(initialAdvanced) { mutableStateOf(initialAdvanced) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Tap to insert tag:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(10.dp))

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filenameTags.forEach { tag ->
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                val text = fieldValue.text
                                val selection = fieldValue.selection
                                val start = selection.start.coerceIn(0, text.length)
                                val end = selection.end.coerceIn(start, text.length)
                                val updated = text.substring(0, start) + tag + text.substring(end)
                                val cursor = start + tag.length
                                fieldValue = androidx.compose.ui.text.input.TextFieldValue(
                                    text = updated,
                                    selection = androidx.compose.ui.text.TextRange(cursor)
                                )
                            },
                            label = { Text(tag) }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Show advanced tags",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Enable formatted tags for track padding and date patterns",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = advanced,
                        onCheckedChange = { advanced = it }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    onSave(
                        fieldValue.text.trim().ifBlank { "{title} - {artist}" },
                        advanced
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FilesFoldersHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 12.dp, end = 22.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "←",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Text(
            text = "Files & Folders",
            modifier = Modifier.padding(start = 14.dp),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FilesFoldersSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 22.dp, bottom = 10.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun FilesFoldersCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text(icon, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 26.sp)
        }
        Spacer(Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "›",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun FilesFoldersSwitchCard(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text(icon, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 25.sp)
        }
        Spacer(Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

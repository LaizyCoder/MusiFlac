package com.laizycoder.musiflac.ui.screens

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import com.laizycoder.musiflac.data.ScanningSettings
import kotlin.math.sin

private enum class EditableList {
    ALLOWLIST,
    BLOCKLIST
}

@Composable
fun ScanningScreen(
    onBack: () -> Unit,
    onRescan: () -> Unit = {},
    onDeepRescan: () -> Unit = {},
    rescanResultCount: Int? = null,
    deepScanning: Boolean = false,
    deepFoundCount: Int = 0,
    deepTotalCount: Int = 0,
    onDismissScanResult: () -> Unit = {}
) {
    val context = LocalContext.current

    var rescanOnLaunch by remember {
        mutableStateOf(ScanningSettings.isRescanOnLaunchEnabled(context))
    }
    var optimizedImageSaving by remember {
        mutableStateOf(ScanningSettings.isOptimizedImageSavingEnabled(context))
    }
    var useMediaStoreScanner by remember {
        mutableStateOf(ScanningSettings.isMediaStoreScannerEnabled(context))
    }
    var allowlist by remember {
        mutableStateOf(ScanningSettings.getAllowlist(context))
    }
    var blocklist by remember {
        mutableStateOf(ScanningSettings.getBlocklist(context))
    }
    var minimumDuration by remember {
        mutableStateOf(ScanningSettings.getMinimumTrackDurationSeconds(context))
    }
    var separators by remember {
        mutableStateOf(ScanningSettings.getMultiValueSeparators(context))
    }

    var editableList by remember { mutableStateOf<EditableList?>(null) }
    var newEntry by remember { mutableStateOf("") }
    var showMinimumDurationDialog by remember { mutableStateOf(false) }
    var showSeparatorsDialog by remember { mutableStateOf(false) }
    var minimumDurationInput by remember { mutableStateOf("") }
    var separatorsInput by remember { mutableStateOf("") }

    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    if (rescanResultCount != null && !deepScanning) {
        AlertDialog(
            onDismissRequest = onDismissScanResult,
            title = { Text("Rescanned Library") },
            text = { Text("${rescanResultCount} songs found") },
            confirmButton = {
                Button(onClick = onDismissScanResult) { Text("OK") }
            }
        )
    }

    if (deepScanning) {
        DeepRescanDialog(found = deepFoundCount, total = deepTotalCount)
    }

    editableList?.let { listType ->
        val entries = if (listType == EditableList.ALLOWLIST) allowlist else blocklist
        val title = if (listType == EditableList.ALLOWLIST) "Allowlist" else "Blocklist"

        AlertDialog(
            onDismissRequest = { editableList = null },
            title = { Text(title) },
            text = {
                Column {
                    if (entries.isEmpty()) {
                        Text(
                            "No entries yet. Add a file or folder path below.",
                            color = onSurfaceVariant
                        )
                    } else {
                        entries.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry,
                                    modifier = Modifier.weight(1f),
                                    color = onSurface
                                )
                                TextButton(
                                    onClick = {
                                        if (listType == EditableList.ALLOWLIST) {
                                            ScanningSettings.removeAllowlistEntry(context, entry)
                                            allowlist = ScanningSettings.getAllowlist(context)
                                        } else {
                                            ScanningSettings.removeBlocklistEntry(context, entry)
                                            blocklist = ScanningSettings.getBlocklist(context)
                                        }
                                    }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newEntry,
                        onValueChange = { newEntry = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("File or folder path") },
                        placeholder = { Text("/storage/emulated/0/Music") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val entry = newEntry.trim()
                            if (entry.isNotBlank()) {
                                if (listType == EditableList.ALLOWLIST) {
                                    ScanningSettings.addAllowlistEntry(context, entry)
                                    allowlist = ScanningSettings.getAllowlist(context)
                                } else {
                                    ScanningSettings.addBlocklistEntry(context, entry)
                                    blocklist = ScanningSettings.getBlocklist(context)
                                }
                                newEntry = ""
                            }
                        },
                        enabled = newEntry.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { editableList = null }) { Text("Done") }
            }
        )
    }

    if (showMinimumDurationDialog) {
        AlertDialog(
            onDismissRequest = { showMinimumDurationDialog = false },
            title = { Text("Minimum Track Duration") },
            text = {
                OutlinedTextField(
                    value = minimumDurationInput,
                    onValueChange = { value ->
                        minimumDurationInput = value.filter(Char::isDigit)
                    },
                    singleLine = true,
                    label = { Text("Seconds") },
                    placeholder = { Text("60") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val value = minimumDurationInput.toLongOrNull()?.coerceAtLeast(0L)
                        if (value != null) {
                            ScanningSettings.setMinimumTrackDurationSeconds(context, value)
                            minimumDuration = value
                            showMinimumDurationDialog = false
                        }
                    },
                    enabled = minimumDurationInput.toLongOrNull() != null
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showMinimumDurationDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSeparatorsDialog) {
        AlertDialog(
            onDismissRequest = { showSeparatorsDialog = false },
            title = { Text("Multi-Value Separators") },
            text = {
                Column {
                    Text(
                        "Enter literal separators separated by commas. Example: feat., ft., &",
                        color = onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = separatorsInput,
                        onValueChange = { separatorsInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text("Separators") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val values = separatorsInput
                            .split(',')
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .distinct()
                        ScanningSettings.setMultiValueSeparators(context, values)
                        separators = ScanningSettings.getMultiValueSeparators(context)
                        showSeparatorsDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSeparatorsDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScanningHeader(onBack = onBack, onSurface = onSurface)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 28.dp)
        ) {
            ScanningActionCard(
                title = "Rescan",
                description = "Look for any new media.",
                onClick = { if (!deepScanning) onRescan() },
                surface = surface,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            ScanningActionCard(
                title = "Deep Rescan",
                description = "Ignores any skip measures that speeds up the normal rescan feature.",
                onClick = { if (!deepScanning) onDeepRescan() },
                surface = surface,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(30.dp))

            ScanningSwitchCard(
                title = "Rescan on Launch",
                description = "If disabled, use the Rescan feature to detect new, updated, and removed tracks.",
                checked = rescanOnLaunch,
                onCheckedChange = {
                    rescanOnLaunch = it
                    ScanningSettings.setRescanOnLaunchEnabled(context, it)
                },
                surface = surface,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            ScanningSwitchCard(
                title = "Optimized Image Saving",
                description = "Tracks share the same artwork as their album when enabled.",
                checked = optimizedImageSaving,
                onCheckedChange = {
                    optimizedImageSaving = it
                    ScanningSettings.setOptimizedImageSavingEnabled(context, it)
                },
                surface = surface,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            ScanningSwitchCard(
                title = "🧪 Use MediaStore Scanner",
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    "Fast metadata scan. Disable it to read metadata directly from each file."
                } else {
                    "Requires Android 11+."
                },
                checked = useMediaStoreScanner,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                onCheckedChange = {
                    useMediaStoreScanner = it
                    ScanningSettings.setMediaStoreScannerEnabled(context, it)
                },
                surface = surface,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            ScanningInfoCard(
                "Allowlist",
                "${allowlist.size} entries",
                surface,
                onSurface,
                onSurfaceVariant,
                onClick = {
                    newEntry = ""
                    editableList = EditableList.ALLOWLIST
                }
            )
            ScanningInfoCard(
                "Blocklist",
                "${blocklist.size} entries",
                surface,
                onSurface,
                onSurfaceVariant,
                onClick = {
                    newEntry = ""
                    editableList = EditableList.BLOCKLIST
                }
            )
            ScanningInfoCard(
                "Minimum Track Duration",
                if (minimumDuration == 1L) "1 second" else "$minimumDuration seconds",
                surface,
                onSurface,
                onSurfaceVariant,
                onClick = {
                    minimumDurationInput = minimumDuration.toString()
                    showMinimumDurationDialog = true
                }
            )
            ScanningInfoCard(
                "Multi-Value Separators",
                "${separators.size} entries",
                surface,
                onSurface,
                onSurfaceVariant,
                onClick = {
                    separatorsInput = separators.joinToString(", ")
                    showSeparatorsDialog = true
                }
            )
        }
    }
}

@Composable
private fun DeepRescanDialog(found: Int, total: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "deep-rescan-wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave-phase"
    )

    val safeTotal = total.coerceAtLeast(found).coerceAtLeast(1)
    val progress = (found.toFloat() / safeTotal).coerceIn(0f, 1f)
    val waveColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Deep Rescan") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (found > 0) {
                        "Scanning tracks… $found songs found"
                    } else {
                        "Preparing library scan…"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(20.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                ) {
                    val bars = 36
                    val gap = size.width / bars
                    val center = size.height / 2f
                    val maxHeight = size.height * 0.42f
                    val progressBars = (bars * progress).toInt()

                    for (i in 0 until bars) {
                        val wave = ((sin(i * 0.72 + phase) + 1.0) / 2.0).toFloat()
                        val base = 0.18f + wave * 0.82f
                        val revealed = if (i <= progressBars) 1f else 0.18f
                        val barHeight = maxHeight * base * revealed
                        drawRoundRect(
                            color = waveColor,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                i * gap + gap * 0.28f,
                                center - barHeight
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                gap * 0.44f,
                                barHeight * 2f
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                gap * 0.18f,
                                gap * 0.18f
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ScanningHeader(onBack: () -> Unit, onSurface: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 22.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = onSurface, style = MaterialTheme.typography.headlineSmall)
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                "Scanning",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun ScanningActionCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = onSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(description, color = onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScanningSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) onSurface else onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(description, color = onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ScanningInfoCard(
    title: String,
    description: String,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, color = onSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(description, color = onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

enum class ThemeOption(
    val title: String,
    val description: String
) {
    SYSTEM(
        "System default",
        "Follow your device theme"
    ),
    LIGHT(
        "Light",
        "Always use light mode"
    ),
    DARK(
        "Dark",
        "Always use dark mode"
    ),
    AMOLED(
        "Pure Black AMOLED",
        "Pure black OLED-friendly mode"
    )
}

@Composable
fun SettingsScreen(
    selectedTheme: ThemeOption,
    onThemeSelected: (ThemeOption) -> Unit,
    onExtensions: () -> Unit,
    onBack: () -> Unit
) {

    var showThemeDialog by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 22.dp,
                end = 22.dp,
                top = 12.dp,
                bottom = 24.dp
            )
    ) {

        // ─────────────────────────────
        // Header
        // ─────────────────────────────

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            MusiFlacBackButton(
                onClick = onBack
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Spacer(
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        // ─────────────────────────────
        // Appearance
        // ─────────────────────────────

        SettingsSectionTitle("Appearance")

        SettingsCard(
            title = "Appearance",
            description = selectedTheme.title,
            icon = "◫",
            onClick = {
                showThemeDialog = true
            }
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        // ─────────────────────────────
        // Online
        // ─────────────────────────────

        SettingsSectionTitle("Online")

        SettingsCard(
            title = "Extensions",
            description = "Manage download providers",
            icon = "⌘",
            onClick = {
                onExtensions()
            }
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        // ─────────────────────────────
        // Personalization
        // ─────────────────────────────

        SettingsSectionTitle("Personalization")

        SettingsCard(
            title = "Backup",
            description = "Backup your MusiFlac data",
            icon = "⇩",
            onClick = {
                // Backup screen coming later
            }
        )

        SettingsCard(
            title = "Insights",
            description = "Your listening statistics",
            icon = "▥",
            onClick = {
                // Insights screen coming later
            }
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        // ─────────────────────────────
        // Music
        // ─────────────────────────────

        SettingsSectionTitle("Music")

        SettingsCard(
            title = "Metadata",
            description = "Cover art, tags, ReplayGain, providers",
            icon = "◇",
            onClick = {
                // Metadata screen coming later
            }
        )

        SettingsCard(
            title = "Lyrics",
            description = "Embed, mode, providers, language options",
            icon = "♫",
            onClick = {
                // Lyrics screen coming later
            }
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        // ─────────────────────────────
        // Audio
        // ─────────────────────────────

        SettingsSectionTitle("Audio")

        SettingsCard(
            title = "Playback",
            description = "Configure playback behavior",
            icon = "◷",
            onClick = {
                // Playback screen coming later
            }
        )

        SettingsCard(
            title = "Scanning",
            description = "Configure music library scanning",
            icon = "⌕",
            onClick = {
                // Scanning screen coming later
            }
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        // ─────────────────────────────
        // Downloads
        // ─────────────────────────────

        SettingsSectionTitle("Downloads")

        SettingsCard(
            title = "Download",
            description = "Service, quality, fallback",
            icon = "↓",
            onClick = {
                // Download screen coming later
            }
        )

        SettingsCard(
            title = "Files & Folders",
            description = "Download location, filename, folder structure",
            icon = "□",
            onClick = {
                // Files & Folders screen coming later
            }
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        // ─────────────────────────────
        // Advanced
        // ─────────────────────────────

        SettingsSectionTitle("Advanced")

        SettingsCard(
            title = "Experimental Features",
            description = "Try new and experimental features",
            icon = "△",
            onClick = {
                // Experimental features coming later
            }
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        // ─────────────────────────────
        // About
        // ─────────────────────────────

        SettingsSectionTitle("About")

        SettingsCard(
            title = "GitHub Repository",
            description = "Report issues, contribute, or request features",
            icon = "↗",
            onClick = {
                // Coming later
            }
        )

        SettingsCard(
            title = "License",
            description = "MusiFlac open-source license",
            icon = "↗",
            onClick = {
                // Coming later
            }
        )

        SettingsCard(
            title = "Privacy Policy",
            description = "How MusiFlac handles your data",
            icon = "↗",
            onClick = {
                // Coming later
            }
        )

        SettingsCard(
            title = "Third-Party Software",
            description = "Libraries and software used by MusiFlac",
            icon = "▣",
            onClick = {
                // Coming later
            }
        )

        SettingsCard(
            title = "Version",
            description = "MusiFlac 1.0.0",
            icon = "ⓘ",
            onClick = {
                // Coming later
            }
        )

        SettingsCard(
            title = "Check for Updates",
            description = "Check whether a newer version is available",
            icon = "↻",
            onClick = {
                // Coming later
            }
        )

        SettingsSwitchCard(
            title = "Pre-Release Notification",
            description = "Receive notifications about pre-release versions",
            icon = "●"
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "MusiFlac",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = "Made for music lovers.",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showThemeDialog) {

        ThemeSelectionDialog(
            selectedTheme = selectedTheme,

            onThemeSelected = {
                onThemeSelected(it)
                showThemeDialog = false
            },

            onDismiss = {
                showThemeDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(
    title: String
) {
    Text(
        text = title,
        modifier = Modifier.padding(
            start = 4.dp,
            bottom = 10.dp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    icon: String,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(
                RoundedCornerShape(12.dp)
            )
            // Cards intentionally stay dark in BOTH themes.
            .background(
                MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable {
                onClick()
            }
            .padding(
                horizontal = 20.dp,
                vertical = 17.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = icon,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(
            modifier = Modifier.size(18.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = "›",
            color = Color(0xFF888888),
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    description: String,
    icon: String
) {

    var enabled by remember {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(
                RoundedCornerShape(12.dp)
            )
            .background(
                MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable {
                enabled = !enabled
            }
            .padding(
                horizontal = 20.dp,
                vertical = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {

            Text(
                text = icon,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(
            modifier = Modifier.size(18.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        androidx.compose.material3.Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
            }
        )
    }
}

@Composable
private fun ThemeSelectionDialog(
    selectedTheme: ThemeOption,
    onThemeSelected: (ThemeOption) -> Unit,
    onDismiss: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Appearance"
            )
        },

        text = {

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                ThemeOption.entries.forEach { option ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeSelected(option)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        RadioButton(
                            selected = selectedTheme == option,
                            onClick = {
                                onThemeSelected(option)
                            }
                        )

                        Column(
                            modifier = Modifier.padding(start = 10.dp)
                        ) {

                            Text(
                                text = option.title
                            )

                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("Close")
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

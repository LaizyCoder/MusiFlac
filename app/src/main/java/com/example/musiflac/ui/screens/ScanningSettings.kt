package com.laizycoder.musiflac.data

import android.content.Context
import java.io.File

/**
 * Single source of truth for all library-scanning preferences.
 * The UI and MusicScanner both read/write these values so the controls
 * affect actual scans instead of only changing Compose state.
 */
object ScanningSettings {
    private const val PREFS_NAME = "scanning_settings"

    private const val KEY_RESCAN_ON_LAUNCH = "rescan_on_launch"
    private const val KEY_OPTIMIZED_IMAGE_SAVING = "optimized_image_saving"
    private const val KEY_USE_MEDIASTORE_SCANNER = "use_mediastore_scanner"
    private const val KEY_ALLOWLIST = "allowlist"
    private const val KEY_BLOCKLIST = "blocklist"
    private const val KEY_MIN_DURATION_SECONDS = "minimum_track_duration_seconds"
    private const val KEY_MULTI_VALUE_SEPARATORS = "multi_value_separators"

    private const val DEFAULT_MIN_DURATION_SECONDS = 60L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun isRescanOnLaunchEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RESCAN_ON_LAUNCH, true)

    fun setRescanOnLaunchEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_RESCAN_ON_LAUNCH, enabled)
            .apply()
    }

    fun isOptimizedImageSavingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OPTIMIZED_IMAGE_SAVING, true)

    fun setOptimizedImageSavingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_OPTIMIZED_IMAGE_SAVING, enabled)
            .apply()
    }

    fun isMediaStoreScannerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(
            KEY_USE_MEDIASTORE_SCANNER,
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        )

    fun setMediaStoreScannerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_USE_MEDIASTORE_SCANNER, enabled)
            .apply()
    }

    fun getAllowlist(context: Context): List<String> =
        prefs(context).getStringSet(KEY_ALLOWLIST, emptySet())
            ?.map(::normalizeEntry)
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.sorted()
            ?: emptyList()

    fun setAllowlist(context: Context, entries: Collection<String>) {
        prefs(context).edit()
            .putStringSet(
                KEY_ALLOWLIST,
                entries.map(::normalizeEntry)
                    .filter(String::isNotBlank)
                    .toSet()
            )
            .apply()
    }

    fun getBlocklist(context: Context): List<String> =
        prefs(context).getStringSet(KEY_BLOCKLIST, emptySet())
            ?.map(::normalizeEntry)
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.sorted()
            ?: emptyList()

    fun setBlocklist(context: Context, entries: Collection<String>) {
        prefs(context).edit()
            .putStringSet(
                KEY_BLOCKLIST,
                entries.map(::normalizeEntry)
                    .filter(String::isNotBlank)
                    .toSet()
            )
            .apply()
    }

    fun addAllowlistEntry(context: Context, entry: String) {
        setAllowlist(context, getAllowlist(context) + entry)
    }

    fun removeAllowlistEntry(context: Context, entry: String) {
        setAllowlist(context, getAllowlist(context).filterNot { it == normalizeEntry(entry) })
    }

    fun addBlocklistEntry(context: Context, entry: String) {
        setBlocklist(context, getBlocklist(context) + entry)
    }

    fun removeBlocklistEntry(context: Context, entry: String) {
        setBlocklist(context, getBlocklist(context).filterNot { it == normalizeEntry(entry) })
    }

    fun getMinimumTrackDurationSeconds(context: Context): Long =
        prefs(context).getLong(
            KEY_MIN_DURATION_SECONDS,
            DEFAULT_MIN_DURATION_SECONDS
        ).coerceAtLeast(0L)

    fun setMinimumTrackDurationSeconds(context: Context, seconds: Long) {
        prefs(context).edit()
            .putLong(KEY_MIN_DURATION_SECONDS, seconds.coerceAtLeast(0L))
            .apply()
    }

    fun getMultiValueSeparators(context: Context): List<String> =
        prefs(context).getStringSet(KEY_MULTI_VALUE_SEPARATORS, emptySet())
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.sortedByDescending(String::length)
            ?: emptyList()

    fun setMultiValueSeparators(context: Context, separators: Collection<String>) {
        prefs(context).edit()
            .putStringSet(
                KEY_MULTI_VALUE_SEPARATORS,
                separators.map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .toSet()
            )
            .apply()
    }

    /**
     * Returns true when a file should be visible to the normal library scan.
     * Allowlist is inclusive when non-empty; blocklist always wins.
     */
    fun shouldIncludePath(context: Context, path: String): Boolean {
        val normalizedPath = normalizeEntry(path)
        if (normalizedPath.isBlank()) return false

        val allowlist = getAllowlist(context)
        val blocklist = getBlocklist(context)

        if (blocklist.any { matchesPath(normalizedPath, it) }) {
            return false
        }

        if (allowlist.isNotEmpty() &&
            allowlist.none { matchesPath(normalizedPath, it) }
        ) {
            return false
        }

        return true
    }

    /**
     * Applies configured literal artist separators to a single metadata value.
     * Example: "Artist feat. Guest" + "feat." => "Artist, Guest".
     */
    fun normalizeMultiValue(context: Context, value: String): String {
        var normalized = value.trim()
        if (normalized.isBlank()) return normalized

        for (separator in getMultiValueSeparators(context)) {
            normalized = normalized.replace(separator, ", ")
        }

        return normalized
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(", ")
    }

    private fun matchesPath(path: String, entry: String): Boolean {
        val normalizedEntry = normalizeEntry(entry)
        if (normalizedEntry.isBlank()) return false

        return path == normalizedEntry ||
                path.startsWith(normalizedEntry + File.separator)
    }

    private fun normalizeEntry(value: String): String {
        return value.trim()
            .replace('\\', File.separatorChar)
            .trimEnd(File.separatorChar)
            .ifBlank { File.separator }
    }
}

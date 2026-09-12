package com.laizycoder.musiflac.online

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resolves an online metadata result against the configured download
 * providers and downloads the matching audio file.
 *
 * Provider order comes from ExtensionManager's Download Priority setting.
 * Fallback Extensions controls which providers may be used after the first
 * provider fails to find a usable match.
 */
object OnlineDownloadResolver {

    private const val TAG = "OnlineDownload"

    data class Result(
        val success: Boolean,
        val filePath: String? = null,
        val providerId: String? = null,
        val error: String? = null
    )

    fun resolveAndDownload(
        context: Context,
        extensionManager: ExtensionManager,
        track: OnlineTrack,
        outputDirectory: File
    ): Result {
        context.applicationContext

        Log.d(TAG, "START: ${track.title} - ${track.artist} | source=${track.source} id=${track.id} isrc=${track.isrc}")

        if (!outputDirectory.exists()) {
            Log.d(TAG, "Creating output directory: ${outputDirectory.absolutePath}")
            outputDirectory.mkdirs()
        }

        val allDownloadProviders = extensionManager
            .getEnabled()
            .filter { ExtensionType.DOWNLOAD in it.types }

        Log.d(TAG, "Enabled download providers: ${allDownloadProviders.joinToString { "${it.name}(${it.id})" }}")

        if (allDownloadProviders.isEmpty()) {
            Log.e(TAG, "NO DOWNLOAD PROVIDERS")
            return Result(false, error = "No enabled download providers installed.")
        }

        val orderedIds = extensionManager.getPriority(
            key = "download",
            defaultIds = allDownloadProviders.map { it.id }
        )

        Log.d(TAG, "Priority IDs: $orderedIds")

        val orderedProviders = orderedIds.mapNotNull { id ->
            allDownloadProviders.firstOrNull { it.id == id }
        }

        Log.d(TAG, "Ordered providers: ${orderedProviders.joinToString { "${it.name}(${it.id})" }}")

        if (orderedProviders.isEmpty()) {
            Log.e(TAG, "NO ORDERED PROVIDERS")
            return Result(false, error = "No enabled download providers are available.")
        }

        val fallbackIds = extensionManager.getFallbackProviders(
            defaultIds = orderedProviders.map { it.id }
        ).toSet()

        Log.d(TAG, "Fallback IDs: $fallbackIds")

        // Try every enabled provider in priority order. A failed provider
        // must not stop the resolver; the next provider gets a chance.
        // YouTube Music is deliberately kept last because it is primarily
        // a fallback source when the lossless catalog providers fail.
        val providersToTry = buildList {
            orderedProviders
                .filterNot { provider ->
                    val id = provider.id.lowercase()
                    val name = provider.name.lowercase()
                    id.contains("ytmusic") ||
                            id.contains("youtube") ||
                            name.contains("youtube music")
                }
                .forEach { add(it) }

            orderedProviders
                .filter { provider ->
                    val id = provider.id.lowercase()
                    val name = provider.name.lowercase()
                    id.contains("ytmusic") ||
                            id.contains("youtube") ||
                            name.contains("youtube music")
                }
                .forEach { add(it) }
        }.distinctBy { it.id }

        Log.d(TAG, "Providers to try (automatic fallback): ${providersToTry.joinToString { "${it.name}(${it.id})" }}")

        var lastError = "No provider could resolve this track."

        for (provider in providersToTry) {
            Log.d(TAG, "========== PROVIDER: ${provider.name} (${provider.id}) ==========")

            val options = JSONObject()
                .put("duration_ms", track.duration ?: 0L)
                .put("spotify_id", if (track.source.equals("Spotify Web", true)) track.id else "")
                .put(
                    "track",
                    JSONObject()
                        .put("album_name", track.album ?: "")
                        .put("isrc", track.isrc ?: "")
                )

            val availabilityArgs = JSONArray()
                .put(track.isrc ?: "")
                .put(track.title)
                .put(track.artist)
                .put(options)
                .toString()

            Log.d(TAG, "CALL checkAvailability -> ${provider.id}")
            Log.d(TAG, "checkAvailability args=$availabilityArgs")

            val availability = try {
                val start = System.currentTimeMillis()
                val response = GoBackend.callExtensionMethod(
                    provider.id,
                    "checkAvailability",
                    availabilityArgs
                )
                Log.d(TAG, "RETURN checkAvailability <- ${provider.id} (${System.currentTimeMillis() - start}ms): $response")
                response
            } catch (error: Throwable) {
                Log.e(TAG, "EXCEPTION checkAvailability ${provider.id}: ${error.message}", error)
                lastError = "${provider.name}: ${error.message ?: "availability check failed"}"
                continue
            }

            val availabilityJson = parseObject(availability)
            if (availabilityJson == null) {
                Log.e(TAG, "INVALID availability JSON from ${provider.id}: $availability")
                lastError = "${provider.name}: invalid availability response"
                continue
            }

            Log.d(TAG, "Availability JSON: $availabilityJson")

            if (!availabilityJson.optBoolean("available", false)) {
                lastError = "${provider.name}: ${availabilityJson.optString("reason").ifBlank { "track not available" }}"
                Log.d(TAG, "UNAVAILABLE: $lastError")
                continue
            }

            val providerTrackId = availabilityJson.optString("track_id").trim()
            Log.d(TAG, "Resolved provider track ID: '$providerTrackId'")

            if (providerTrackId.isEmpty()) {
                lastError = "${provider.name}: provider returned no track ID"
                Log.e(TAG, lastError)
                continue
            }

            val preparedContext = availabilityJson.optJSONObject("prepared_context")
                ?: availabilityJson.optJSONObject("preparedContext")

            Log.d(TAG, "Prepared context present: ${preparedContext != null}")

            val safeBaseName = sanitizeFileName("${track.artist} - ${track.title}")
            val outputBase = File(
                outputDirectory,
                "${safeBaseName}_${track.id.take(12)}"
            ).absolutePath

            val downloadOptions = JSONObject()
                .put("spotify_id", if (track.source.equals("Spotify Web", true)) track.id else "")

            preparedContext?.let { downloadOptions.put("preparedContext", it) }

            val downloadArgs = JSONArray()
                .put(providerTrackId)
                .put("flac")
                .put(outputBase)
                .put(JSONObject.NULL)
                .put(downloadOptions)
                .toString()

            Log.d(TAG, "CALL download -> ${provider.id}")
            Log.d(TAG, "download args=$downloadArgs")

            val downloadResult = try {
                val start = System.currentTimeMillis()
                val response = GoBackend.callExtensionMethod(
                    provider.id,
                    "download",
                    downloadArgs
                )
                Log.d(TAG, "RETURN download <- ${provider.id} (${System.currentTimeMillis() - start}ms): $response")
                response
            } catch (error: Throwable) {
                Log.e(TAG, "EXCEPTION download ${provider.id}: ${error.message}", error)
                lastError = "${provider.name}: ${error.message ?: "download failed"}"
                continue
            }

            val downloadJson = parseObject(downloadResult)
            if (downloadJson == null) {
                Log.e(TAG, "INVALID download JSON from ${provider.id}: $downloadResult")
                lastError = "${provider.name}: invalid download response"
                continue
            }

            Log.d(TAG, "Download JSON: $downloadJson")

            if (!downloadJson.optBoolean("success", false)) {
                lastError = "${provider.name}: ${downloadJson.optString("error_message").ifBlank { downloadJson.optString("error") }.ifBlank { "download failed" }}"
                Log.e(TAG, "DOWNLOAD FAILED: $lastError")
                continue
            }

            val filePath = downloadJson.optString("file_path")
                .ifBlank { downloadJson.optString("path") }
                .trim()

            Log.d(TAG, "Returned file path: '$filePath'")

            if (filePath.isEmpty() || !File(filePath).isFile) {
                lastError = "${provider.name}: download succeeded but no local file was returned"
                Log.e(TAG, lastError)
                continue
            }

            Log.d(TAG, "SUCCESS: ${File(filePath).absolutePath} | provider=${provider.id} | size=${File(filePath).length()}")

            return Result(
                success = true,
                filePath = filePath,
                providerId = provider.id
            )
        }

        Log.e(TAG, "ALL PROVIDERS FAILED: $lastError")

        return Result(
            success = false,
            error = lastError
        )
    }

    private fun parseObject(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        return try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            try {
                val value = org.json.JSONTokener(trimmed).nextValue()
                value as? JSONObject
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "MusiFlac Track" }
            .take(180)
    }
}

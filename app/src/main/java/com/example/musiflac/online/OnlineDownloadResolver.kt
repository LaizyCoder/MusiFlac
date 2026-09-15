package com.laizycoder.musiflac.online

import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import com.laizycoder.musiflac.ui.screens.DownloadFolderSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Performs a download through one explicitly selected download extension.
 *
 * The extension runtime is sandboxed. Therefore the third download()
 * argument MUST be a relative path inside the extension sandbox.
 * After the extension finishes, the host copies the resulting file into
 * MusiFlac's actual download directory.
 *
 * Signed-session verification is handled here as a one-time retry:
 * if the provider reports verification_required / VERIFY_REQUIRED, the
 * in-app verification coordinator is opened and the same operation is
 * retried once after the session is authenticated.
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
        providerId: String,
        qualityId: String,
        outputDirectory: File
    ): Result {
        return resolveInternal(
            context = context,
            extensionManager = extensionManager,
            track = track,
            providerId = providerId,
            qualityId = qualityId,
            outputDirectory = outputDirectory,
            allowVerification = true
        )
    }

    private fun resolveInternal(
        context: Context,
        extensionManager: ExtensionManager,
        track: OnlineTrack,
        providerId: String,
        qualityId: String,
        outputDirectory: File,
        allowVerification: Boolean
    ): Result {
        val provider = extensionManager
            .getEnabled()
            .firstOrNull {
                it.id == providerId &&
                        ExtensionType.DOWNLOAD in it.types
            }
            ?: return Result(
                success = false,
                error = "Selected download provider is not enabled."
            )

        val quality = extensionManager
            .getDownloadQualities(provider.id)
            .firstOrNull { it.id == qualityId }

        if (quality == null) {
            return Result(
                success = false,
                providerId = provider.id,
                error = "Selected quality is not supported by ${provider.name}."
            )
        }

        Log.d(
            TAG,
            "START: provider=${provider.id}, quality=${quality.id}, " +
                    "track=${track.title} - ${track.artist}, " +
                    "verification=$allowVerification"
        )

        // The selected download folder is SAF-backed. Do not touch the
        // shared-storage filesystem path here; Android may reject direct
        // File access even though the user granted the directory through SAF.
        // The extension downloads into its own sandbox, then we copy the
        // completed file into the persisted SAF tree below.

        val isSpotify =
            track.source.equals("Spotify Web", ignoreCase = true)

        val options = JSONObject()
            .put("duration_ms", track.duration ?: 0L)
            .put(
                "spotify_id",
                if (isSpotify) track.id else ""
            )
            .put("requested_quality", quality.id)
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

        val availability = try {
            Log.d(
                TAG,
                "CALL checkAvailability -> ${provider.id}"
            )

            GoBackend.callExtensionMethod(
                provider.id,
                "checkAvailability",
                availabilityArgs
            )
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "checkAvailability failed for ${provider.id}: ${error.message}",
                error
            )

            if (
                allowVerification &&
                isVerificationRequired(error.message)
            ) {
                return retryAfterVerification(
                    context = context,
                    extensionId = provider.id,
                    extensionManager = extensionManager,
                    track = track,
                    providerId = providerId,
                    qualityId = qualityId,
                    outputDirectory = outputDirectory
                )
            }

            return Result(
                success = false,
                providerId = provider.id,
                error = "${provider.name}: ${
                    error.message ?: "availability check failed"
                }"
            )
        }

        val availabilityJson =
            parseObject(availability)
                ?: return Result(
                    success = false,
                    providerId = provider.id,
                    error = "${provider.name}: invalid availability response"
                )

        if (
            !availabilityJson.optBoolean(
                "available",
                false
            )
        ) {
            if (
                allowVerification &&
                isVerificationRequired(availabilityJson)
            ) {
                return retryAfterVerification(
                    context = context,
                    extensionId = provider.id,
                    extensionManager = extensionManager,
                    track = track,
                    providerId = providerId,
                    qualityId = qualityId,
                    outputDirectory = outputDirectory
                )
            }

            return Result(
                success = false,
                providerId = provider.id,
                error = "${provider.name}: ${
                    availabilityJson
                        .optString("reason")
                        .ifBlank {
                            availabilityJson
                                .optString("error")
                        }
                        .ifBlank {
                            "track not available"
                        }
                }"
            )
        }

        val providerTrackId =
            availabilityJson
                .optString("track_id")
                .trim()

        if (providerTrackId.isBlank()) {
            return Result(
                success = false,
                providerId = provider.id,
                error = "${provider.name}: provider returned no track ID"
            )
        }

        val preparedContext =
            availabilityJson.optJSONObject(
                "prepared_context"
            )
                ?: availabilityJson.optJSONObject(
                    "preparedContext"
                )

        preparedContext?.let {
            options.put(
                "preparedContext",
                it
            )
        }

        /*
         * IMPORTANT:
         *
         * Do NOT pass outputDirectory.absolutePath here.
         * The official extension runtime intentionally rejects absolute
         * paths because file.download() is confined to the extension
         * sandbox.
         *
         * The extension creates the file in its own sandbox and returns
         * the real local path. The host copies that file into
         * outputDirectory below.
         */
        val safeBaseName =
            sanitizeFileName(
                "${track.artist} - ${track.title}"
            )

        val extensionOutputName =
            "${safeBaseName}_${track.id.take(12)}"

        val downloadArgs = JSONArray()
            .put(providerTrackId)
            .put(quality.id)
            .put(extensionOutputName)
            .put(JSONObject.NULL)
            .put(options)
            .toString()

        Log.d(
            TAG,
            "CALL download -> ${provider.id}, " +
                    "quality=${quality.id}, " +
                    "trackId=$providerTrackId, " +
                    "sandboxOutput=$extensionOutputName"
        )

        val downloadResult = try {
            GoBackend.callExtensionMethod(
                provider.id,
                "download",
                downloadArgs
            )
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "download failed for ${provider.id}: ${error.message}",
                error
            )

            if (
                allowVerification &&
                isVerificationRequired(error.message)
            ) {
                return retryAfterVerification(
                    context = context,
                    extensionId = provider.id,
                    extensionManager = extensionManager,
                    track = track,
                    providerId = providerId,
                    qualityId = qualityId,
                    outputDirectory = outputDirectory
                )
            }

            return Result(
                success = false,
                providerId = provider.id,
                error = "${provider.name}: ${
                    error.message ?: "download failed"
                }"
            )
        }

        val downloadJson =
            parseObject(downloadResult)
                ?: return Result(
                    success = false,
                    providerId = provider.id,
                    error = "${provider.name}: invalid download response"
                )

        if (
            !downloadJson.optBoolean(
                "success",
                false
            )
        ) {
            val message =
                downloadJson
                    .optString("error_message")
                    .ifBlank {
                        downloadJson.optString("error")
                    }
                    .ifBlank {
                        "download failed"
                    }

            if (
                allowVerification &&
                isVerificationRequired(downloadJson)
            ) {
                return retryAfterVerification(
                    context = context,
                    extensionId = provider.id,
                    extensionManager = extensionManager,
                    track = track,
                    providerId = providerId,
                    qualityId = qualityId,
                    outputDirectory = outputDirectory
                )
            }

            return Result(
                success = false,
                providerId = provider.id,
                error = "${provider.name}: $message"
            )
        }

        val sandboxPath =
            downloadJson
                .optString("file_path")
                .ifBlank {
                    downloadJson.optString("path")
                }
                .trim()

        if (sandboxPath.isBlank()) {
            return Result(
                success = false,
                providerId = provider.id,
                error =
                    "${provider.name}: download succeeded but no local file was returned"
            )
        }

        val sandboxFile = File(sandboxPath)

        if (!sandboxFile.isFile) {
            return Result(
                success = false,
                providerId = provider.id,
                error =
                    "${provider.name}: returned file does not exist"
            )
        }

        val destinationName =
            sanitizeFileName(
                sandboxFile.name
            ).ifBlank {
                "$extensionOutputName.flac"
            }

        return try {
            /*
             * The download directory is selected through Android's Storage
             * Access Framework. A path such as /storage/emulated/0/Music is
             * only a displayable filesystem path; it is NOT a writable grant
             * on modern Android. Use the persisted tree URI to create and
             * write the final file instead.
             */
            val treeUri = DownloadFolderSettings.getTreeUri(context)
                ?: throw IllegalStateException(
                    "No download folder is selected. Open Files & Folders and select a Download Directory."
                )

            val sourceSize = sandboxFile.length()

            val finalUri = copyDownloadedFileToTree(
                context = context,
                source = sandboxFile,
                treeUri = treeUri,
                displayName = destinationName
            )

            /*
             * The extension sandbox file is only a staging file now.
             * Remove it after the host copy succeeds so the sandbox does
             * not accumulate completed downloads.
             */
            try {
                sandboxFile.delete()
            } catch (_: Throwable) {
                // Cleanup failure must not turn a successful download
                // into a failed download.
            }

            Log.d(
                TAG,
                "SUCCESS: provider=${provider.id}, " +
                        "quality=${quality.id}, " +
                        "uri=$finalUri, " +
                        "size=$sourceSize"
            )

            Result(
                success = true,
                filePath = finalUri.toString(),
                providerId = provider.id
            )
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "Failed to move downloaded file into MusiFlac storage",
                error
            )

            Result(
                success = false,
                providerId = provider.id,
                error =
                    "${provider.name}: ${
                        error.message
                            ?: "failed to save downloaded file"
                    }"
            )
        }
    }

    private fun retryAfterVerification(
        context: Context,
        extensionId: String,
        extensionManager: ExtensionManager,
        track: OnlineTrack,
        providerId: String,
        qualityId: String,
        outputDirectory: File
    ): Result {
        Log.d(
            TAG,
            "Verification required by $extensionId. " +
                    "Opening in-app verification."
        )

        val verified =
            try {
                ExtensionVerificationCoordinator
                    .awaitVerification(
                        context = context,
                        extensionId = extensionId
                    )
            } catch (error: Throwable) {
                Log.e(
                    TAG,
                    "Verification failed for $extensionId",
                    error
                )
                false
            }

        if (!verified) {
            return Result(
                success = false,
                providerId = extensionId,
                error =
                    "Verification was not completed for $extensionId."
            )
        }

        Log.d(
            TAG,
            "Verification completed for $extensionId. Retrying download once."
        )

        return resolveInternal(
            context = context,
            extensionManager = extensionManager,
            track = track,
            providerId = providerId,
            qualityId = qualityId,
            outputDirectory = outputDirectory,
            allowVerification = false
        )
    }

    private fun isVerificationRequired(
        message: String?
    ): Boolean {
        val value =
            message
                ?.trim()
                ?.lowercase()
                ?: return false

        return value.contains("verify_required") ||
                value.contains("verification_required") ||
                value.contains("verification required") ||
                value.contains("needsverification") ||
                value.contains("needs verification")
    }

    private fun isVerificationRequired(
        json: JSONObject
    ): Boolean {
        if (
            json.optBoolean(
                "needsVerification",
                false
            )
        ) {
            return true
        }

        if (
            json.optBoolean(
                "verification_required",
                false
            )
        ) {
            return true
        }

        val error =
            json.optString("error")
                .ifBlank {
                    json.optString("error_message")
                }
                .ifBlank {
                    json.optString("error_type")
                }
                .ifBlank {
                    json.optString("errorType")
                }

        return isVerificationRequired(error)
    }

    private fun copyDownloadedFileToTree(
        context: Context,
        source: File,
        treeUri: android.net.Uri,
        displayName: String
    ): android.net.Uri {
        val resolver = context.contentResolver

        if (!DocumentsContract.isTreeUri(treeUri)) {
            throw IllegalArgumentException(
                "Saved download folder URI is not a valid storage tree."
            )
        }

        val mimeType = "audio/flac"
        val finalName =
            if (displayName.lowercase().endsWith(".flac")) {
                displayName
            } else {
                "$displayName.flac"
            }

        val treeDocumentId =
            DocumentsContract.getTreeDocumentId(treeUri)

        val parentDocumentUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                treeDocumentId
            )

        val destinationUri =
            DocumentsContract.createDocument(
                resolver,
                parentDocumentUri,
                mimeType,
                finalName
            ) ?: throw IOException(
                "Android could not create '$finalName' in the selected download folder."
            )

        try {
            resolver.openOutputStream(destinationUri, "w")?.use { output ->
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(1024 * 1024)

                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }

                    output.flush()
                }
            } ?: throw IOException(
                "Android could not open the selected download folder for writing."
            )
        } catch (error: Throwable) {
            try {
                DocumentsContract.deleteDocument(resolver, destinationUri)
            } catch (_: Throwable) {
                // Best-effort cleanup of a partially created document.
            }
            throw error
        }

        return destinationUri
    }

    private fun parseObject(
        raw: String
    ): JSONObject? {
        val trimmed =
            raw.trim()

        if (trimmed.isEmpty()) {
            return null
        }

        return try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            try {
                org.json.JSONTokener(
                    trimmed
                ).nextValue() as? JSONObject
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun sanitizeFileName(
        value: String
    ): String {
        return value
            .replace(
                Regex("[\\\\/:*?\"<>|]"),
                "_"
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
            .ifBlank {
                "MusiFlac Track"
            }
            .take(180)
    }
}

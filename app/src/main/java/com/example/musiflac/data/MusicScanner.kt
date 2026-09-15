package com.laizycoder.musiflac.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

class MusicScanner(
    private val context: Context
) {

    fun scan(): List<MusicTrack> = scanInternal(
        onProgress = null,
        forceDeep = false
    )

    /**
     * Deep scan deliberately uses the slower metadata path. This bypasses the
     * fast MediaStore metadata shortcut while still respecting user filters.
     */
    fun scanWithProgress(
        onProgress: (found: Int, total: Int) -> Unit
    ): List<MusicTrack> = scanInternal(
        onProgress = onProgress,
        forceDeep = true
    )

    private fun scanInternal(
        onProgress: ((found: Int, total: Int) -> Unit)?,
        forceDeep: Boolean
    ): List<MusicTrack> {
        val candidates = queryMediaStore()
        val filteredCandidates = candidates.filter { candidate ->
            candidate.duration >= ScanningSettings.getMinimumTrackDurationSeconds(context) * 1000L &&
                    ScanningSettings.shouldIncludePath(context, candidate.filePath)
        }

        onProgress?.invoke(0, filteredCandidates.size)

        val useFastMediaStore =
            ScanningSettings.isMediaStoreScannerEnabled(context) && !forceDeep

        val tracks = mutableListOf<MusicTrack>()

        for ((index, candidate) in filteredCandidates.withIndex()) {
            val track = if (useFastMediaStore) {
                buildFastTrack(candidate)
            } else {
                buildDeepTrack(candidate)
            }

            if (track != null) {
                tracks += track
            }

            onProgress?.invoke(index + 1, filteredCandidates.size)
        }

        return tracks.sortedBy { it.title.lowercase() }
    }

    private data class Candidate(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val filePath: String,
        val albumId: Long
    )

    private fun queryMediaStore(): List<Candidate> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val resolver: ContentResolver = context.contentResolver
        val candidates = mutableListOf<Candidate>()

        resolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn).orEmpty()
                if (path.isBlank()) continue

                candidates += Candidate(
                    id = cursor.getLong(idColumn),
                    title = cursor.getString(titleColumn) ?: "Unknown Title",
                    artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                    album = cursor.getString(albumColumn) ?: "Unknown Album",
                    duration = cursor.getLong(durationColumn),
                    filePath = path,
                    albumId = cursor.getLong(albumIdColumn)
                )
            }
        }

        return candidates
    }

    private fun buildFastTrack(candidate: Candidate): MusicTrack {
        val artist = ScanningSettings.normalizeMultiValue(
            context,
            candidate.artist
        )

        val artworkUri = if (
            ScanningSettings.isOptimizedImageSavingEnabled(context)
        ) {
            "content://media/external/audio/albumart/${candidate.albumId}"
        } else {
            saveEmbeddedArtwork(
                filePath = candidate.filePath,
                cacheKey = "track_${candidate.id}"
            )
        }

        return MusicTrack(
            id = candidate.id,
            title = candidate.title,
            artist = artist.ifBlank { "Unknown Artist" },
            album = candidate.album,
            duration = candidate.duration,
            filePath = candidate.filePath,
            albumId = candidate.albumId,
            artworkUri = artworkUri
        )
    }

    private fun buildDeepTrack(candidate: Candidate): MusicTrack? {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(candidate.filePath)

            val title = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_TITLE
            ).orEmpty().ifBlank { candidate.title }

            val artist = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ARTIST
            ).orEmpty().ifBlank { candidate.artist }

            val album = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ALBUM
            ).orEmpty().ifBlank { candidate.album }

            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: candidate.duration

            if (
                duration <
                ScanningSettings.getMinimumTrackDurationSeconds(context) * 1000L
            ) {
                return null
            }

            val normalizedArtist = ScanningSettings.normalizeMultiValue(
                context,
                artist
            )

            val artworkUri = if (
                ScanningSettings.isOptimizedImageSavingEnabled(context)
            ) {
                "content://media/external/audio/albumart/${candidate.albumId}"
            } else {
                saveEmbeddedArtwork(
                    filePath = candidate.filePath,
                    cacheKey = "track_${candidate.id}"
                )
            }

            MusicTrack(
                id = candidate.id,
                title = title,
                artist = normalizedArtist.ifBlank { "Unknown Artist" },
                album = album,
                duration = duration,
                filePath = candidate.filePath,
                albumId = candidate.albumId,
                artworkUri = artworkUri
            )
        } catch (_: Throwable) {
            // A single malformed/unreadable file must not abort the whole scan.
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun saveEmbeddedArtwork(
        filePath: String,
        cacheKey: String
    ): String? {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(filePath)
            val picture = retriever.embeddedPicture ?: return null

            val directory = File(
                context.filesDir,
                "scanned-artwork"
            )
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val output = File(directory, "$cacheKey.jpg")
            if (!output.isFile || output.length() != picture.size.toLong()) {
                FileOutputStream(output).use { stream ->
                    stream.write(picture)
                    stream.flush()
                }
            }

            output.absolutePath
        } catch (_: Throwable) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }
}

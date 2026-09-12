package com.laizycoder.musiflac.data

data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val filePath: String,
    val albumId: Long,
    val artworkUri: String?
)
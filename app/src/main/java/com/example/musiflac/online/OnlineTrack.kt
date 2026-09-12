package com.laizycoder.musiflac.online

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long?,
    val artworkUrl: String?,
    val isrc: String?,
    val source: String
)
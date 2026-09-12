package com.laizycoder.musiflac.online

sealed class OnlineSearchResult {

    data class Track(
        val track: OnlineTrack
    ) : OnlineSearchResult()

    data class Album(
        val id: String,
        val title: String,
        val artist: String?,
        val artworkUrl: String?,
        val source: String
    ) : OnlineSearchResult()

    data class Artist(
        val id: String,
        val name: String,
        val artworkUrl: String?,
        val source: String
    ) : OnlineSearchResult()

    data class Playlist(
        val id: String,
        val title: String,
        val owner: String?,
        val artworkUrl: String?,
        val source: String
    ) : OnlineSearchResult()
}
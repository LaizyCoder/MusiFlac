package com.laizycoder.musiflac.online

interface AudioProvider : Extension {

    suspend fun getAudioUrl(
        track: OnlineTrack
    ): String?

    suspend fun canHandle(
        track: OnlineTrack
    ): Boolean
}
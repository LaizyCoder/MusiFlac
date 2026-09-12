package com.laizycoder.musiflac.online

interface MetadataProvider : Extension {

    suspend fun search(
        query: String
    ): List<OnlineTrack>

    suspend fun getTrack(
        id: String
    ): OnlineTrack?
}
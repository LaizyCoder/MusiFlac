package com.laizycoder.musiflac.online

class SpotifyMetadataProvider : MetadataProvider {

    private var enabled = true

    override val id: String =
        "spotify"

    override val name: String =
        "Spotify"

    override val version: String =
        "1.0.0"

    override val description: String =
        "Spotify metadata and track identification"

    override val author: String =
        "MusiFlac"

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun setEnabled(
        enabled: Boolean
    ) {
        this.enabled = enabled
    }

    override suspend fun search(
        query: String
    ): List<OnlineTrack> {
        return emptyList()
    }

    override suspend fun getTrack(
        id: String
    ): OnlineTrack? {
        return null
    }
}
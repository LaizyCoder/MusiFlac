package com.laizycoder.musiflac.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ExtensionRepositoryEntry(
    val id: String,
    val name: String,
    val displayName: String,
    val version: String,
    val description: String,
    val downloadUrl: String,
    val sha256: String?,
    val iconUrl: String?,
    val category: String,
    val tags: List<String>,
    val minAppVersion: String?
)

class ExtensionRepository {

    companion object {
        const val DEFAULT_REPOSITORY_URL =
            "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json"
    }

    suspend fun fetch(
        repositoryUrl: String = DEFAULT_REPOSITORY_URL
    ): List<ExtensionRepositoryEntry> =
        withContext(Dispatchers.IO) {

            val connection =
                URL(repositoryUrl)
                    .openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                if (
                    connection.responseCode !in
                    200..299
                ) {
                    throw IllegalStateException(
                        "Repository returned HTTP ${connection.responseCode}"
                    )
                }

                val json =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                parseRegistry(json)
            } finally {
                connection.disconnect()
            }
        }

    private fun parseRegistry(
        json: String
    ): List<ExtensionRepositoryEntry> {

        val root =
            JSONObject(json)

        val extensions =
            root.getJSONArray("extensions")

        val result =
            mutableListOf<ExtensionRepositoryEntry>()

        for (index in 0 until extensions.length()) {

            val item =
                extensions.getJSONObject(index)

            val tags =
                mutableListOf<String>()

            val tagArray =
                item.optJSONArray("tags")

            if (tagArray != null) {
                for (
                tagIndex in
                0 until tagArray.length()
                ) {
                    tags.add(
                        tagArray.getString(tagIndex)
                    )
                }
            }

            result.add(
                ExtensionRepositoryEntry(
                    id =
                        item.getString("id"),

                    name =
                        item.getString("name"),

                    displayName =
                        item.optString(
                            "display_name",
                            item.getString("name")
                        ),

                    version =
                        item.getString("version"),

                    description =
                        item.optString(
                            "description",
                            ""
                        ),

                    downloadUrl =
                        item.getString(
                            "download_url"
                        ),

                    sha256 =
                        item.optString(
                            "sha256",
                            null
                        ),

                    iconUrl =
                        item.optString(
                            "icon_url",
                            null
                        ),

                    category =
                        item.optString(
                            "category",
                            "other"
                        ),

                    tags =
                        tags,

                    minAppVersion =
                        item.optString(
                            "min_app_version",
                            null
                        )
                )
            )
        }

        return result
    }
}
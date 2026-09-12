package com.laizycoder.musiflac.online

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

class ExtensionPackageLoader {

    fun load(
        file: File
    ): ExtensionPackage {

        if (!file.exists()) {
            throw IllegalArgumentException(
                "Extension package does not exist."
            )
        }

        if (!file.isFile) {
            throw IllegalArgumentException(
                "Extension package is not a file."
            )
        }

        if (
            !file.extension.equals(
                "sflx",
                ignoreCase = true
            )
        ) {
            throw IllegalArgumentException(
                "Unsupported extension package format."
            )
        }

        ZipFile(file).use { zip ->

            val manifestEntry =
                zip.getEntry(
                    "manifest.json"
                )
                    ?: throw IllegalStateException(
                        "Extension package is missing manifest.json."
                    )

            val scriptEntry =
                zip.getEntry(
                    "index.js"
                )
                    ?: throw IllegalStateException(
                        "Extension package is missing index.js."
                    )

            val manifestJson =
                zip.getInputStream(
                    manifestEntry
                )
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val script =
                zip.getInputStream(
                    scriptEntry
                )
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val manifest =
                JSONObject(
                    manifestJson
                )

            val id =
                manifest.optString(
                    "name"
                )

            if (id.isBlank()) {
                throw IllegalStateException(
                    "Extension manifest is missing name."
                )
            }

            val displayName =
                manifest.optString(
                    "displayName",
                    id
                )

            val version =
                manifest.optString(
                    "version",
                    "0.0.0"
                )

            val description =
                manifest.optString(
                    "description",
                    ""
                )

            val minAppVersion =
                manifest.optString(
                    "minAppVersion",
                    null
                )

            val types =
                parseTypes(
                    manifest.optJSONArray(
                        "type"
                    )
                )

            return ExtensionPackage(
                id = id,
                name = id,
                displayName = displayName,
                version = version,
                description = description,
                minAppVersion = minAppVersion,
                types = types,
                manifestJson = manifestJson,
                script = script
            )
        }
    }

    private fun parseTypes(
        array: JSONArray?
    ): List<String> {

        if (array == null) {
            return emptyList()
        }

        val types =
            mutableListOf<String>()

        for (
        index in
        0 until array.length()
        ) {
            val type =
                array.optString(index)

            if (type.isNotBlank()) {
                types.add(type)
            }
        }

        return types
    }
}
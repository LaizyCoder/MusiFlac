package com.laizycoder.musiflac.online

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ExtensionInstaller(
    context: Context
) {

    private val extensionDirectory =
        File(
            context.applicationContext.filesDir,
            "extensions"
        )

    suspend fun install(
        entry: ExtensionRepositoryEntry
    ): File = withContext(Dispatchers.IO) {

        extensionDirectory.mkdirs()

        val safeId =
            entry.id.replace(
                Regex("[^A-Za-z0-9._-]"),
                "_"
            )

        val targetFile =
            File(
                extensionDirectory,
                "$safeId-${entry.version}.sflx"
            )

        val temporaryFile =
            File(
                extensionDirectory,
                "$safeId-${entry.version}.sflx.part"
            )

        val connection =
            URL(entry.downloadUrl)
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "Accept",
                "application/octet-stream"
            )

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "Download failed: HTTP ${connection.responseCode}"
                )
            }

            connection.inputStream.use { input ->
                temporaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

        } finally {
            connection.disconnect()
        }

        if (!temporaryFile.exists()) {
            throw IllegalStateException(
                "Downloaded extension file is missing."
            )
        }

        try {

            if (!entry.sha256.isNullOrBlank()) {

                val actualHash =
                    sha256(temporaryFile)

                if (
                    !actualHash.equals(
                        entry.sha256,
                        ignoreCase = true
                    )
                ) {
                    throw SecurityException(
                        "SHA-256 verification failed."
                    )
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (!temporaryFile.renameTo(targetFile)) {
                throw IllegalStateException(
                    "Unable to finalize extension installation."
                )
            }

            targetFile

        } catch (error: Exception) {

            temporaryFile.delete()

            throw error
        }
    }

    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->

            val buffer =
                ByteArray(8192)

            while (true) {

                val count =
                    input.read(buffer)

                if (count <= 0) {
                    break
                }

                digest.update(
                    buffer,
                    0,
                    count
                )
            }
        }

        return digest
            .digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }
}
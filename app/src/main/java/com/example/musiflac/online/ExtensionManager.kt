package com.laizycoder.musiflac.online

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import org.json.JSONObject

class ExtensionManager(
    context: Context
) {

    private val applicationContext =
        context.applicationContext

    private val preferences =
        applicationContext.getSharedPreferences(
            "extension_preferences",
            Context.MODE_PRIVATE
        )

    private val extensionDirectory =
        File(
            applicationContext.filesDir,
            "extensions"
        )

    private val extensions =
        mutableStateListOf<Extension>()

    private val packageLoader =
        ExtensionPackageLoader()

    data class DownloadQuality(
        val id: String,
        val label: String,
        val description: String?,
        val kind: String? = null
    )

    init {
        loadInstalledExtensions()
    }

    fun register(extension: Extension) {
        if (extensions.none { it.id == extension.id }) {
            val savedState = preferences.getBoolean(
                "enabled_${extension.id}",
                extension.isEnabled()
            )
            extension.setEnabled(savedState)
            extensions.add(extension)
        }
    }

    fun unregister(extensionId: String) {
        extensions.removeAll { it.id == extensionId }
    }

    fun getAll(): List<Extension> = extensions.toList()

    /**
     * Re-reads installed .sflx packages without recreating the manager.
     * Built-in extensions remain registered; only package-backed entries
     * currently present on disk are refreshed.
     */
    fun refreshInstalledExtensions() {
        val installedPackages = loadInstalledPackages()
        val installedIds = installedPackages
            .map { it.id }
            .toSet()

        // Replace package-backed entries in-place. Because `extensions` is a
        // SnapshotStateList, every Compose screen that reads getAll()/getEnabled()
        // observes these mutations and recomposes immediately.
        extensions.removeAll { it.id in installedIds }

        installedPackages.forEach { extensionPackage ->
            try {
                val extension = InstalledExtension(
                    extensionPackage = extensionPackage,
                    types = parseTypes(extensionPackage.types)
                )
                register(extension)
            } catch (_: Throwable) {
                // Keep the existing behavior: one malformed package must not
                // prevent other installed extensions from being refreshed.
            }
        }
    }

    fun getEnabled(): List<Extension> = extensions.filter { it.isEnabled() }

    fun get(extensionId: String): Extension? =
        extensions.firstOrNull { it.id == extensionId }

    fun setEnabled(extensionId: String, enabled: Boolean) {
        val extension = get(extensionId) ?: return
        extension.setEnabled(enabled)
        preferences.edit()
            .putBoolean("enabled_$extensionId", enabled)
            .apply()
    }

    fun getPriority(
        key: String,
        defaultIds: List<String>
    ): List<String> {
        val saved = preferences.getString("priority_$key", null)
        if (saved.isNullOrBlank()) return defaultIds

        val savedIds = saved.split("|").filter { it.isNotBlank() }
        val knownIds = defaultIds.toSet()

        return savedIds
            .filter { it in knownIds }
            .plus(defaultIds.filter { it !in savedIds })
    }

    fun setPriority(key: String, ids: List<String>) {
        preferences.edit()
            .putString("priority_$key", ids.joinToString("|"))
            .apply()
    }

    fun getFallbackProviders(defaultIds: List<String>): List<String> {
        val saved = preferences.getString("fallback_download", null)
        if (saved == null) return defaultIds

        val knownIds = defaultIds.toSet()
        return saved.split("|").filter {
            it.isNotBlank() && it in knownIds
        }
    }

    fun setFallbackProviders(ids: List<String>) {
        preferences.edit()
            .putString("fallback_download", ids.joinToString("|"))
            .apply()
    }

    /**
     * Returns the quality choices declared by a download extension's
     * manifest.json. The extension remains the source of truth for its
     * supported qualities.
     */
    fun getDownloadQualities(extensionId: String): List<DownloadQuality> {
        val extension = get(extensionId) as? InstalledExtension ?: return emptyList()

        return try {
            val manifest = JSONObject(extension.extensionPackage.manifestJson)
            val qualityOptions = manifest.optJSONArray("qualityOptions")
                ?: return emptyList()

            buildList {
                for (index in 0 until qualityOptions.length()) {
                    val item = qualityOptions.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val label = item.optString("label").trim()

                    if (id.isBlank() || label.isBlank()) continue

                    add(
                        DownloadQuality(
                            id = id,
                            label = label,
                            description = item.optString("description")
                                .takeIf { it.isNotBlank() },
                            kind = item.optString("kind")
                                .takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun loadInstalledPackages(): List<ExtensionPackage> {
        if (!extensionDirectory.exists()) return emptyList()

        val files = extensionDirectory
            .listFiles()
            ?.filter {
                it.isFile && it.extension.equals("sflx", ignoreCase = true)
            }
            ?: return emptyList()

        val packages = mutableListOf<ExtensionPackage>()
        files.forEach { file ->
            try {
                packages.add(packageLoader.load(file))
            } catch (_: Exception) {
                // Ignore invalid packages.
            }
        }
        return packages
    }

    fun getInstalledPackage(extensionId: String): ExtensionPackage? =
        loadInstalledPackages().firstOrNull { it.id == extensionId }

    fun getInstalledExtensionPackage(extensionId: String): ExtensionPackage? {
        val extension = get(extensionId)
        if (extension !is InstalledExtension) return null
        return extension.extensionPackage
    }

    fun clear() {
        extensions.clear()
    }

    private fun loadInstalledExtensions() {
        if (!extensionDirectory.exists()) return

        val files = extensionDirectory.listFiles() ?: return
        files.filter {
            it.isFile && it.extension.equals("sflx", ignoreCase = true)
        }.forEach { file ->
            val extension = createInstalledExtension(file)
            if (extension != null) register(extension)
        }
    }

    private fun createInstalledExtension(file: File): Extension? =
        try {
            val extensionPackage = packageLoader.load(file)
            val types = parseTypes(extensionPackage.types)
            InstalledExtension(
                extensionPackage = extensionPackage,
                types = types
            )
        } catch (_: Exception) {
            null
        }

    private fun parseTypes(typeList: List<String>): Set<ExtensionType> {
        if (typeList.isEmpty()) return emptySet()

        val types = mutableSetOf<ExtensionType>()
        typeList.forEach { type ->
            when (type) {
                "metadata_provider" -> types.add(ExtensionType.METADATA)
                "download_provider" -> types.add(ExtensionType.DOWNLOAD)
                "lyrics_provider" -> types.add(ExtensionType.LYRICS)
            }
        }
        return types
    }

    private class InstalledExtension(
        val extensionPackage: ExtensionPackage,
        override val types: Set<ExtensionType>
    ) : Extension {
        override val id: String = extensionPackage.id
        override val name: String = extensionPackage.displayName
        override val version: String = extensionPackage.version
        override val description: String = extensionPackage.description
        override val author: String = "Extension"

        private var enabled = true

        override fun isEnabled(): Boolean = enabled

        override fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }
}

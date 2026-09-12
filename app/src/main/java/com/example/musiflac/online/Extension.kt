package com.laizycoder.musiflac.online

enum class ExtensionType {
    METADATA,
    DOWNLOAD,
    LYRICS
}

interface Extension {

    val id: String
    val name: String
    val version: String
    val description: String
    val author: String

    val types: Set<ExtensionType>
        get() = emptySet()

    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}
package com.laizycoder.musiflac.online

data class ExtensionPackage(
    val id: String,
    val name: String,
    val displayName: String,
    val version: String,
    val description: String,
    val minAppVersion: String?,
    val types: List<String>,
    val manifestJson: String,
    val script: String
)
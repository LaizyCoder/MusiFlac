package com.laizycoder.musiflac.online

import gobackend.Gobackend

object GoBackend {

    fun version(): String {
        return Gobackend.version()
    }

    fun evaluateJavaScript(
        script: String
    ): String {
        return Gobackend.evaluateJavaScript(
            script
        )
    }

    fun loadExtension(
        packagePath: String
    ): String {
        return Gobackend.loadExtension(
            packagePath
        )
    }

    fun loadExtensionsFromDirectory(
        directoryPath: String
    ): String {
        return Gobackend.loadExtensionsFromDirectory(
            directoryPath
        )
    }

    fun hasExtensionMethod(
        extensionId: String,
        method: String
    ): Boolean {
        return Gobackend.getExtensionMethod(
            extensionId,
            method
        )
    }

    fun getExtensionMethods(
        extensionId: String
    ): String {
        return Gobackend.getExtensionMethods(
            extensionId
        )
    }

    fun callExtensionMethod(
        extensionId: String,
        method: String,
        argumentsJson: String = "[]"
    ): String {
        return Gobackend.callExtensionMethod(
            extensionId,
            method,
            argumentsJson
        )
    }
}
package com.laizycoder.musiflac.online

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import gobackend.Gobackend
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object GoBackend {

    fun version(): String {
        return Gobackend.version()
    }

    fun evaluateJavaScript(
        script: String
    ): String {
        return Gobackend.evaluateJavaScript(script)
    }

    fun loadExtension(
        packagePath: String
    ): String {
        return Gobackend.loadExtension(packagePath)
    }

    fun loadExtensionsFromDirectory(
        directoryPath: String
    ): String {
        return Gobackend.loadExtensionsFromDirectory(directoryPath)
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

    fun getExtensionPendingAuth(
        extensionId: String
    ): String {
        return Gobackend.getExtensionPendingAuthJSON(
            extensionId
        )
    }

    fun setExtensionSessionGrant(
        extensionId: String,
        grant: String
    ) {
        Gobackend.setExtensionSessionGrantByID(
            extensionId,
            grant
        )
    }

    fun clearExtensionPendingAuth(
        extensionId: String
    ) {
        Gobackend.clearExtensionPendingAuthByID(
            extensionId
        )
    }

    fun isExtensionAuthenticated(
        extensionId: String
    ): Boolean {
        return Gobackend.isExtensionAuthenticatedByID(
            extensionId
        )
    }
}

object ExtensionVerificationCoordinator {

    private const val TAG = "ExtensionVerification"
    private const val TIMEOUT_SECONDS = 180L

    private data class PendingVerification(
        val extensionId: String,
        val expectedState: String?,
        val latch: CountDownLatch
    )

    private val pending =
        ConcurrentHashMap<String, PendingVerification>()

    fun awaitVerification(
        context: Context,
        extensionId: String
    ): Boolean {

        val existing = pending[extensionId]

        if (existing != null) {
            return existing.latch.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        }

        val authResponse = try {
            GoBackend.getExtensionPendingAuth(
                extensionId
            )
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "Failed to get pending auth for $extensionId",
                error
            )
            return false
        }

        if (authResponse.isBlank()) {
            Log.w(
                TAG,
                "No pending auth request for $extensionId"
            )
            return false
        }

        val authJson = try {
            JSONObject(authResponse)
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "Invalid pending auth response for $extensionId",
                error
            )
            return false
        }

        val authUrl =
            authJson
                .optString("auth_url")
                .trim()

        val callbackUrl =
            authJson
                .optString("callback_url")
                .trim()

        val expectedState =
            extractState(callbackUrl)

        if (authUrl.isBlank()) {
            Log.w(
                TAG,
                "No verification URL for $extensionId"
            )
            return false
        }

        val verification =
            PendingVerification(
                extensionId = extensionId,
                expectedState = expectedState,
                latch = CountDownLatch(1)
            )

        pending[extensionId] = verification

        try {

            val intent =
                Intent(
                    context,
                    ExtensionVerificationActivity::class.java
                ).apply {
                    putExtra(
                        ExtensionVerificationActivity.EXTRA_EXTENSION_ID,
                        extensionId
                    )

                    putExtra(
                        ExtensionVerificationActivity.EXTRA_AUTH_URL,
                        authUrl
                    )

                    putExtra(
                        ExtensionVerificationActivity.EXTRA_EXPECTED_STATE,
                        expectedState
                    )

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            context.applicationContext.startActivity(
                intent
            )

            Log.d(
                TAG,
                "Started in-app verification for $extensionId"
            )

        } catch (error: Throwable) {

            pending.remove(
                extensionId,
                verification
            )

            Log.e(
                TAG,
                "Could not start in-app verification",
                error
            )

            return false
        }

        return try {

            verification.latch.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )

        } finally {

            pending.remove(
                extensionId,
                verification
            )
        }
    }

    fun handleCallback(
        uri: Uri?
    ): Boolean {

        if (uri == null) {
            return false
        }

        val callback =
            parseGrantCallback(
                uri,
                0
            ) ?: return false

        val grant = callback.first
        val state = callback.second

        val verification =
            pending.values.firstOrNull { item ->

                item.expectedState.isNullOrBlank() ||
                        item.expectedState == state
            }

        if (verification == null) {
            Log.w(
                TAG,
                "No matching verification request"
            )
            return false
        }

        return try {

            GoBackend.setExtensionSessionGrant(
                verification.extensionId,
                grant
            )

            /*
             * setExtensionSessionGrant() only places the grant into the
             * Go runtime's pending-grant store. The signed-session runtime
             * must then consume that grant and exchange it for the actual
             * authenticated session.
             *
             * The official extension runtime exposes this host action as
             * "completeGrant". It consumes the pending grant, performs the
             * session exchange, and persists the resulting session.
             */
            val completionResponse =
                GoBackend.callExtensionMethod(
                    verification.extensionId,
                    "completeGrant",
                    "[]"
                )

            val completionJson = try {
                JSONObject(completionResponse)
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "Invalid signed-session completion response",
                    error
                )
            }

            val completed =
                completionJson.optBoolean(
                    "success",
                    false
                )

            if (!completed) {
                val completionError =
                    completionJson
                        .optString("error")
                        .trim()
                        .ifBlank {
                            "Signed-session grant completion failed."
                        }

                throw IllegalStateException(
                    completionError
                )
            }

            /*
             * Do not unblock the waiting download until Go confirms that
             * the extension now has a real authenticated signed session.
             */
            if (!GoBackend.isExtensionAuthenticated(
                    verification.extensionId
                )
            ) {
                throw IllegalStateException(
                    "Signed-session grant completed but the extension is not authenticated."
                )
            }

            verification.latch.countDown()

            Log.d(
                TAG,
                "Signed-session grant completed and authenticated for " +
                        verification.extensionId
            )

            true

        } catch (error: Throwable) {

            Log.e(
                TAG,
                "Failed to pass grant to Go",
                error
            )

            false
        }
    }

    private fun parseGrantCallback(
        uri: Uri,
        depth: Int
    ): Pair<String, String?>? {

        if (depth > 4) {
            return null
        }

        val grant =
            firstNonBlank(
                uri.getQueryParameter("grant"),
                uri.getQueryParameter("code")
            )

        val state =
            uri.getQueryParameter("state")

        if (!grant.isNullOrBlank()) {
            return grant to state
        }

        val nested =
            firstNonBlank(
                uri.getQueryParameter("cb"),
                uri.getQueryParameter("callback"),
                uri.getQueryParameter("callback_url"),
                uri.getQueryParameter("redirect_uri")
            )

        if (nested.isNullOrBlank()) {
            return null
        }

        return try {
            parseGrantCallback(
                Uri.parse(nested),
                depth + 1
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractState(
        value: String
    ): String? {

        if (value.isBlank()) {
            return null
        }

        return try {

            val uri =
                Uri.parse(value)

            val state =
                uri.getQueryParameter("state")

            if (!state.isNullOrBlank()) {
                return state
            }

            val nested =
                firstNonBlank(
                    uri.getQueryParameter("cb"),
                    uri.getQueryParameter("callback"),
                    uri.getQueryParameter("callback_url"),
                    uri.getQueryParameter("redirect_uri")
                )

            if (nested.isNullOrBlank()) {
                null
            } else {
                extractState(nested)
            }

        } catch (_: Throwable) {
            null
        }
    }

    private fun firstNonBlank(
        vararg values: String?
    ): String? {
        return values
            .firstOrNull {
                !it.isNullOrBlank()
            }
            ?.trim()
    }
}
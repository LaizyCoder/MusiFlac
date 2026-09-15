package com.laizycoder.musiflac.online

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class ExtensionVerificationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EXTENSION_ID = "extension_id"
        const val EXTRA_AUTH_URL = "auth_url"
        const val EXTRA_EXPECTED_STATE = "expected_state"
    }

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val authUrl = intent
            ?.getStringExtra(EXTRA_AUTH_URL)
            ?.trim()

        if (authUrl.isNullOrBlank()) {
            finish()
            return
        }

        val view = WebView(this)
        webView = view

        configureWebView(view)

        setContentView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        setupBackHandling()

        view.loadUrl(authUrl)
    }

    private fun configureWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true

            allowFileAccess = false
            allowContentAccess = true
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(true)

        CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(
                view,
                true
            )

        view.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return handleNavigation(request.url)
            }

            @Deprecated("Deprecated in API 24")
            override fun shouldOverrideUrlLoading(
                view: WebView,
                url: String
            ): Boolean {
                return handleNavigation(Uri.parse(url))
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?
            ) {
                super.onPageStarted(
                    view,
                    url,
                    favicon
                )

                handleNavigation(
                    Uri.parse(url)
                )
            }
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    val view = webView

                    if (view != null && view.canGoBack()) {
                        view.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    private fun handleNavigation(
        uri: Uri
    ): Boolean {

        if (
            uri.scheme.equals(
                "spotiflac",
                ignoreCase = true
            ) &&
            uri.host.equals(
                "session-grant",
                ignoreCase = true
            )
        ) {
            ExtensionVerificationCoordinator
                .handleCallback(uri)

            finish()

            return true
        }

        return false
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()

            (parent as? ViewGroup)
                ?.removeView(this)

            destroy()
        }

        webView = null

        super.onDestroy()
    }
}
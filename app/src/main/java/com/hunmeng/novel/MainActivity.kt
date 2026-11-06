package com.hunmeng.novel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Hunmeng Novel — приложение-обёртка над сайтом hunmeng-novel.ru.
 * Сайт открывается внутри приложения; ссылки на другие сайты и приложения
 * (Telegram и т. п.) открываются снаружи.
 */
class MainActivity : Activity() {

    companion object {
        const val SITE_HOST = "hunmeng-novel.ru"
        const val HOME_URL = "https://$SITE_HOST/"
        private const val COLOR_NAVY = 0xFF1C3455.toInt()
        private const val COLOR_CREAM = 0xFFF6F5F0.toInt()
        private const val COLOR_GREEN = 0xFF2F6B4F.toInt()
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorView: View
    private var mainFrameFailed = false
    private lateinit var updates: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        setupWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        }
        if (webView.url == null) {
            webView.loadUrl(siteUrlFrom(intent) ?: HOME_URL)
        }

        updates = UpdateManager(this)
        if (savedInstanceState == null) updates.check()

        // Уведомления о новых главах
        ChapterChecker.createChannel(this)
        ChapterChecker.schedule(this)
        ChapterChecker.baselineIfNeeded(this)
        askNotificationPermissionOnce()
    }

    /** Android 13+: один раз спрашиваем разрешение на уведомления. */
    private fun askNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val permission = "android.permission.POST_NOTIFICATIONS"
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("notif_asked", false)) return
        prefs.edit().putBoolean("notif_asked", true).apply()
        requestPermissions(arrayOf(permission), 42)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        siteUrlFrom(intent)?.let { webView.loadUrl(it) }
    }

    /** Ссылка на наш сайт, с которой открыли приложение (например, из Telegram-канала). */
    private fun siteUrlFrom(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        return if (isSiteUri(uri)) uri.toString() else null
    }

    private fun isSiteUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return false
        return (scheme == "https" || scheme == "http") &&
            (host == SITE_HOST || host == "www.$SITE_HOST")
    }

    // ---------- Разметка (без XML, чтобы проект был проще) ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_CREAM)
            fitsSystemWindows = true
        }

        webView = WebView(this).apply { setBackgroundColor(COLOR_CREAM) }
        root.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(COLOR_GREEN)
            visibility = View.GONE
        }
        root.addView(progress, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP))

        errorView = buildErrorView()
        root.addView(errorView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        return root
    }

    private fun buildErrorView(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(COLOR_CREAM)
            setPadding(dp(32), dp(32), dp(32), dp(32))
            visibility = View.GONE
            isClickable = true // не пропускать нажатия к сайту под заглушкой
        }

        box.addView(ImageView(this).apply {
            setImageResource(R.drawable.emblem)
        }, LinearLayout.LayoutParams(dp(120), dp(120)))

        box.addView(TextView(this).apply {
            setText(R.string.offline_title)
            textSize = 20f
            setTextColor(COLOR_NAVY)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(8))
        })

        box.addView(TextView(this).apply {
            setText(R.string.offline_text)
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        })

        box.addView(Button(this).apply {
            setText(R.string.retry)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(COLOR_GREEN)
            setOnClickListener { retry() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        return box
    }

    // ---------- WebView ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // вход в аккаунт (Supabase) хранится здесь
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            userAgentString = "$userAgentString HunmengNovelApp/${BuildConfig.VERSION_NAME}"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (isSiteUri(request.url)) return false
                openExternally(request.url)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                mainFrameFailed = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!mainFrameFailed) errorView.visibility = View.GONE
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    mainFrameFailed = true
                    errorView.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openExternally(uri: Uri) {
        try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Для intent:// ссылок пробуем запасной адрес из самой ссылки
            val fallback = try {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    .getStringExtra("browser_fallback_url")
            } catch (ignored: Exception) { null }
            if (fallback != null) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback))); return }
                catch (ignored: Exception) { }
            }
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun retry() {
        errorView.visibility = View.GONE
        if (webView.url.isNullOrEmpty()) webView.loadUrl(HOME_URL) else webView.reload()
    }

    // ---------- Кнопка «назад» и жизненный цикл ----------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            errorView.visibility = View.GONE
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        updates.onResume()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

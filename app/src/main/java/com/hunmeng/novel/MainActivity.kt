package com.hunmeng.novel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf

/**
 * Native Android entry point: the user interface is Jetpack Compose.
 * No WebView is created or used anywhere in the activity.
 */
class MainActivity : ComponentActivity() {
    private lateinit var updates: UpdateManager
    private val incomingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        incomingLink.value = safeSiteLink(intent)
        setContent { HunmengNativeApp(incomingLink.value) }

        updates = UpdateManager(this)
        safely { if (savedInstanceState == null) updates.check() }
        safely { ChapterChecker.createChannel(this) }
        safely { ChapterChecker.schedule(this) }
        safely { ChapterChecker.baselineIfNeeded(this) }
        safely { askNotificationPermissionOnce() }
        if (savedInstanceState == null) safely { CrashReporter.showLastCrash(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingLink.value = safeSiteLink(intent)
    }

    private fun safeSiteLink(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "https") return null
        if (uri.host != "hunmeng-novel.ru" && uri.host != "www.hunmeng-novel.ru") return null
        return uri.toString()
    }

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (e: Exception) { CrashReporter.save(this, e) }
    }

    private fun askNotificationPermissionOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val permission = "android.permission.POST_NOTIFICATIONS"
        if (checkSelfPermission(permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("notif_asked", false)) return
        prefs.edit().putBoolean("notif_asked", true).apply()
        requestPermissions(arrayOf(permission), 42)
    }

    override fun onResume() {
        super.onResume()
        if (::updates.isInitialized) safely { updates.onResume() }
    }
}

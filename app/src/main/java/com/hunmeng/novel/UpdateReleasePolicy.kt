package com.hunmeng.novel

import java.net.URI

/**
 * Pure checks for official Android releases. No Android framework dependencies,
 * so comparison and URL safety can be unit-tested without a device.
 */
internal object UpdateReleasePolicy {
    private val versionPattern = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)$""")
    private val filePattern = Regex("""^[A-Za-z0-9._-]+\.apk$""", RegexOption.IGNORE_CASE)

    private fun parts(version: String): List<Int>? {
        val match = versionPattern.matchEntire(version.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    }

    fun isNewer(remote: String, installed: String): Boolean {
        val r = parts(remote) ?: return false
        val l = parts(installed) ?: return false
        for (i in 0..2) {
            val comparison = r[i].compareTo(l[i])
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    /** The first foreground entry checks immediately, then subsequent checks are throttled. */
    fun shouldCheck(lastCheckAt: Long, now: Long, intervalMs: Long): Boolean =
        lastCheckAt < 0 || (now >= lastCheckAt && now - lastCheckAt >= intervalMs)

    /** Allow APK downloads only from this app's GitHub Releases repository. */
    fun trustedApk(url: String): Boolean = try {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host != "github.com" ||
            uri.userInfo != null || uri.port != -1 ||
            uri.rawQuery != null || uri.rawFragment != null
        ) {
            false
        } else {
            val p = uri.rawPath.split('/')
            p.size == 7 && p[0].isEmpty() &&
                p[1] == "DEXTERITY-maker" &&
                p[2] == "hunmeng-novel-app" &&
                p[3] == "releases" && p[4] == "download" &&
                parts(p[5]) != null && filePattern.matches(p[6])
        }
    } catch (_: Exception) {
        false
    }
}

package com.krejci.halo.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A release discovered on GitHub that is newer than the installed build. */
data class AppRelease(
    val versionName: String,
    val tag: String,
    val apkUrl: String,
    val notes: String,
)

/**
 * Minimal in-app updater backed by GitHub Releases.
 *
 * Queries the repo's `releases/latest`, compares the release tag against the installed
 * `versionName`, and — when newer — downloads the release APK and hands it to the system
 * package installer. No third-party dependencies: [HttpURLConnection] + `org.json`.
 *
 * In-place installs require the downloaded APK to be signed with the same key as the
 * installed build, so the first release APK must be installed manually; thereafter this
 * upgrades in place. Needs the `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions and a
 * FileProvider whose paths expose the cache `updates/` directory.
 */
class AppUpdater(
    private val context: Context,
    private val owner: String,
    private val repo: String,
    private val fileProviderAuthority: String = "${context.packageName}.fileprovider",
) {
    /** The versionName baked into the installed package (e.g. "2.0"). */
    val currentVersion: String by lazy {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        }.getOrDefault("0")
    }

    /** Returns the latest release when it is newer than [currentVersion], otherwise null. */
    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val json = httpGet("https://api.github.com/repos/$owner/$repo/releases/latest")
        val release = parseRelease(JSONObject(json)) ?: return@withContext null
        if (isNewer(release.versionName, currentVersion)) release else null
    }

    /** Downloads [release]'s APK into the cache and returns the file. */
    suspend fun download(release: AppRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // drop any stale download
        val out = File(dir, "$repo-${release.tag}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "$repo-updater")
        }
        try {
            conn.inputStream.use { input -> out.outputStream().use(input::copyTo) }
        } finally {
            conn.disconnect()
        }
        out
    }

    /** Launches the system package installer for a downloaded [apk]. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun parseRelease(obj: JSONObject): AppRelease? {
        val tag = obj.optString("tag_name").ifBlank { return null }
        val assets = obj.optJSONArray("assets") ?: JSONArray()
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        if (apkUrl.isBlank()) return null
        return AppRelease(
            versionName = tag.removePrefix("v"),
            tag = tag,
            apkUrl = apkUrl,
            notes = obj.optString("body"),
        )
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "$repo-updater")
        }
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** True when [candidate] is a strictly higher dotted-numeric version than [current]. */
        fun isNewer(candidate: String, current: String): Boolean {
            val c = parse(candidate)
            val cur = parse(current)
            for (i in 0 until maxOf(c.size, cur.size)) {
                val a = c.getOrElse(i) { 0 }
                val b = cur.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }

        private fun parse(v: String): List<Int> =
            v.trim().removePrefix("v").split('.', '-')
                .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
    }
}

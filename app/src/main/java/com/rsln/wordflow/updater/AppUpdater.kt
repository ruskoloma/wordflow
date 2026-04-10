package com.rsln.wordflow.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val body: String,
    val apkUrl: String?,
    val publishedAt: String
)

class AppUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val GITHUB_OWNER = "ruskoloma"
        private const val GITHUB_REPO = "wordflow"
        private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    /**
     * Fetch latest release from GitHub. Works on private repos only with a token,
     * but on public repos no auth needed. For private: pass a token or make the repo public.
     */
    suspend fun checkForUpdate(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.code == 404) {
                return@withContext Result.success(null) // no releases yet
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API error: ${response.code}"))
            }

            val json = JsonParser.parseString(body).asJsonObject
            val tagName = json.get("tag_name")?.asString ?: ""
            val releaseBody = json.get("body")?.asString ?: ""
            val publishedAt = json.get("published_at")?.asString ?: ""

            // Find APK asset
            val assets = json.getAsJsonArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (asset in assets) {
                    val assetObj = asset.asJsonObject
                    val name = assetObj.get("name")?.asString ?: ""
                    if (name.endsWith(".apk")) {
                        apkUrl = assetObj.get("browser_download_url")?.asString
                        break
                    }
                }
            }

            val versionName = tagName.removePrefix("v")

            Result.success(ReleaseInfo(
                tagName = tagName,
                versionName = versionName,
                body = releaseBody,
                apkUrl = apkUrl,
                publishedAt = publishedAt
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    fun getCurrentVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    /**
     * Download APK using DownloadManager and trigger install when complete.
     */
    fun downloadAndInstall(apkUrl: String, versionName: String) {
        val fileName = "WordFlow-$versionName.apk"

        // Clean up old APKs
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("WordFlow Update $versionName")
            .setDescription("Downloading update...")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadId = downloadManager.enqueue(request)

        // Register receiver to install when download completes
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    val file = File(downloadsDir, fileName)
                    if (file.exists()) {
                        installApk(ctx, file)
                    }
                }
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_EXPORTED
        )
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}

package com.valdigley.claudevs.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String
)

class AppUpdateChecker(private val context: Context) {

    companion object {
        // GitHub repository info
        private const val GITHUB_OWNER = "valdigley"
        private const val GITHUB_REPO = "ClaudeVS"
        private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    /**
     * Check for updates from GitHub Releases
     * Returns AppUpdate if a newer version is available, null otherwise
     */
    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                CrashLogger.log("AppUpdateChecker", "GitHub API returned ${connection.responseCode}")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = org.json.JSONObject(response)
            val tagName = json.getString("tag_name") // e.g., "v1.2.0"
            val releaseNotes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            val assets = json.getJSONArray("assets")

            // Find APK asset
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl == null) {
                CrashLogger.log("AppUpdateChecker", "No APK found in release")
                return@withContext null
            }

            // Parse version from tag (e.g., "v1.2.0" -> 1.2.0)
            val versionName = tagName.removePrefix("v")
            val versionCode = parseVersionCode(versionName)

            // Get current version
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            CrashLogger.log("AppUpdateChecker", "Current: $currentVersionCode, Latest: $versionCode")

            // Check if update is available
            if (versionCode > currentVersionCode) {
                return@withContext AppUpdate(
                    versionName = versionName,
                    versionCode = versionCode,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    publishedAt = publishedAt
                )
            }

            null
        } catch (e: Exception) {
            CrashLogger.log("AppUpdateChecker", "Error checking for updates: ${e.message}")
            null
        }
    }

    /**
     * Download and install APK update
     */
    fun downloadAndInstall(update: AppUpdate, onProgress: (Int) -> Unit, onComplete: () -> Unit, onError: (String) -> Unit) {
        try {
            val fileName = "ClaudeVS-${update.versionName}.apk"
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Delete old APK files
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }

            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("ClaudeVS ${update.versionName}")
                .setDescription("Baixando atualização...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)

            // Register receiver to handle download completion
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        context.unregisterReceiver(this)

                        // Check download status
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = cursor.getInt(statusIndex)

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                onComplete()
                                // Install APK
                                val file = File(downloadDir, fileName)
                                installApk(file)
                            } else {
                                onError("Download falhou")
                            }
                        }
                        cursor.close()
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }

        } catch (e: Exception) {
            CrashLogger.log("AppUpdateChecker", "Download error: ${e.message}")
            onError(e.message ?: "Erro desconhecido")
        }
    }

    /**
     * Install APK file
     */
    private fun installApk(file: File) {
        try {
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
        } catch (e: Exception) {
            CrashLogger.log("AppUpdateChecker", "Install error: ${e.message}")
        }
    }

    /**
     * Parse version string to version code
     * e.g., "1.2.3" -> 10203
     */
    private fun parseVersionCode(version: String): Int {
        return try {
            val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
            val major = parts.getOrElse(0) { 0 }
            val minor = parts.getOrElse(1) { 0 }
            val patch = parts.getOrElse(2) { 0 }
            major * 10000 + minor * 100 + patch
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get current app version name
     */
    fun getCurrentVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}

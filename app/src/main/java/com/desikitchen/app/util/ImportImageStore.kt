package com.desikitchen.app.util

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional copy of an imported cover photo into app storage.
 *
 * Phase 1 (Meta-safe, Sai lock #11): persisting creator thumbnails is OFF by default — [persist] returns the remote
 * URL unchanged (hotlink; the UI falls back to a placeholder once it expires). Flip [persistEnabled] only after a
 * policy review. Not initialised in JVM unit tests → the remote URL is kept as-is either way.
 */
object ImportImageStore {
    @Volatile private var dir: File? = null

    /** Default false: never download/store third-party (e.g. Instagram CDN) images. */
    @Volatile var persistEnabled: Boolean = false

    fun init(context: Context) { dir = File(context.filesDir, "import-images").apply { mkdirs() } }

    fun persist(remoteUrl: String?, key: String): String? {
        if (!persistEnabled) return remoteUrl
        val target = dir ?: return remoteUrl
        if (remoteUrl.isNullOrBlank() || !remoteUrl.startsWith("https://")) return remoteUrl
        return runCatching {
            val file = File(target, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg")
            val conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("User-Agent", WebRecipeFetcher.USER_AGENT)
            }
            try {
                if (conn.responseCode !in 200..299) return remoteUrl
                conn.inputStream.use { input -> file.outputStream().use { out -> input.copyTo(out) } }
            } finally { conn.disconnect() }
            if (file.length() > 1024) file.toURI().toString() else remoteUrl
        }.getOrDefault(remoteUrl)
    }
}

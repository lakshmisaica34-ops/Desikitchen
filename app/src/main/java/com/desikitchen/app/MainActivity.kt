package com.desikitchen.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.desikitchen.app.ui.navigation.DesiKitchenRoot
import com.desikitchen.app.ui.theme.DesiKitchenTheme
import com.desikitchen.app.util.SharedPayload

class MainActivity : ComponentActivity() {
    private var shared by mutableStateOf<SharedPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) shared = runCatching { intent.extractShared() }.getOrNull()
        setContent {
            DesiKitchenTheme(darkTheme = false) { // force-light product lock
                DesiKitchenRoot(shared = shared, onSharedConsumed = { shared = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runCatching { intent.extractShared() }.getOrNull()?.let { shared = it }
    }

    private fun Intent?.extractShared(): SharedPayload? {
        val intent = this ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val streams = intent.streams()
                val (images, others) = streams.partition { mimeOf(it, intent.type).startsWith("image/") }
                SharedPayload.from(
                    subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                    text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                    files = others.mapNotNull { uri -> readText(uri)?.let { displayName(uri, intent.type) to it } },
                    imageUris = images.map { it.toString() },
                )
            }
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (uri.scheme == "content" || uri.scheme == "file") {
                    if (mimeOf(uri, intent.type).startsWith("image/")) SharedPayload.Images(listOf(uri.toString()))
                    else readText(uri)?.let { SharedPayload.from(null, null, listOf(displayName(uri, intent.type) to it)) }
                } else SharedPayload.Text(uri.toString())
            }
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streams(): List<Uri> {
        val list = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND_MULTIPLE) {
            val extra = if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            extra?.let { list += it }
        } else {
            val one = if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            one?.let { list += it }
        }
        clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.forEach { if (it !in list) list += it } }
        return list
    }

    private fun mimeOf(uri: Uri, fallback: String?): String =
        (runCatching { contentResolver.getType(uri) }.getOrNull() ?: fallback ?: "").lowercase()

    private fun displayName(uri: Uri, fallback: String?): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: fallback ?: ""

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(SharedPayload.MAX_TEXT) }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

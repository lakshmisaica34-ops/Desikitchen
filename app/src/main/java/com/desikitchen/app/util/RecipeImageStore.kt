package com.desikitchen.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Copies picked / captured photos into app storage (downscaled JPEG) so recipe images survive the picker grant. */
object RecipeImageStore {
    private const val MAX_EDGE = 1600

    /** A fresh FileProvider uri for ActivityResultContracts.TakePicture. */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    /** Returns a file:// uri string in filesDir/recipe-images, or null when the image can't be read. */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotated = rotateForExif(context, uri, bmp)
        val dir = File(context.filesDir, "recipe-images").apply { mkdirs() }
        val file = File(dir, "img-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        file.toURI().toString()
    }.getOrNull()

    private fun rotateForExif(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        if (degrees == 0) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }
}

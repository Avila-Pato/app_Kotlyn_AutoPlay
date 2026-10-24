package org.example.autoplay.mycar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pictures of the papers (a photo of the license, of the registration certificate…), kept in the app's own
 * storage as small JPEGs. Only their names go into the car's data; the pictures stay on the phone.
 */
@Singleton
class DocumentImages @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir = File(context.filesDir, "papers")
    private val cameraDir = File(context.cacheDir, "camera")

    /** Copies a picture the driver chose into the app, scaled down and turned upright; its name, or null if it cannot be read. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_IMAGE_SIDE)
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { exifDegrees(it) } ?: 0
            save(upright(decoded, rotation))
        }.getOrNull()
    }

    /** A file for the camera to write into, and the address to give it. */
    fun newCameraTarget(): Pair<File, Uri> {
        cameraDir.mkdirs()
        val file = File(cameraDir, "${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Same as [import], for a photo the camera just took. */
    suspend fun importFile(file: File): String? = import(Uri.fromFile(file))

    /** The picture [name] for the screen, with its longer side no bigger than [maxSide]; null if it is gone. */
    suspend fun load(name: String, maxSide: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, name)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide) }
            BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
        }.getOrNull()
    }

    /** Throws away the pictures no paper points to anymore (a paper or a car was deleted). */
    fun deleteUnreferenced(garage: Garage) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val now = System.currentTimeMillis()
        val old = orphanImages(files.map { it.name to now - it.lastModified() }, garage.referencedImages(), minAgeMillis = ORPHAN_GRACE_MILLIS)
        old.forEach { File(dir, it).delete() }
    }

    private fun save(bitmap: Bitmap): String {
        dir.mkdirs()
        val (width, height) = fitSize(bitmap.width, bitmap.height, MAX_IMAGE_SIDE)
        val scaled = if (width != bitmap.width || height != bitmap.height) Bitmap.createScaledBitmap(bitmap, width, height, true) else bitmap
        val name = "${UUID.randomUUID()}.jpg"
        File(dir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return name
    }

    private fun upright(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    private fun exifDegrees(input: java.io.InputStream): Int = when (
        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private companion object {
        const val JPEG_QUALITY = 85

        /** A picture not yet saved into its paper must not be swept away. */
        const val ORPHAN_GRACE_MILLIS = 60_000L
    }
}

package corsica.universita.objetscampus.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Stockage interne des photos dans filesDir/photos/ (TD A7).
 * - Ne jamais stocker un BLOB photo dans Room (limite CursorWindow ~2 Mo).
 * - Room ne stocke que le chemin absolu (photoPath: String?).
 * - Partage securise vers les autres applications via FileProvider (content://).
 */
class PhotoStore(private val context: Context) {
    private val dir = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun saveJpeg(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        file.absolutePath
    }

    suspend fun saveFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val bitmap = BitmapFactory.decodeStream(stream) ?: return@use null
            val out = ByteArrayOutputStream()
            // Redimensionnement similaire a la PWA (max 900px, JPEG 75%) pour economiser l'espace
            val maxDim = 900
            val ratio = minOf(
                maxDim.toFloat() / bitmap.width.toFloat(),
                maxDim.toFloat() / bitmap.height.toFloat(),
                1.0f
            )
            val scaled = if (ratio < 1.0f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
            out.toByteArray()
        } ?: return@withContext null

        saveJpeg(bytes)
    }

    fun delete(path: String?) {
        if (path != null) {
            runCatching { File(path).delete() }
        }
    }

    fun contentUri(path: String): Uri? {
        val file = File(path)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file
        )
    }
}

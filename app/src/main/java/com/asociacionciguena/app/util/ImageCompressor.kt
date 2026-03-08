package com.asociacionciguena.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.default
import id.zelory.compressor.constraint.destination
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageCompressor {

    /**
     * Comprimir imagen para foto de perfil
     * Tamaño: 512x512px, Calidad: 80%
     */
    suspend fun compressProfilePhoto(context: Context, uri: Uri): File {
        return withContext(Dispatchers.IO) {
            val originalFile = uriToFile(context, uri)

            Compressor.compress(context, originalFile) {
                resolution(512, 512)
                quality(80)
                destination(File(context.cacheDir, "compressed_profile_${System.currentTimeMillis()}.jpg"))
            }
        }
    }

    /**
     * Comprimir imagen para noticias y galería
     * Ancho máx: 1920px, Calidad: 85%
     */
    suspend fun compressPhoto(context: Context, uri: Uri): File {
        return withContext(Dispatchers.IO) {
            val originalFile = uriToFile(context, uri)

            Compressor.compress(context, originalFile) {
                default(width = 1920, height = 1920)
                quality(85)
                destination(File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg"))
            }
        }
    }

    /**
     * Convertir URI a File temporal
     */
    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir la imagen")

        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")

        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        // Corregir orientación EXIF
        return correctImageOrientation(tempFile)
    }

    /**
     * Corregir orientación de imagen basada en EXIF
     */
    private fun correctImageOrientation(file: File): File {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            if (orientation == ExifInterface.ORIENTATION_NORMAL) {
                return file // No necesita corrección
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            val matrix = Matrix()

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            FileOutputStream(file).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            bitmap.recycle()
            rotatedBitmap.recycle()

            return file
        } catch (e: Exception) {
            // Si falla, devolver archivo original
            return file
        }
    }
}
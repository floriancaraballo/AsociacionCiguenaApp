package com.asociacionciguena.app.presentation.screens.admin.photos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.asociacionciguena.app.R
import com.asociacionciguena.app.util.ImageCompressor
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class PhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val excursionId = inputData.getString(KEY_EXCURSION_ID) ?: return failure("Excursion no válida")
        val uploaderId = inputData.getString(KEY_UPLOADER_ID) ?: return failure("Usuario no válido")
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return failure("Subida no válida")
        val paths = inputData.getStringArray(KEY_FILE_PATHS)?.toList().orEmpty()
        val mimeTypes = inputData.getStringArray(KEY_MIME_TYPES)?.toList().orEmpty()
        if (paths.isEmpty() || paths.size != mimeTypes.size) return failure("No se encontraron los archivos seleccionados")

        val files = paths.map(::File)
        var retainFilesForRetry = false
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                ?: return failure("Inicia sesión de nuevo para subir los archivos")
            if (currentUserId != uploaderId || !canManageExcursionMedia(uploaderId)) {
                return failure("No tienes permisos para subir fotos")
            }

            setForeground(createForegroundInfo(0, files.size))
            val firestore = FirebaseFirestore.getInstance()
            val storage = FirebaseStorage.getInstance()
            val photoIds = paths.map { path -> UUID.nameUUIDFromBytes("$batchId:$path".toByteArray()).toString() }
            firestore.collection("uploadBatches").document(batchId).set(
                hashMapOf("excursionId" to excursionId, "photoCount" to files.size, "status" to "uploading", "createdAt" to Timestamp.now())
            ).await()

            files.forEachIndexed { index, file ->
                if (!file.isFile) return failure("No se encontró un archivo pendiente")
                val isVideo = mimeTypes[index].startsWith("video/")
                val photoId = photoIds[index]
                val storagePath = "excursions/$excursionId/$photoId.${if (isVideo) "mp4" else "jpg"}"
                setProgress(workDataOfProgress(index, files.size))

                val uploadUri = if (isVideo) Uri.fromFile(file) else {
                    Uri.fromFile(ImageCompressor.compressPhoto(applicationContext, Uri.fromFile(file)))
                }
                val storageRef = storage.reference.child(storagePath)
                if (isVideo) {
                    val metadata = StorageMetadata.Builder().setContentType(mimeTypes[index])
                        .setCacheControl("public, max-age=31536000").build()
                    storageRef.putFile(uploadUri, metadata).await()
                } else {
                    storageRef.putFile(uploadUri).await()
                    File(uploadUri.path.orEmpty()).delete()
                }
                val downloadUrl = storageRef.downloadUrl.await().toString()
                val thumbnailUrl = if (isVideo) uploadVideoThumbnail(excursionId, photoId, Uri.fromFile(file), storage) else null
                firestore.collection("photos").document(photoId).set(
                    hashMapOf(
                        "id" to photoId, "excursionId" to excursionId, "imageUrl" to downloadUrl,
                        "storagePath" to storagePath, "uploadedBy" to uploaderId, "uploadedAt" to Timestamp.now(),
                        "authorizedUsers" to emptyList<String>(), "batchId" to batchId,
                        "mediaType" to if (isVideo) "video" else "image", "thumbnailUrl" to thumbnailUrl
                    )
                ).await()
                setProgress(workDataOfProgress(index + 1, files.size))
                setForeground(createForegroundInfo(index + 1, files.size))
            }
            firestore.collection("uploadBatches").document(batchId)
                .update(mapOf("status" to "completed", "completedAt" to Timestamp.now())).await()
            return Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            retainFilesForRetry = true
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                retainFilesForRetry = true
                return Result.retry()
            }
            return failure("Error al subir: ${e.localizedMessage ?: "Error desconocido"}")
        } finally {
            if (!retainFilesForRetry) {
                files.forEach { it.delete() }
                files.firstOrNull()?.parentFile?.delete()
            }
        }
    }

    private suspend fun canManageExcursionMedia(userId: String): Boolean {
        val role = FirebaseFirestore.getInstance().collection("users").document(userId).get().await().getString("role")
        return role == "admin" || role == "superadmin" || role == "monitor"
    }

    private suspend fun uploadVideoThumbnail(excursionId: String, photoId: String, uri: Uri, storage: FirebaseStorage): String? = try {
        val retriever = MediaMetadataRetriever()
        val bitmap = try {
            retriever.setDataSource(applicationContext, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally { retriever.release() }
        bitmap?.let {
            val output = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 80, output)
            val ref = storage.reference.child("excursions/$excursionId/thumbnails/$photoId.jpg")
            ref.putBytes(output.toByteArray()).await()
            ref.downloadUrl.await().toString()
        }
    } catch (_: Exception) { null }

    private fun createForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Subida de archivos", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Subiendo fotos y vídeos")
            .setContentText("Archivo $current de $total")
            .setProgress(total, current, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun workDataOfProgress(current: Int, total: Int) = androidx.work.Data.Builder()
        .putFloat(KEY_PROGRESS, current.toFloat() / total).putInt(KEY_CURRENT, current.coerceAtLeast(1))
        .putInt(KEY_TOTAL, total).build()

    private fun failure(message: String) = Result.failure(androidx.work.Data.Builder().putString(KEY_ERROR, message).build())

    companion object {
        const val TAG = "persistent_photo_upload"
        const val KEY_EXCURSION_ID = "excursion_id"
        const val KEY_UPLOADER_ID = "uploader_id"
        const val KEY_BATCH_ID = "batch_id"
        const val KEY_FILE_PATHS = "file_paths"
        const val KEY_MIME_TYPES = "mime_types"
        const val KEY_PROGRESS = "progress"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "photo_upload_progress"
        private const val NOTIFICATION_ID = 5401
    }
}

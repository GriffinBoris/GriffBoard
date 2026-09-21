package com.griffinboris.griffboard.models

import android.content.Context
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class ModelDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val model = ModelCatalog.find(requireNotNull(inputData.getString("model")))
        val target = ModelStore(applicationContext).file(model)
        val partial = File(target.parentFile, "${model.filename}.$id.part")
        var connection: HttpURLConnection? = null
        try {
            if (StatFs(target.parent!!).availableBytes < model.bytes + 32_000_000) {
                throw IOException("Not enough storage for this model.")
            }
            connection = URI(model.url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download failed (HTTP ${connection.responseCode}). Please retry.")
            }
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    ModelVerifier.copy(input, output, model) { received ->
                        coroutineContext.ensureActive()
                        val percent = (received * 100 / model.bytes).toInt()
                        if (percent != lastPercent) {
                            setProgressAsync(workDataOf("percent" to percent))
                            lastPercent = percent
                        }
                    }
                    output.fd.sync()
                }
            }
            coroutineContext.ensureActive()
            if (!partial.renameTo(target)) throw IOException("Could not save the downloaded model.")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.failure(workDataOf("error" to (error.message ?: "Download failed. Please retry.")))
        } finally {
            connection?.disconnect()
            partial.delete()
        }
    }

    companion object {
        fun workName(model: WhisperModel) = "model-${model.id}"
        fun enqueue(context: Context, model: WhisperModel) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf("model" to model.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("model-download")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(model), ExistingWorkPolicy.KEEP, request)
        }
    }
}

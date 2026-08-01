package com.snaprelay.upload

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class TelegramUploader(
    client: OkHttpClient? = null
) {
    private val okHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun uploadDocument(
        botToken: String,
        chatId: String,
        file: File,
        caption: String? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) {
            return@withContext UploadResult.PermanentFailure("Telegram Bot Token is missing.")
        }
        if (chatId.isBlank()) {
            return@withContext UploadResult.PermanentFailure("Telegram Chat ID is missing.")
        }
        if (!file.exists()) {
            return@withContext UploadResult.PermanentFailure("File does not exist: ${file.absolutePath}")
        }

        val url = "https://api.telegram.org/bot$botToken/sendDocument"

        val fileRequestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("document", file.name, fileRequestBody)

        caption?.let {
            requestBodyBuilder.addFormDataPart("caption", it)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyBuilder.build())
            .build()

        try {
            Log.d("TelegramUploader", "Uploading ${file.name} to Telegram chat $chatId...")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Log.d("TelegramUploader", "Upload success: $responseBody")
                UploadResult.Success(responseBody)
            } else {
                val statusCode = response.code
                Log.e("TelegramUploader", "Upload failed with HTTP $statusCode: $responseBody")
                when (statusCode) {
                    401, 404, 400, 403 -> UploadResult.PermanentFailure("HTTP $statusCode: $responseBody")
                    else -> UploadResult.RetryableFailure("HTTP $statusCode: $responseBody")
                }
            }
        } catch (e: IOException) {
            Log.e("TelegramUploader", "Network error during upload", e)
            UploadResult.RetryableFailure("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            Log.e("TelegramUploader", "Unexpected error during upload", e)
            UploadResult.PermanentFailure("Unexpected error: ${e.localizedMessage}")
        }
    }
}

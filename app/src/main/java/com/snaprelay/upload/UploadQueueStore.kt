package com.snaprelay.upload

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class UploadQueueStore(private val context: Context) {

    private val queueFile: File
        get() = File(context.filesDir, "upload_queue.json")

    fun loadTasks(): List<UploadTask> {
        val file = queueFile
        if (!file.exists()) return emptyList()

        return try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            val tasks = mutableListOf<UploadTask>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                tasks.add(
                    UploadTask(
                        id = obj.getString("id"),
                        filePath = obj.getString("filePath"),
                        status = try { UploadStatus.valueOf(obj.getString("status")) } catch (e: Exception) { UploadStatus.PENDING },
                        attempts = obj.optInt("attempts", 0),
                        createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
                        lastAttemptMs = obj.optLong("lastAttemptMs", 0L),
                        errorMessage = obj.optString("errorMessage", null)
                    )
                )
            }
            tasks
        } catch (e: Exception) {
            Log.e("UploadQueueStore", "Failed to load queue from disk", e)
            emptyList()
        }
    }

    fun saveTasks(tasks: List<UploadTask>) {
        try {
            val jsonArray = JSONArray()
            for (task in tasks) {
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("filePath", task.filePath)
                    put("status", task.status.name)
                    put("attempts", task.attempts)
                    put("createdAtMs", task.createdAtMs)
                    put("lastAttemptMs", task.lastAttemptMs)
                    put("errorMessage", task.errorMessage ?: JSONObject.NULL)
                }
                jsonArray.put(obj)
            }
            queueFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e("UploadQueueStore", "Failed to save queue to disk", e)
        }
    }
}

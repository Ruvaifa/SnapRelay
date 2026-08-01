package com.snaprelay.upload

import android.util.Log
import com.snaprelay.logging.LogLevel
import com.snaprelay.logging.LogRepository
import com.snaprelay.settings.AppSettings
import com.snaprelay.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class UploadQueueManager(
    private val queueStore: UploadQueueStore,
    private val telegramUploader: TelegramUploader,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks: StateFlow<List<UploadTask>> = _tasks.asStateFlow()

    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)

    init {
        // Load persisted queue from disk on launch
        val initialTasks = queueStore.loadTasks()
        // Reset any stale IN_PROGRESS tasks to PENDING if process died mid-upload
        val sanitizedTasks = initialTasks.map { task ->
            if (task.status == UploadStatus.IN_PROGRESS) {
                task.copy(status = UploadStatus.PENDING)
            } else {
                task
            }
        }
        _tasks.value = sanitizedTasks

        logRepository?.log("Init", "UploadQueueManager initialized with ${sanitizedTasks.size} persisted tasks.")

        // Start serial worker loop
        scope.launch {
            startWorkerLoop()
        }
    }

    fun enqueue(file: File) {
        val newTask = UploadTask(
            id = UUID.randomUUID().toString(),
            filePath = file.absolutePath,
            status = UploadStatus.PENDING
        )
        val updatedList = _tasks.value + newTask
        updateQueue(updatedList)
        triggerChannel.trySend(Unit)
        logRepository?.log("Queued", "Enqueued ${file.name} for background upload.")
        Log.d("UploadQueueManager", "Enqueued task ${newTask.id} for file ${file.name}")
    }

    fun retryFailedTask(taskId: String) {
        val updatedList = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(status = UploadStatus.PENDING, attempts = 0, errorMessage = null)
            } else {
                task
            }
        }
        updateQueue(updatedList)
        triggerChannel.trySend(Unit)
        logRepository?.log("Retry", "Manual retry triggered for task $taskId.")
    }

    fun retryAllFailedTasks() {
        val updatedList = _tasks.value.map { task ->
            if (task.status == UploadStatus.FAILED) {
                task.copy(status = UploadStatus.PENDING, attempts = 0, errorMessage = null)
            } else {
                task
            }
        }
        updateQueue(updatedList)
        triggerChannel.trySend(Unit)
        logRepository?.log("Retry", "Triggered retry for all failed tasks.")
    }

    fun clearFailedTasks() {
        val updatedList = _tasks.value.filter { it.status != UploadStatus.FAILED }
        updateQueue(updatedList)
        logRepository?.log("Queue", "Cleared failed tasks from queue.")
    }

    fun clearCompletedTasks() {
        val updatedList = _tasks.value.filter { it.status != UploadStatus.SUCCESS }
        updateQueue(updatedList)
    }

    private fun updateQueue(newList: List<UploadTask>) {
        _tasks.value = newList
        queueStore.saveTasks(newList)
    }

    private suspend fun startWorkerLoop() {
        while (true) {
            val pendingTask = _tasks.value.firstOrNull { task ->
                task.status == UploadStatus.PENDING || task.status == UploadStatus.RETRY_SCHEDULED
            }

            if (pendingTask == null) {
                // Wait for next enqueue trigger
                triggerChannel.receive()
                continue
            }

            processTask(pendingTask)
        }
    }

    private suspend fun processTask(task: UploadTask) {
        // Mark IN_PROGRESS
        updateTaskStatus(task.id, UploadStatus.IN_PROGRESS)

        val settings: AppSettings = settingsRepository.settingsFlow.first()
        val file = File(task.filePath)

        if (!file.exists()) {
            val errMsg = "File missing from disk: ${task.filePath}"
            logRepository?.log("Failure", errMsg, LogLevel.ERROR)
            updateTaskStatus(
                task.id,
                UploadStatus.FAILED,
                error = errMsg
            )
            return
        }

        logRepository?.log("Uploading", "Sending ${file.name} to Telegram...")
        val result = telegramUploader.uploadDocument(
            botToken = settings.botToken,
            chatId = settings.chatId,
            file = file,
            caption = "SnapRelay • ${file.name}"
        )

        when (result) {
            is UploadResult.Success -> {
                Log.d("UploadQueueManager", "Successfully uploaded task ${task.id}")
                logRepository?.log("Success", "Uploaded ${file.name} to Telegram.")
                updateTaskStatus(task.id, UploadStatus.SUCCESS)

                // Optional delete after upload
                if (settings.deleteAfterUpload) {
                    try {
                        val deleted = file.delete()
                        logRepository?.log("Storage", "Deleted ${file.name} after upload (status: $deleted).")
                        Log.d("UploadQueueManager", "Delete after upload for ${file.name}: $deleted")
                    } catch (e: Exception) {
                        Log.e("UploadQueueManager", "Failed to delete file after upload", e)
                    }
                }
            }

            is UploadResult.RetryableFailure -> {
                val nextAttempts = task.attempts + 1
                if (nextAttempts >= settings.maxUploadRetries) {
                    val errMsg = "Max retries reached: ${result.reason}"
                    Log.e("UploadQueueManager", errMsg)
                    logRepository?.log("Failure", errMsg, LogLevel.ERROR)
                    updateTaskStatus(task.id, UploadStatus.FAILED, error = result.reason, attempts = nextAttempts)
                } else {
                    val backoffSeconds = minOf(60L, 2L * (1 shl nextAttempts))
                    val warnMsg = "Retryable failure (attempt $nextAttempts), retrying in ${backoffSeconds}s: ${result.reason}"
                    Log.w("UploadQueueManager", warnMsg)
                    logRepository?.log("Retry", warnMsg, LogLevel.WARN)
                    updateTaskStatus(
                        task.id,
                        UploadStatus.RETRY_SCHEDULED,
                        error = result.reason,
                        attempts = nextAttempts
                    )
                    delay(backoffSeconds * 1000L)
                }
            }

            is UploadResult.PermanentFailure -> {
                val errMsg = "Permanent upload failure: ${result.reason}"
                Log.e("UploadQueueManager", errMsg)
                logRepository?.log("Failure", errMsg, LogLevel.ERROR)
                updateTaskStatus(task.id, UploadStatus.FAILED, error = result.reason)
            }
        }
    }

    private fun updateTaskStatus(
        taskId: String,
        newStatus: UploadStatus,
        error: String? = null,
        attempts: Int? = null
    ) {
        val updatedList = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(
                    status = newStatus,
                    lastAttemptMs = System.currentTimeMillis(),
                    errorMessage = error ?: task.errorMessage,
                    attempts = attempts ?: task.attempts
                )
            } else {
                task
            }
        }
        updateQueue(updatedList)
    }
}

package com.snaprelay.upload

enum class UploadStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    RETRY_SCHEDULED,
    FAILED
}

data class UploadTask(
    val id: String,
    val filePath: String,
    val status: UploadStatus = UploadStatus.PENDING,
    val attempts: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastAttemptMs: Long = 0L,
    val errorMessage: String? = null
)

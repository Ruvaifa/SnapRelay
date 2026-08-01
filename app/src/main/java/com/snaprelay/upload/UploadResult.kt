package com.snaprelay.upload

sealed class UploadResult {
    data class Success(val responseText: String) : UploadResult()
    data class RetryableFailure(val reason: String) : UploadResult()
    data class PermanentFailure(val reason: String) : UploadResult()
}

package com.snaprelay.settings

data class AppSettings(
    val botToken: String = "",
    val chatId: String = "",
    val deleteAfterUpload: Boolean = false,
    val maxUploadRetries: Int = 3,
    val rotationDegrees: Int = 90
)

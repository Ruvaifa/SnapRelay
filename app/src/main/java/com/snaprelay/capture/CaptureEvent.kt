package com.snaprelay.capture

import java.io.File

sealed class CaptureEvent {
    data class Captured(val file: File) : CaptureEvent()
    data class Error(val throwable: Throwable) : CaptureEvent()
}

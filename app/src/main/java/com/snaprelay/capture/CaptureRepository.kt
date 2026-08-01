package com.snaprelay.capture

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureRepository(private val context: Context) {

    private val _captureEvents = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 64)
    val captureEvents: SharedFlow<CaptureEvent> = _captureEvents.asSharedFlow()

    fun getPicturesDirectory(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) 
            ?: File(context.filesDir, "pictures")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createOutputFile(): File {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "SNAP_$timeStamp.jpg"
        return File(getPicturesDirectory(), fileName)
    }

    fun notifyCaptured(file: File) {
        _captureEvents.tryEmit(CaptureEvent.Captured(file))
    }

    fun notifyError(throwable: Throwable) {
        _captureEvents.tryEmit(CaptureEvent.Error(throwable))
    }
}

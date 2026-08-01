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

    fun getStorageUsage(): Pair<Int, Long> {
        val dir = getPicturesDirectory()
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") } ?: emptyArray()
        val totalBytes = files.sumOf { it.length() }
        return Pair(files.size, totalBytes)
    }

    fun clearAllLocalSnapshots(): Int {
        val dir = getPicturesDirectory()
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") } ?: emptyArray()
        var deletedCount = 0
        for (file in files) {
            if (file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }

    fun notifyCaptured(file: File) {
        _captureEvents.tryEmit(CaptureEvent.Captured(file))
    }

    fun notifyError(throwable: Throwable) {
        _captureEvents.tryEmit(CaptureEvent.Error(throwable))
    }
}

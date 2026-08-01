package com.snaprelay.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.snaprelay.capture.CaptureRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class CameraManager(
    private val context: Context,
    private val captureRepository: CaptureRepository
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                this.cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                Log.d("CameraManager", "Camera successfully bound")
                continuation.resume(true)
            } catch (e: Exception) {
                Log.e("CameraManager", "Use case binding failed", e)
                continuation.resume(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun captureNow(onResult: (Result<File>) -> Unit) {
        val captureUseCase = imageCapture ?: run {
            val err = IllegalStateException("Camera not bound or ImageCapture is null")
            captureRepository.notifyError(err)
            onResult(Result.failure(err))
            return
        }

        val outputFile = captureRepository.createOutputFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        val mainExecutor = ContextCompat.getMainExecutor(context)

        captureUseCase.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("CameraManager", "Photo capture succeeded: ${outputFile.absolutePath}")
                    captureRepository.notifyCaptured(outputFile)
                    onResult(Result.success(outputFile))
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraManager", "Photo capture failed: ${exception.message}", exception)
                    captureRepository.notifyError(exception)
                    onResult(Result.failure(exception))
                }
            }
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
    }
}

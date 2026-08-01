package com.snaprelay.camera

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.snaprelay.capture.CaptureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val controlBridge = Camera2ControlBridge()

    private val _capabilities = MutableStateFlow<CameraCapabilityReport?>(null)
    val capabilities: StateFlow<CameraCapabilityReport?> = _capabilities.asStateFlow()

    private val _settingsState = MutableStateFlow(CameraSettingsState())
    val settingsState: StateFlow<CameraSettingsState> = _settingsState.asStateFlow()

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                this.cameraProvider = provider

                val targetRotation = getSurfaceRotation(_settingsState.value.rotationDegrees)

                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Force full high-resolution sensor strategy (50MP/12MP max camera hardware output)
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                // Maximize image quality and sharpness for reading laptop screen text
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(100)
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(targetRotation)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                this.camera = boundCamera

                // Inspect capabilities
                val report = CameraCapabilities.inspect(boundCamera.cameraInfo)
                _capabilities.value = report

                // Apply initial settings
                controlBridge.applySettings(boundCamera, _settingsState.value, report)

                Log.d("CameraManager", "Camera bound with capabilities: $report")
                continuation.resume(true)
            } catch (e: Exception) {
                Log.e("CameraManager", "Use case binding failed", e)
                continuation.resume(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun getSurfaceRotation(degrees: Int): Int {
        return when (degrees) {
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }
    }

    fun resetToDefaults() {
        val defaultSettings = CameraSettingsState()
        updateSettings(defaultSettings)
    }

    fun focusOnPoint(x: Float, y: Float, previewView: PreviewView) {
        val cameraControl = camera?.cameraControl ?: return
        try {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point).build()
            cameraControl.startFocusAndMetering(action)
            Log.d("CameraManager", "Triggered tap-to-focus at ($x, $y)")
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to tap-to-focus", e)
        }
    }

    fun updateSettings(newSettings: CameraSettingsState) {
        _settingsState.value = newSettings
        val rotation = getSurfaceRotation(newSettings.rotationDegrees)
        imageCapture?.targetRotation = rotation
        controlBridge.applySettings(camera, newSettings, _capabilities.value)
    }

    fun captureNow(onResult: (Result<File>) -> Unit) {
        val captureUseCase = imageCapture ?: run {
            val err = IllegalStateException("Camera not bound or ImageCapture is null")
            captureRepository.notifyError(err)
            onResult(Result.failure(err))
            return
        }

        // Ensure targetRotation is explicitly set right before takePicture
        captureUseCase.targetRotation = getSurfaceRotation(_settingsState.value.rotationDegrees)

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

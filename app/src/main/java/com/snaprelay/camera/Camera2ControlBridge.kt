package com.snaprelay.camera

import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera

class Camera2ControlBridge {

    fun applySettings(camera: Camera?, state: CameraSettingsState, report: CameraCapabilityReport?) {
        val cameraControl = camera?.cameraControl ?: return
        val camera2Control = Camera2CameraControl.from(cameraControl)

        val optionsBuilder = CaptureRequestOptions.Builder()

        // 1. AE Lock & Manual ISO
        if (state.isAeLocked && report?.isAeLockSupported == true) {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        } else {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
        }

        if (state.isManualIsoEnabled && report?.isManualIsoSupported == true) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                state.iso
            )
        }

        // 2. AF Lock & Manual Focus
        if (state.isManualFocusEnabled && report?.isManualFocusSupported == true) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                state.focusDistance
            )
        } else if (state.isAfLocked) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO
            )
        } else {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        }

        // High quality Edge Sharpening & Noise Reduction (ISP tuning for crisp text)
        optionsBuilder.setCaptureRequestOption(
            CaptureRequest.EDGE_MODE,
            CaptureRequest.EDGE_MODE_HIGH_QUALITY
        )
        optionsBuilder.setCaptureRequestOption(
            CaptureRequest.NOISE_REDUCTION_MODE,
            CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
        )

        // Apply Camera2 options
        camera2Control.setCaptureRequestOptions(optionsBuilder.build())

        // 3. Exposure Compensation via CameraX API
        if (report?.isExposureCompensationSupported == true) {
            try {
                cameraControl.setExposureCompensationIndex(state.exposureCompensationIndex)
            } catch (e: Exception) {
                Log.e("Camera2ControlBridge", "Failed to set exposure compensation", e)
            }
        }
    }
}

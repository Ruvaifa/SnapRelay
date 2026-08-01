package com.snaprelay.camera

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo

data class CameraCapabilityReport(
    val hardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
    val isAeLockSupported: Boolean = false,
    val isAfLockSupported: Boolean = true,
    val isManualIsoSupported: Boolean = false,
    val isoRange: ClosedRange<Int>? = null,
    val isManualFocusSupported: Boolean = false,
    val minFocusDistance: Float? = null,
    val isExposureCompensationSupported: Boolean = false,
    val exposureCompensationRange: Range<Int>? = null
) {
    val isFullOrLevel3: Boolean
        get() = hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL ||
                hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
}

object CameraCapabilities {

    fun inspect(cameraInfo: CameraInfo): CameraCapabilityReport {
        return try {
            val camera2Info = Camera2CameraInfo.from(cameraInfo)
            
            val hwLevel = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
            ) ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY

            val aeLockSupported = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE
            ) == true

            val isoRangeSys = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
            )
            val isoRange = isoRangeSys?.let { it.lower..it.upper }
            val manualIsoSupported = isoRange != null

            val minFocusDist = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
            )
            val manualFocusSupported = minFocusDist != null && minFocusDist > 0f

            val expRange = cameraInfo.exposureState.exposureCompensationRange
            val expSupported = cameraInfo.exposureState.isExposureCompensationSupported

            CameraCapabilityReport(
                hardwareLevel = hwLevel,
                isAeLockSupported = aeLockSupported,
                isAfLockSupported = true,
                isManualIsoSupported = manualIsoSupported,
                isoRange = isoRange,
                isManualFocusSupported = manualFocusSupported,
                minFocusDistance = minFocusDist,
                isExposureCompensationSupported = expSupported,
                exposureCompensationRange = expRange
            )
        } catch (e: Exception) {
            CameraCapabilityReport()
        }
    }
}

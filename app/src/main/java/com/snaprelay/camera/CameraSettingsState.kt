package com.snaprelay.camera

data class CameraSettingsState(
    val isAfLocked: Boolean = false,
    val isAeLocked: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isManualIsoEnabled: Boolean = false,
    val iso: Int = 100,
    val isManualFocusEnabled: Boolean = false,
    val focusDistance: Float = 0.0f,
    val exposureCompensationIndex: Int = 0,
    val rotationDegrees: Int = 90 // Default 90 degrees for horizontal tripod mounting
)

package com.snaprelay.camera

data class CameraSettingsState(
    val isAfLocked: Boolean = false,
    val isAeLocked: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val isManualIsoEnabled: Boolean = false,
    val iso: Int = 100,
    val isManualFocusEnabled: Boolean = false,
    val focusDistance: Float = 0.0f, // 0.0f = infinity, higher = closer (diopters)
    val exposureCompensationIndex: Int = 0,
    val rotationDegrees: Int = 0 // 0, 90, 180, 270 degrees manual rotation
)

package com.snaprelay.camera

import android.view.KeyEvent
import android.util.Log

class VolumeKeyCaptureHandler(
    private val onCaptureTriggered: () -> Unit
) {
    private var lastCaptureTimeMs: Long = 0L
    private val debounceIntervalMs: Long = 400L

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val currentTime = System.currentTimeMillis()
            // Ignore key repeats (event.repeatCount > 0) or double fires within debounce interval
            if (event?.repeatCount == 0 && currentTime - lastCaptureTimeMs > debounceIntervalMs) {
                lastCaptureTimeMs = currentTime
                Log.d("VolumeKeyCaptureHandler", "Volume Up pressed - triggering capture")
                onCaptureTriggered()
            }
            // Return true to consume the event so system media volume doesn't change
            return true
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Consume key up as well
            return true
        }
        return false
    }
}

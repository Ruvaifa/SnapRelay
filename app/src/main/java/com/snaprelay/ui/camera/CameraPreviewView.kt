package com.snaprelay.ui.camera

import android.view.MotionEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onTapToFocus: ((Float, Float, PreviewView) -> Unit)? = null,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        onTapToFocus?.invoke(event.x, event.y, this)
                    }
                    true
                }
                onPreviewViewCreated(this)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

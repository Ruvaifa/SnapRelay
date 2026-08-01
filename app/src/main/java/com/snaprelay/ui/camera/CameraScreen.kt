package com.snaprelay.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaprelay.camera.CameraManager
import com.snaprelay.capture.CaptureEvent
import com.snaprelay.capture.CaptureRepository
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    cameraManager: CameraManager,
    captureRepository: CaptureRepository,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastCapturedFile by remember { mutableStateOf<String?>(null) }
    var captureCount by remember { mutableIntStateOf(0) }
    var showFlashOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        captureRepository.captureEvents.collect { event ->
            when (event) {
                is CaptureEvent.Captured -> {
                    lastCapturedFile = event.file.name
                    captureCount++
                    showFlashOverlay = true
                    delay(150)
                    showFlashOverlay = false
                }
                is CaptureEvent.Error -> {
                    lastCapturedFile = "Error: ${event.throwable.message}"
                }
            }
        }
    }

    var previewViewInstance by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }

    LaunchedEffect(previewViewInstance) {
        previewViewInstance?.let { view ->
            cameraManager.bindCamera(lifecycleOwner, view)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        CameraPreviewView(
            onPreviewViewCreated = { previewView ->
                previewViewInstance = previewView
            }
        )

        // Subtle Flash Effect on Shutter Click
        AnimatedVisibility(
            visible = showFlashOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.4f)))
        }

        // Top Status Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "SnapRelay • Captures: $captureCount",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            lastCapturedFile?.let { fileName ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Saved: $fileName",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom Shutter Controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer Ring
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                // Inner Shutter Button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            cameraManager.captureNow { /* Handled via CaptureRepository Flow */ }
                        }
                )
            }
        }
    }
}

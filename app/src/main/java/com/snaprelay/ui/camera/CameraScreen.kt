package com.snaprelay.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.snaprelay.ui.camera.components.CameraControlChips
import com.snaprelay.upload.UploadQueueManager
import com.snaprelay.upload.UploadStatus
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(
    cameraManager: CameraManager,
    captureRepository: CaptureRepository,
    uploadQueueManager: UploadQueueManager,
    onOpenSettingsClicked: () -> Unit,
    onFileCaptured: (java.io.File) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastCapturedFile by remember { mutableStateOf<String?>(null) }
    var captureCount by remember { mutableIntStateOf(0) }
    var showFlashOverlay by remember { mutableStateOf(false) }

    val capabilities by cameraManager.capabilities.collectAsState()
    val settingsState by cameraManager.settingsState.collectAsState()
    val uploadTasks by uploadQueueManager.tasks.collectAsState()

    val pendingCount = uploadTasks.count {
        it.status == UploadStatus.PENDING || it.status == UploadStatus.IN_PROGRESS || it.status == UploadStatus.RETRY_SCHEDULED
    }
    val failedCount = uploadTasks.count { it.status == UploadStatus.FAILED }

    LaunchedEffect(Unit) {
        captureRepository.captureEvents.collect { event ->
            when (event) {
                is CaptureEvent.Captured -> {
                    lastCapturedFile = event.file.name
                    onFileCaptured(event.file)
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
            onTapToFocus = { x, y, view ->
                cameraManager.focusOnPoint(x, y, view)
            },
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
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(40.dp)) // Spacer for symmetry

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                // Live Upload Queue Status Chip
                Spacer(modifier = Modifier.height(6.dp))
                val (queueText, queueColor) = when {
                    pendingCount > 0 -> "📤 Uploading ($pendingCount in queue)" to Color(0xFF38BDF8)
                    failedCount > 0 -> "⚠️ $failedCount Failed" to Color(0xFFEF4444)
                    uploadTasks.isNotEmpty() -> "✅ All Uploaded" to Color(0xFF10B981)
                    else -> "Ready" to Color(0xFF94A3B8)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = queueText,
                        color = queueColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onOpenSettingsClicked,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        // Bottom Controls Container (HUD Chips + Shutter Button)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CameraControlChips(
                settingsState = settingsState,
                capabilities = capabilities,
                onSettingsChanged = { newSettings ->
                    cameraManager.updateSettings(newSettings)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

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

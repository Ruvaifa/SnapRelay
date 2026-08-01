package com.snaprelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.snaprelay.camera.CameraManager
import com.snaprelay.camera.VolumeKeyCaptureHandler
import com.snaprelay.capture.CaptureEvent
import com.snaprelay.capture.CaptureRepository
import com.snaprelay.logging.LogRepository
import com.snaprelay.settings.SettingsRepository
import com.snaprelay.ui.camera.CameraScreen
import com.snaprelay.ui.logs.LogScreen
import com.snaprelay.ui.settings.SettingsScreen
import com.snaprelay.upload.TelegramUploader
import com.snaprelay.upload.UploadQueueManager
import com.snaprelay.upload.UploadQueueStore
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var captureRepository: CaptureRepository
    private lateinit var cameraManager: CameraManager
    private lateinit var volumeKeyCaptureHandler: VolumeKeyCaptureHandler
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var telegramUploader: TelegramUploader
    private lateinit var uploadQueueStore: UploadQueueStore
    private lateinit var uploadQueueManager: UploadQueueManager
    private lateinit var logRepository: LogRepository

    private var hasCameraPermission by mutableStateOf(false)
    private var currentScreen by mutableStateOf(Screen.CAMERA)
    private var latestCapturedFile by mutableStateOf<File?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logRepository = LogRepository()
        captureRepository = CaptureRepository(applicationContext)
        cameraManager = CameraManager(applicationContext, captureRepository)
        settingsRepository = SettingsRepository(applicationContext)
        telegramUploader = TelegramUploader()
        uploadQueueStore = UploadQueueStore(applicationContext)
        uploadQueueManager = UploadQueueManager(
            queueStore = uploadQueueStore,
            telegramUploader = telegramUploader,
            settingsRepository = settingsRepository,
            logRepository = logRepository
        )

        // Automatically enqueue captures into persistent upload queue
        lifecycleScope.launch {
            captureRepository.captureEvents.collect { event ->
                if (event is CaptureEvent.Captured) {
                    logRepository.log("Captured", "Photo captured: ${event.file.name}")
                    uploadQueueManager.enqueue(event.file)
                }
            }
        }

        // Restore saved rotation preference
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                val current = cameraManager.settingsState.value
                if (current.rotationDegrees != settings.rotationDegrees) {
                    cameraManager.updateSettings(current.copy(rotationDegrees = settings.rotationDegrees))
                }
            }
        }

        volumeKeyCaptureHandler = VolumeKeyCaptureHandler {
            if (hasCameraPermission && currentScreen == Screen.CAMERA) {
                cameraManager.captureNow { /* Captured */ }
            }
        }

        checkCameraPermission()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (hasCameraPermission) {
                        when (currentScreen) {
                            Screen.CAMERA -> {
                                CameraScreen(
                                    cameraManager = cameraManager,
                                    captureRepository = captureRepository,
                                    uploadQueueManager = uploadQueueManager,
                                    settingsRepository = settingsRepository,
                                    onOpenSettingsClicked = { currentScreen = Screen.SETTINGS },
                                    onFileCaptured = { file -> latestCapturedFile = file }
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsScreen(
                                    settingsRepository = settingsRepository,
                                    captureRepository = captureRepository,
                                    telegramUploader = telegramUploader,
                                    latestCapturedFile = latestCapturedFile,
                                    onBackClicked = { currentScreen = Screen.CAMERA },
                                    onOpenLogsClicked = { currentScreen = Screen.LOGS }
                                )
                            }
                            Screen.LOGS -> {
                                LogScreen(
                                    logRepository = logRepository,
                                    onBackClicked = { currentScreen = Screen.SETTINGS }
                                )
                            }
                        }
                    } else {
                        PermissionRequestScreen(
                            onRequestPermission = {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkCameraPermission() {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyCaptureHandler.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyCaptureHandler.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.unbind()
    }
}

private enum class Screen {
    CAMERA, SETTINGS, LOGS
}

@androidx.compose.runtime.Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera Permission Required",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SnapRelay needs access to your camera to preview and snapshot your laptop screen.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Grant Permission",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

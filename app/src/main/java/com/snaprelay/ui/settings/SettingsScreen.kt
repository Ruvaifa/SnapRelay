package com.snaprelay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaprelay.capture.CaptureRepository
import com.snaprelay.settings.SettingsRepository
import com.snaprelay.upload.TelegramUploader
import com.snaprelay.upload.UploadQueueManager
import com.snaprelay.upload.UploadResult
import com.snaprelay.upload.UploadStatus
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    captureRepository: CaptureRepository,
    telegramUploader: TelegramUploader,
    uploadQueueManager: UploadQueueManager? = null,
    latestCapturedFile: File?,
    onResetToDefaults: () -> Unit,
    onBackClicked: () -> Unit,
    onOpenLogsClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appSettings by settingsRepository.settingsFlow.collectAsState(initial = com.snaprelay.settings.AppSettings())
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val uploadTasks by (uploadQueueManager?.tasks?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val failedCount = uploadTasks.count { it.status == UploadStatus.FAILED }

    var botTokenInput by remember { mutableStateOf("") }
    var chatIdInput by remember { mutableStateOf("") }

    var storageStats by remember { mutableStateOf(captureRepository.getStorageUsage()) }

    LaunchedEffect(appSettings) {
        if (botTokenInput.isEmpty() && appSettings.botToken.isNotEmpty()) {
            botTokenInput = appSettings.botToken
        }
        if (chatIdInput.isEmpty() && appSettings.chatId.isNotEmpty()) {
            chatIdInput = appSettings.chatId
        }
    }

    var showToken by remember { mutableStateOf(false) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings & Preferences",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Bot Token Field
        Text(
            text = "Telegram Bot Token",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = botTokenInput,
            onValueChange = { newToken ->
                botTokenInput = newToken
                coroutineScope.launch { settingsRepository.updateBotToken(newToken) }
            },
            placeholder = { Text("e.g. 123456789:ABCdefGhIJK...", color = Color.Gray) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle token visibility",
                        tint = Color.Gray
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Chat ID Field
        Text(
            text = "Telegram Chat ID",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = chatIdInput,
            onValueChange = { newChatId ->
                chatIdInput = newChatId
                coroutineScope.launch { settingsRepository.updateChatId(newChatId) }
            },
            placeholder = { Text("e.g. 987654321", color = Color.Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Delete After Upload Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Delete After Upload", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Automatically delete JPEGs from phone memory after Telegram receives them", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Switch(
                checked = appSettings.deleteAfterUpload,
                onCheckedChange = { checked ->
                    coroutineScope.launch { settingsRepository.updateDeleteAfterUpload(checked) }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2563EB)
                )
            )
        }

        // Retry & Clear Failed Uploads Section if any failed tasks exist
        if (failedCount > 0) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        uploadQueueManager?.retryAllFailedTasks()
                        testStatusMessage = "Retrying $failedCount failed upload(s)..."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry $failedCount Failed", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        uploadQueueManager?.clearFailedTasks()
                        testStatusMessage = "Cleared failed upload warnings."
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Clear Warnings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Restore Defaults Button inside Settings
        OutlinedButton(
            onClick = {
                onResetToDefaults()
                coroutineScope.launch {
                    settingsRepository.updateRotationDegrees(90)
                    settingsRepository.updateDeleteAfterUpload(false)
                }
                testStatusMessage = "All camera controls and orientation reset to default values (90°)."
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFF59E0B))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restore Camera Controls to Default", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // View Live Activity Logs Button
        OutlinedButton(
            onClick = onOpenLogsClicked,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.ListAlt, contentDescription = null, tint = Color(0xFF38BDF8))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View Live Upload & Activity Logs", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Storage Info Section
        Text(
            text = "Local Storage Management",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E293B))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${storageStats.first} Local Photos (${String.format("%.2f", storageStats.second / (1024f * 1024f))} MB)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Path: Android/data/com.snaprelay/files/Pictures/",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        val count = captureRepository.clearAllLocalSnapshots()
                        storageStats = captureRepository.getStorageUsage()
                        testStatusMessage = "Cleared $count local photos from storage."
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All Local Snapshots", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Test Upload Button
        Button(
            onClick = {
                if (latestCapturedFile == null) {
                    testStatusMessage = "Please take a photo first on the camera screen to test uploading!"
                    return@Button
                }
                isTesting = true
                testStatusMessage = "Uploading ${latestCapturedFile.name} to Telegram..."

                coroutineScope.launch {
                    val result = telegramUploader.uploadDocument(
                        botToken = botTokenInput.ifEmpty { appSettings.botToken },
                        chatId = chatIdInput.ifEmpty { appSettings.chatId },
                        file = latestCapturedFile,
                        caption = "SnapRelay Test Upload • ${latestCapturedFile.name}"
                    )
                    isTesting = false
                    testStatusMessage = when (result) {
                        is UploadResult.Success -> "✅ Test upload successful! Check your Telegram chat."
                        is UploadResult.RetryableFailure -> "⚠️ Network issue: ${result.reason}"
                        is UploadResult.PermanentFailure -> "❌ Failed: ${result.reason}"
                    }
                    storageStats = captureRepository.getStorageUsage()
                }
            },
            enabled = !isTesting,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isTesting) "Uploading..." else "Test Upload Latest Photo", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        testStatusMessage?.let { status ->
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                    .padding(12.dp)
            ) {
                Text(text = status, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

package com.snaprelay.ui.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaprelay.camera.CameraCapabilityReport
import com.snaprelay.camera.CameraSettingsState

@Composable
fun CameraControlChips(
    settingsState: CameraSettingsState,
    capabilities: CameraCapabilityReport?,
    onSettingsChanged: (CameraSettingsState) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSliderPanel by remember { mutableStateOf<SliderPanel?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Expandable Slider Popup Panel
        AnimatedVisibility(
            visible = activeSliderPanel != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                    .padding(16.dp)
            ) {
                when (activeSliderPanel) {
                    SliderPanel.ISO -> {
                        val minIso = capabilities?.isoRange?.start ?: 100
                        val maxIso = capabilities?.isoRange?.endInclusive ?: 3200
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Manual ISO: ${settingsState.iso}", color = Color.White, fontSize = 14.sp)
                                Text(
                                    text = if (settingsState.isManualIsoEnabled) "Disable Manual" else "Enable Manual",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        onSettingsChanged(settingsState.copy(isManualIsoEnabled = !settingsState.isManualIsoEnabled))
                                    }
                                )
                            }
                            if (settingsState.isManualIsoEnabled) {
                                Slider(
                                    value = settingsState.iso.toFloat(),
                                    onValueChange = { newIso ->
                                        onSettingsChanged(settingsState.copy(iso = newIso.toInt()))
                                    },
                                    valueRange = minIso.toFloat()..maxIso.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF38BDF8),
                                        activeTrackColor = Color(0xFF38BDF8)
                                    )
                                )
                            }
                        }
                    }
                    SliderPanel.EV -> {
                        val minEv = capabilities?.exposureCompensationRange?.lower ?: -4
                        val maxEv = capabilities?.exposureCompensationRange?.upper ?: 4
                        Column {
                            Text("Exposure Compensation (EV): ${settingsState.exposureCompensationIndex}", color = Color.White, fontSize = 14.sp)
                            Slider(
                                value = settingsState.exposureCompensationIndex.toFloat(),
                                onValueChange = { newEv ->
                                    onSettingsChanged(settingsState.copy(exposureCompensationIndex = newEv.toInt()))
                                },
                                valueRange = minEv.toFloat()..maxEv.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF59E0B),
                                    activeTrackColor = Color(0xFFF59E0B)
                                )
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal HUD Chips Bar
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. AF Lock Chip
            ChipItem(
                label = if (settingsState.isAfLocked) "AF LOCK" else "AF AUTO",
                isLocked = settingsState.isAfLocked,
                onClick = {
                    onSettingsChanged(settingsState.copy(isAfLocked = !settingsState.isAfLocked))
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 2. Flash / Torch Chip
            ChipItem(
                label = if (settingsState.isTorchEnabled) "FLASH ON" else "FLASH OFF",
                isLocked = settingsState.isTorchEnabled,
                onClick = {
                    onSettingsChanged(settingsState.copy(isTorchEnabled = !settingsState.isTorchEnabled))
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 3. AE Lock Chip
            if (capabilities?.isAeLockSupported == true) {
                ChipItem(
                    label = if (settingsState.isAeLocked) "AE LOCK" else "AE AUTO",
                    isLocked = settingsState.isAeLocked,
                    onClick = {
                        onSettingsChanged(settingsState.copy(isAeLocked = !settingsState.isAeLocked))
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 3. ISO Adjustment Chip
            if (capabilities?.isManualIsoSupported == true) {
                ChipItem(
                    label = if (settingsState.isManualIsoEnabled) "ISO ${settingsState.iso}" else "ISO AUTO",
                    isLocked = settingsState.isManualIsoEnabled,
                    onClick = {
                        activeSliderPanel = if (activeSliderPanel == SliderPanel.ISO) null else SliderPanel.ISO
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 4. EV (Exposure Compensation) Chip
            if (capabilities?.isExposureCompensationSupported == true) {
                ChipItem(
                    label = "EV ${settingsState.exposureCompensationIndex}",
                    isLocked = settingsState.exposureCompensationIndex != 0,
                    onClick = {
                        activeSliderPanel = if (activeSliderPanel == SliderPanel.EV) null else SliderPanel.EV
                    }
                )
            }
        }
    }
}

@Composable
private fun ChipItem(
    label: String,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isLocked) Color(0xFF0284C7).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f)
    val contentColor = if (isLocked) Color.White else Color(0xFFCBD5E1)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.height(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private enum class SliderPanel {
    ISO, EV
}

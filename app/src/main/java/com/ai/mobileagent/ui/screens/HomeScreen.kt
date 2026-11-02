package com.ai.mobileagent.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.mobileagent.data.AppPreferences
import com.ai.mobileagent.engine.AgentEngine
import com.ai.mobileagent.engine.AgentStatus
import com.ai.mobileagent.service.AgentAccessibilityService
import com.ai.mobileagent.service.OverlayService
import com.ai.mobileagent.ui.theme.PrimaryBlue
import com.ai.mobileagent.ui.theme.StopRed
import com.ai.mobileagent.ui.theme.SuccessGreen
import com.ai.mobileagent.ui.theme.TextDim

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val agentEngine = remember { AgentEngine.getInstance(context) }

    val agentStatus by agentEngine.status.collectAsState()
    val isAccessibilityActive by AgentAccessibilityService.isServiceActive.collectAsState()

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        )
    }

    var isOverlayRunning by remember { mutableStateOf(OverlayService.isRunning) }
    var userGoalInput by remember { mutableStateOf("") }

    val presetTasks = listOf(
        "Open YouTube and play lofi music",
        "Open Settings and check Battery",
        "Open Chrome and search AI news",
        "Open Calculator",
        "Open WhatsApp"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title
        Text(
            text = "Gemini Mobile Agent",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Permission Checklist Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "System Permissions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // 1. Accessibility Service Check
                PermissionRow(
                    title = "Accessibility Automation Service",
                    subtitle = if (isAccessibilityActive) "Active & Ready" else "Tap to enable in Android Settings",
                    isGranted = isAccessibilityActive,
                    actionLabel = "Enable",
                    onAction = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // 2. Draw Over Other Apps Check
                PermissionRow(
                    title = "Draw Over Other Apps (Overlay)",
                    subtitle = if (hasOverlayPermission) "Granted" else "Needed for floating STOP button",
                    isGranted = hasOverlayPermission,
                    actionLabel = "Grant",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // 3. Gemini API Key Check
                PermissionRow(
                    title = "Gemini API Key",
                    subtitle = if (prefs.isConfigured()) "Configured (${prefs.selectedModel})" else "Required - click to enter",
                    isGranted = prefs.isConfigured(),
                    actionLabel = "Setup",
                    onAction = onNavigateToSettings
                )
            }
        }

        // Floating Overlay Controller Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Floating Assistant Bubble",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Show draggable AI pill over other apps",
                        fontSize = 12.sp,
                        color = TextDim
                    )
                }
                Switch(
                    checked = isOverlayRunning,
                    onCheckedChange = { enable ->
                        if (enable) {
                            OverlayService.start(context)
                            isOverlayRunning = true
                        } else {
                            OverlayService.stop(context)
                            isOverlayRunning = false
                        }
                    },
                    enabled = hasOverlayPermission
                )
            }
        }

        // Live Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Agent Status",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                when (val st = agentStatus) {
                    is AgentStatus.Idle -> {
                        Text("Status: Idle (Ready for tasks)", color = TextDim, fontSize = 14.sp)
                    }
                    is AgentStatus.Running -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Step ${st.step}/${st.maxSteps}: ${st.currentThought}",
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }
                    is AgentStatus.Completed -> {
                        Text(
                            "Completed: ${st.message}",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    is AgentStatus.Failed -> {
                        Text(
                            "Failed: ${st.reason}",
                            color = StopRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    is AgentStatus.Stopped -> {
                        Text(
                            "Stopped by user",
                            color = StopRed,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Task Execution Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Give Phone Control Instruction",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = userGoalInput,
                    onValueChange = { userGoalInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Open YouTube and search for news") },
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Quick Presets:",
                    fontSize = 12.sp,
                    color = TextDim
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetTasks) { preset ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { userGoalInput = preset }
                        ) {
                            Text(
                                text = preset,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (userGoalInput.isNotBlank()) {
                                agentEngine.startTask(userGoalInput.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = userGoalInput.isNotBlank() && !agentEngine.isRunning(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Agent")
                    }

                    Button(
                        onClick = { agentEngine.stopTask() },
                        colors = ButtonDefaults.buttonColors(containerColor = StopRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("STOP")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) SuccessGreen else Color(0xFFF59E0B),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, fontSize = 12.sp, color = TextDim)
            }
        }

        if (!isGranted) {
            OutlinedButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        }
    }
}

package com.ai.mobileagent.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.mobileagent.data.AppPreferences
import com.ai.mobileagent.gemini.GeminiClient
import com.ai.mobileagent.ui.theme.PrimaryBlue
import com.ai.mobileagent.ui.theme.StopRed
import com.ai.mobileagent.ui.theme.SuccessGreen
import com.ai.mobileagent.ui.theme.TextDim
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val geminiClient = remember { GeminiClient() }

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(prefs.selectedModel) }
    var maxSteps by remember { mutableFloatStateOf(prefs.maxSteps.toFloat()) }
    var stepDelay by remember { mutableFloatStateOf((prefs.stepDelayMs / 100).toFloat()) }
    var useVision by remember { mutableStateOf(prefs.useMultimodalVision) }

    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AI Agent Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // API Key Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gemini API Key",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "Apni Gemini API key yahan enter karein. (Free key lene ke liye aistudio.google.com par jayein).",
                    fontSize = 13.sp,
                    color = TextDim
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        validationResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key (AIzaSy...)") },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isApiKeyVisible) "Hide Key" else "Show Key"
                            )
                        }
                    }
                )

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isValidating = true
                            validationResult = null
                            val res = geminiClient.validateApiKey(apiKey.trim(), selectedModel)
                            isValidating = false
                            if (res.isSuccess) {
                                prefs.geminiApiKey = apiKey.trim()
                                validationResult = Pair(true, "API Key is valid and saved securely!")
                            } else {
                                validationResult = Pair(false, res.exceptionOrNull()?.message ?: "Validation failed")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = apiKey.isNotBlank() && !isValidating
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Validating Key...")
                    } else {
                        Text("Save & Validate API Key")
                    }
                }

                validationResult?.let { (isSuccess, msg) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isSuccess) SuccessGreen else StopRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg,
                            color = if (isSuccess) SuccessGreen else StopRed,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Model Selection Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Gemini Model",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Fast models ensure sub-second UI control response.",
                    fontSize = 13.sp,
                    color = TextDim,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val models = listOf(
                    AppPreferences.MODEL_GEMINI_FLASH_LATEST to "Gemini Flash Latest (Fast & Reliable)",
                    AppPreferences.MODEL_GEMINI_3_8_FLASH to "Gemini 3.8 Flash (Latest Preview)",
                    AppPreferences.MODEL_GEMINI_PRO_LATEST to "Gemini Pro Latest"
                )

                models.forEach { (modelId, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedModel == modelId,
                            onClick = {
                                selectedModel = modelId
                                prefs.selectedModel = modelId
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, fontSize = 14.sp)
                    }
                }
            }
        }

        // Execution Tuning
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Automation Parameters",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Max Steps Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max Steps per Task:", fontSize = 14.sp)
                        Text("${maxSteps.toInt()} steps", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                    Slider(
                        value = maxSteps,
                        onValueChange = {
                            maxSteps = it
                            prefs.maxSteps = it.toInt()
                        },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                }

                // Step Delay Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("UI Transition Delay:", fontSize = 14.sp)
                        Text("${stepDelay.toInt() * 100} ms", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                    Slider(
                        value = stepDelay,
                        onValueChange = {
                            stepDelay = it
                            prefs.stepDelayMs = (it * 100).toLong()
                        },
                        valueRange = 5f..30f,
                        steps = 24
                    )
                }

                // Multimodal Vision Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = useVision,
                        onCheckedChange = {
                            useVision = it
                            prefs.useMultimodalVision = it
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Send Screenshots to Gemini (Vision Mode)", fontSize = 14.sp)
                        Text("Uses multimodal images alongside UI element tree (API 30+)", fontSize = 12.sp, color = TextDim)
                    }
                }
            }
        }
    }
}

package com.ai.mobileagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.ai.mobileagent.ui.screens.HomeScreen
import com.ai.mobileagent.ui.screens.LogsScreen
import com.ai.mobileagent.ui.screens.SettingsScreen
import com.ai.mobileagent.ui.theme.GeminiMobileAgentTheme

sealed class Screen(val title: String, val icon: ImageVector) {
    object Dashboard : Screen("Agent", Icons.Default.SmartToy)
    object Logs : Screen("Logs", Icons.Default.List)
    object Settings : Screen("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeminiMobileAgentTheme {
                MainAppScaffold()
            }
        }
    }
}

@Composable
fun MainAppScaffold() {
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val screens = listOf(Screen.Dashboard, Screen.Logs, Screen.Settings)
                screens.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (selectedScreen) {
                Screen.Dashboard -> HomeScreen(
                    onNavigateToSettings = { selectedScreen = Screen.Settings }
                )
                Screen.Logs -> LogsScreen()
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}

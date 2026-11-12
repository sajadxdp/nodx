package app.xero.xero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.xero.xero.data.ThemeSetting
import app.xero.xero.ui.chat.ChatScreen
import app.xero.xero.ui.models.ModelsScreen
import app.xero.xero.ui.sessions.SessionsScreen
import app.xero.xero.ui.settings.SettingsScreen
import app.xero.xero.ui.theme.XeroTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Live theme from DataStore, applied app-wide. */
    private val appearance = MutableStateFlow(ThemeSetting.DARK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as XeroApplication).container
        lifecycleScope.launch {
            container.settings.settings.collect { s ->
                appearance.value = s.theme
            }
        }

        setContent {
            val themeSetting by appearance.collectAsState()
            val dark = when (themeSetting) {
                ThemeSetting.LIGHT -> false
                ThemeSetting.DARK -> true
                ThemeSetting.SYSTEM -> isSystemInDarkTheme()
            }
            XeroTheme(darkTheme = dark) {
                XeroNav(container)
            }
        }
    }
}

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun XeroNav(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val destinations = listOf(
        Destination("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
        Destination("sessions", "Sessions", Icons.Filled.History),
        Destination("models", "Models", Icons.Filled.Download),
        Destination("settings", "Settings", Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(padding),
        ) {
            composable("chat") { ChatScreen(container) }
            composable("sessions") {
                SessionsScreen(
                    container = container,
                    onOpenSession = { id ->
                        container.openSessionRequest.value = id
                        navController.navigate("chat") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("models") { ModelsScreen(container) }
            composable("settings") { SettingsScreen(container) }
        }
    }
}

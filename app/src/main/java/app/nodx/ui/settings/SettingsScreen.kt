package app.nodx.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nodx.AppContainer
import app.nodx.data.EngineModeSetting
import app.nodx.data.Provider
import app.nodx.data.Settings
import app.nodx.data.SettingsStore
import app.nodx.data.ThemeSetting
import app.nodx.ui.vmFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val settings: StateFlow<Settings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(
        viewModelStoreOwner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        factory = vmFactory { SettingsViewModel(container.settings) },
    )
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val store = container.settings
    val theme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.surface,
                ),
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            SectionHeader("Appearance")
            var pendingTheme by remember(s.theme) { mutableStateOf(s.theme) }
            ListItem(
                headlineContent = { Text("Theme") },
                leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                        ThemeSetting.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = pendingTheme == mode,
                                onClick = {
                                    pendingTheme = mode
                                    scope.launch { store.setTheme(mode) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeSetting.entries.size),
                            ) { Text(mode.label) }
                        }
                    }
                },
            )

            SectionHeader("Engine")
            ListItem(
                headlineContent = { Text("Engine mode") },
                supportingContent = {
                    Text(
                        when (s.engineMode) {
                            EngineModeSetting.CLOUD ->
                                "Cloud — provider APIs. Local GGUF lands in Phase 3."
                            EngineModeSetting.LOCAL ->
                                "Local — on-device GGUF. Load a model in Models first."
                        }
                    )
                },
                leadingContent = { Icon(Icons.Filled.Memory, contentDescription = null) },
                trailingContent = {
                    SingleChoiceSegmentedButtonRow {
                        EngineModeSetting.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = s.engineMode == mode,
                                onClick = { scope.launch { store.setEngineMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index,
                                    EngineModeSetting.entries.size,
                                ),
                            ) { Text(mode.label) }
                        }
                    }
                },
            )

            SectionHeader("Cloud provider")
            var providerMenu by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(s.provider.label) },
                supportingContent = { Text("Endpoint: ${s.baseUrl.ifBlank { "(not set)" }}") },
                trailingContent = {
                    OutlinedButton(onClick = { providerMenu = true }) { Text("Change") }
                    DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        Provider.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label) },
                                onClick = {
                                    providerMenu = false
                                    scope.launch { store.setProvider(p) }
                                },
                            )
                        }
                    }
                },
            )

            if (s.provider == Provider.CUSTOM) {
                var baseUrl by remember(s.baseUrl) { mutableStateOf(s.baseUrl) }
                ListItem(
                    headlineContent = { Text("Base URL") },
                    supportingContent = {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            placeholder = { Text("https://host/v1") },
                            singleLine = true,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    },
                    trailingContent = {
                        Button(onClick = { scope.launch { store.setBaseUrl(baseUrl) } }) {
                            Text("Save")
                        }
                    },
                )
            }

            var apiKey by remember(s.apiKey) { mutableStateOf(s.apiKey) }
            ListItem(
                headlineContent = { Text("API key") },
                leadingContent = { Icon(Icons.Filled.Key, contentDescription = null) },
                supportingContent = {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text(if (s.apiKey.isBlank()) "Enter key" else "•••••••• (saved)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                },
                trailingContent = {
                    Button(onClick = { scope.launch { store.setApiKey(apiKey) } }) { Text("Save") }
                },
            )

            var model by remember(s.model) { mutableStateOf(s.model) }
            ListItem(
                headlineContent = { Text("Model") },
                supportingContent = {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = { Text(s.provider.defaultModel) },
                        singleLine = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                },
                trailingContent = {
                    Button(onClick = { scope.launch { store.setModel(model) } }) { Text("Save") }
                },
            )

            SectionHeader("Generation")
            var systemPrompt by remember(s.systemPrompt) { mutableStateOf(s.systemPrompt) }
            ListItem(
                headlineContent = { Text("System prompt") },
                supportingContent = {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        placeholder = { Text("Optional persona for the assistant") },
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                    )
                },
                trailingContent = {
                    Button(onClick = { scope.launch { store.setSystemPrompt(systemPrompt) } }) {
                        Text("Save")
                    }
                },
            )

            var temperature by remember(s.temperature) { mutableStateOf(s.temperature) }
            ListItem(
                headlineContent = { Text("Temperature (%.2f)".format(temperature)) },
                supportingContent = {
                    androidx.compose.material3.Slider(
                        value = temperature.toFloat(),
                        onValueChange = { temperature = it.toDouble() },
                        onValueChangeFinished = {
                            scope.launch { store.setTemperature(temperature) }
                        },
                        valueRange = 0f..2f,
                    )
                },
            )

            var maxTokens by remember(s.maxTokens) { mutableStateOf(s.maxTokens.toString()) }
            ListItem(
                headlineContent = { Text("Max tokens") },
                supportingContent = {
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it.filter { c -> c.isDigit() }.take(5) },
                        placeholder = { Text("1024") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                },
                trailingContent = {
                    Button(
                        onClick = {
                            scope.launch {
                                store.setMaxTokens(maxTokens.toIntOrNull() ?: 1024)
                            }
                        },
                    ) { Text("Save") }
                },
            )
        }
    }
}

private val ThemeSetting.label: String
    get() = when (this) {
        ThemeSetting.LIGHT -> "Light"
        ThemeSetting.DARK -> "Dark"
        ThemeSetting.SYSTEM -> "Auto"
    }

private val EngineModeSetting.label: String
    get() = when (this) {
        EngineModeSetting.LOCAL -> "Local"
        EngineModeSetting.CLOUD -> "Cloud"
    }

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

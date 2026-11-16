package app.nodx.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nodx.AppContainer
import app.nodx.R
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

/**
 * Settings, rebuilt on the opencode-mobile layout language: uppercase
 * section headers over grouped white cards, hairline dividers between
 * rows, and each row's icon inside a rounded tile.
 */
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
        containerColor = theme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.surface,
                    scrolledContainerColor = theme.surface,
                ),
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 24.dp),
        ) {
            SectionHeader("Appearance")
            SectionCard {
                var pendingTheme by remember(s.theme) { mutableStateOf(s.theme) }
                SettingRow(
                    icon = Icons.Filled.Palette,
                    title = "Theme",
                    supporting = {
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth(),
                        ) {
                            ThemeSetting.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = pendingTheme == mode,
                                    onClick = {
                                        pendingTheme = mode
                                        scope.launch { store.setTheme(mode) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index,
                                        ThemeSetting.entries.size,
                                    ),
                                ) { Text(mode.label) }
                            }
                        }
                    },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Filled.Tag,
                    title = "Dynamic color",
                    supporting = {
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                "Material You — derive colors from your wallpaper"
                            else
                                "Requires Android 12 or newer",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant,
                        )
                    },
                    onClick = { scope.launch { store.setDynamicColor(!s.dynamicColor) } },
                    trailing = {
                        Switch(
                            checked = s.dynamicColor,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onCheckedChange = { scope.launch { store.setDynamicColor(it) } },
                        )
                    },
                )
            }

            SectionHeader("Engine")
            SectionCard {
                SettingRow(
                    icon = Icons.Filled.Memory,
                    title = "Engine mode",
                    supporting = {
                        Column {
                            Text(
                                "Local — on-device GGUF · Cloud — provider APIs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.onSurfaceVariant,
                            )
                            SingleChoiceSegmentedButtonRow(
                                Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth(),
                            ) {
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
                        }
                    },
                )
            }

            SectionHeader("Cloud provider")
            SectionCard {
                var providerMenu by remember { mutableStateOf(false) }
                SettingRow(
                    title = s.provider.label,
                    supporting = {
                        Text(
                            "Endpoint: ${s.baseUrl.ifBlank { "(not set)" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        Button(onClick = { providerMenu = true }) { Text("Change") }
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
                    RowDivider()
                    var baseUrl by remember(s.baseUrl) { mutableStateOf(s.baseUrl) }
                    SettingRow(
                        title = "Base URL",
                        supporting = {
                            OutlinedTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                placeholder = { Text("https://host/v1") },
                                singleLine = true,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(),
                            )
                        },
                        trailing = {
                            Button(onClick = { scope.launch { store.setBaseUrl(baseUrl) } }) {
                                Text("Save")
                            }
                        },
                    )
                }
                RowDivider()
                var apiKey by remember(s.apiKey) { mutableStateOf(s.apiKey) }
                SettingRow(
                    icon = Icons.Filled.Key,
                    title = "API key",
                    supporting = {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = { Text(if (s.apiKey.isBlank()) "Enter key" else "•••••••• (saved)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                        )
                    },
                    trailing = {
                        Button(onClick = { scope.launch { store.setApiKey(apiKey) } }) { Text("Save") }
                    },
                )
                RowDivider()
                var model by remember(s.model) { mutableStateOf(s.model) }
                SettingRow(
                    title = "Model",
                    supporting = {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            placeholder = { Text(s.provider.defaultModel) },
                            singleLine = true,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                        )
                    },
                    trailing = {
                        Button(onClick = { scope.launch { store.setModel(model) } }) { Text("Save") }
                    },
                )
            }

            SectionHeader("Generation")
            SectionCard {
                var systemPrompt by remember(s.systemPrompt) { mutableStateOf(s.systemPrompt) }
                SettingRow(
                    title = "System prompt",
                    supporting = {
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            placeholder = { Text("Optional persona for the assistant") },
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                        )
                    },
                    trailing = {
                        Button(onClick = { scope.launch { store.setSystemPrompt(systemPrompt) } }) {
                            Text("Save")
                        }
                    },
                )
                RowDivider()
                var temperature by remember(s.temperature) { mutableStateOf(s.temperature) }
                SettingRow(
                    title = "Temperature (%.2f)".format(temperature),
                    supporting = {
                        androidx.compose.material3.Slider(
                            value = temperature.toFloat(),
                            onValueChange = { temperature = it.toDouble() },
                            onValueChangeFinished = {
                                scope.launch { store.setTemperature(temperature) }
                            },
                            valueRange = 0f..2f,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    },
                )
                RowDivider()
                var maxTokens by remember(s.maxTokens) { mutableStateOf(s.maxTokens.toString()) }
                SettingRow(
                    title = "Max tokens",
                    supporting = {
                        OutlinedTextField(
                            value = maxTokens,
                            onValueChange = { maxTokens = it.filter { c -> c.isDigit() }.take(5) },
                            placeholder = { Text("1024") },
                            singleLine = true,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                        )
                    },
                    trailing = {
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

            SectionHeader("About")
            SectionCard {
                val context = LocalContext.current
                val version = app.nodx.BuildConfig.VERSION_NAME
                SettingRow(
                    icon = null,
                    title = "nodx",
                    supporting = {
                        Text(
                            "Local-first AI chat · v$version",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant,
                        )
                    },
                    iconSlot = {
                        Icon(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Filled.Tag,
                    title = "Version",
                    supporting = {
                        Text(
                            "v$version (build ${app.nodx.BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant,
                        )
                    },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Filled.Person,
                    title = "Developer",
                    supporting = {
                        Text(
                            "sajad · sajadxdp",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant,
                        )
                    },
                )
                RowDivider()
                AboutLink(
                    label = "GitHub",
                    supporting = "github.com/sajadxdp/nodx",
                    url = "https://github.com/sajadxdp/nodx",
                    context = context,
                )
                RowDivider()
                AboutLink(
                    label = "Report an issue",
                    supporting = "github.com/sajadxdp/nodx/issues",
                    url = "https://github.com/sajadxdp/nodx/issues",
                    context = context,
                )
            }
        }
    }
}

/** A settings row that opens a URL in the browser. */
@Composable
private fun AboutLink(
    label: String,
    supporting: String,
    url: String,
    context: android.content.Context,
) {
    val theme = MaterialTheme.colorScheme
    SettingRow(
        icon = Icons.Filled.Code,
        title = label,
        supporting = {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurfaceVariant,
            )
        },
        onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        trailing = {
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = null,
                tint = theme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** The grouped card: flat surface, hairline border, rounded corners. */
@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    val theme = MaterialTheme.colorScheme
    Surface(
        color = theme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(content = content)
    }
}

/** One setting row: icon tile · title (+ supporting content) · trailing. */
@Composable
private fun SettingRow(
    title: String,
    icon: ImageVector? = null,
    iconSlot: (@Composable () -> Unit)? = null,
    supporting: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconSlot != null) {
            iconSlot()
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(theme.surfaceContainerHighest, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            supporting?.invoke(this)
        }
        trailing?.invoke(this)
    }
}

/** Hairline divider inset past the icon tile, like iOS grouped lists. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 60.dp),
    )
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

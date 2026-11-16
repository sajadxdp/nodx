package app.nodx.ui.models

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Badge

import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nodx.AppContainer
import app.nodx.data.CatalogModel
import app.nodx.data.DownloadState
import app.nodx.data.DownloadedModel
import app.nodx.ui.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(container: AppContainer) {
    val vm: ModelsViewModel = viewModel(
        viewModelStoreOwner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        factory = vmFactory { ModelsViewModel(container) },
    )
    val state by vm.state.collectAsState()
    val theme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = theme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.surface,
                    scrolledContainerColor = theme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp,
            ),
        ) {
            if (state.deviceInfo.isNotBlank()) {
                item {
                    Text(
                        "Your device: ${state.deviceInfo} — models up to " +
                            "%.1f GB".format(container.deviceProfile.maxModelGb),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let { err ->
                item {
                    Surface(
                        color = theme.error.copy(alpha = 0.10f),
                        contentColor = theme.error,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.error.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("⚠ $err", modifier = Modifier.padding(12.dp))
                    }
                }
            }

            if (state.downloaded.isNotEmpty()) {
                item { SectionHeader("On this device") }
                items(state.downloaded, key = { "dl-" + it.id }) { model ->
                    DownloadedModelCard(
                        model = model,
                        isLoaded = state.loadedPath?.endsWith(model.filename) == true,
                        isLoading = state.isLoadingModel,
                        onLoad = { vm.toggleLoad(model) },
                        onDelete = { vm.delete(model) },
                    )
                }
            }

            item { SectionHeader("Catalog") }
            items(
                state.catalog.filter { cat -> state.downloaded.none { it.id == cat.id } },
                key = { "cat-" + it.id },
            ) { model ->
                CatalogModelCard(
                    model = model,
                    state = state.downloads[model.id] ?: DownloadState.Idle,
                    onDownload = { vm.download(model) },
                    onPause = { vm.pause(model.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Flat card shell shared by every model row: rounded, hairline border,
 * no elevation — the opencode-mobile card look. */
@Composable
private fun ModelCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val theme = MaterialTheme.colorScheme
    Surface(
        color = theme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column(content = content)
    }
}

@Composable
private fun DownloadedModelCard(
    model: DownloadedModel,
    isLoaded: Boolean,
    isLoading: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    ModelCard {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(theme.surfaceContainerHighest, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Memory,
                    contentDescription = null,
                    tint = if (isLoaded) theme.primary else theme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.filename,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "${"%.1f".format(model.sizeBytes / 1e9)} GB" +
                        (model.contextLength?.let { " · ctx $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant,
                )
            }
            if (isLoading && !isLoaded) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = onLoad) {
                    Text(if (isLoaded) "Unload" else "Load")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete model",
                    tint = theme.onSurfaceVariant,
                )
            }
        }
        if (isLoaded) {
            Text(
                "Loaded — chat now uses this model locally",
                style = MaterialTheme.typography.bodySmall,
                color = theme.primary,
                modifier = Modifier.padding(start = 60.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun CatalogModelCard(
    model: CatalogModel,
    state: DownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    ModelCard {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.onSurfaceVariant,
                    )
                }
                when (state) {
                    is DownloadState.Downloading -> IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }

                    is DownloadState.Paused -> IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                    }

                    else -> Button(onClick = onDownload, enabled = state !is DownloadState.Done) {
                        if (state is DownloadState.Failed) Text("Retry")
                        else Icon(Icons.Filled.Download, contentDescription = "Download")
                    }
                }
            }
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Badge(model.quant)
                Badge("%.1f GB".format(model.sizeBytes / 1e9))
                Badge("≥ ${model.minRamGb} GB RAM")
            }
            when (state) {
                is DownloadState.Downloading -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${"%.0f".format(state.bytesPerSecond / 1e6)} MB/s" +
                            (if (state.etaSeconds > 0) " · ${state.etaSeconds}s left" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }

                is DownloadState.Paused -> Text(
                    "Paused — ${"%.1f".format(state.downloadedBytes / 1e9)} GB so far",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )

                is DownloadState.Failed -> Text(
                    "⚠ ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )

                else -> Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Non-interactive spec tag (quant, size, RAM floor): bordered, quiet.
 * No tap target — these carry no action. */
@Composable
private fun Badge(text: String) {
    val theme = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = theme.onSurfaceVariant,
        modifier = Modifier
            .background(theme.surfaceContainerHigh, RoundedCornerShape(7.dp))
            .border(1.dp, theme.outlineVariant, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

package app.nodx.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nodx.AppContainer
import app.nodx.data.ChatRepository
import app.nodx.domain.ChatSession
import app.nodx.ui.vmFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionsViewModel(private val repo: ChatRepository) : ViewModel() {
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    /** Session pending deletion confirmation. */
    val pendingDelete = MutableStateFlow<ChatSession?>(null)

    /** Session pending rename. */
    val pendingRename = MutableStateFlow<ChatSession?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _sessions.value = repo.sessions() }
    }

    fun deleteConfirmed() {
        val s = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            repo.deleteSession(s.id)
            refresh()
        }
    }

    fun renameConfirmed(title: String) {
        val s = pendingRename.value ?: return
        pendingRename.value = null
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.renameSession(s.id, title.trim())
            refresh()
        }
    }
}

private fun relativeTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    val mins = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date(epochMs))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    container: AppContainer,
    onOpenSession: (String) -> Unit,
) {
    val vm: SessionsViewModel = viewModel(
        viewModelStoreOwner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        factory = vmFactory { SessionsViewModel(container.chatRepository) },
    )
    val sessions by vm.sessions.collectAsState()
    val pendingDelete by vm.pendingDelete.collectAsState()
    val pendingRename by vm.pendingRename.collectAsState()
    val theme = MaterialTheme.colorScheme

    pendingDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { vm.pendingDelete.value = null },
            title = { Text("Delete session?") },
            text = { Text("\u201C${doomed.title}\u201D and its messages will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = vm::deleteConfirmed) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { vm.pendingDelete.value = null }) { Text("Cancel") }
            },
        )
    }

    pendingRename?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { vm.pendingRename.value = null },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.renameConfirmed(name) }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { vm.pendingRename.value = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.surface,
                ),
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    "No sessions yet",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Chats are saved to the on-device database.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(sessions, key = { it.id }) { session ->
                    var menuOpen by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(session.title, maxLines = 1) },
                        supportingContent = { Text(relativeTime(session.updatedAt)) },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        },
                        trailingContent = {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Session menu")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.pendingRename.value = session
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        vm.pendingDelete.value = session
                                    },
                                )
                            }
                        },
                        modifier = Modifier.clickable { onOpenSession(session.id) },
                    )
                }
            }
        }
    }
}

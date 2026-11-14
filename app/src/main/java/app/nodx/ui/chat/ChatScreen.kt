package app.nodx.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nodx.AppContainer
import app.nodx.R
import app.nodx.domain.ChatMessage
import app.nodx.domain.MessageRole
import app.nodx.ui.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(container: AppContainer) {
    val vm: ChatViewModel = viewModel(
        viewModelStoreOwner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
        factory = vmFactory {
            ChatViewModel(
                container = container,
                repo = container.chatRepository,
                settings = container.settings,
            )
        },
    )
    val state by vm.state.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(container) {
        container.openSessionRequest.collect { id ->
            if (id != null) {
                vm.openSession(id)
                container.openSessionRequest.value = null
            }
        }
    }

    // Auto-scroll to the newest content while generating or appending.
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1)
                        state.stats?.let { stats ->
                            val label = if (state.engineLabel.isNotBlank()) "${state.engineLabel} · " else ""
                            Text(
                                "$label${"%.0f".format(stats.tokensPerSecond)} tok/s · ${stats.contextUsed}/${stats.contextTotal} ctx",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    AnimatedVisibility(
                        visible = state.isGenerating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        TextButton(onClick = vm::stop) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Text("Stop")
                        }
                    }
                    AnimatedVisibility(
                        visible = !state.isGenerating && state.messages.size > 1,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        IconButton(onClick = vm::regenerate) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Regenerate")
                        }
                    }
                    IconButton(onClick = vm::newSession) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.messages.isEmpty()) {
                EmptyChat(Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        ChatBubble(message, onRetry = vm::regenerate)
                    }
                }
            }

            Composer(
                value = text,
                onValueChange = { text = it },
                enabled = !state.isGenerating,
                onSend = {
                    vm.send(text)
                    text = ""
                },
            )
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.nodx_logo),
            contentDescription = "nodx",
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialTheme.shapes.extraLarge),
        )
        Text(
            "Your models, your device",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            "Stream from a cloud provider,\nor run local GGUF models on-device.",
            style = MaterialTheme.typography.bodyMedium,
            color = theme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(message: ChatMessage, onRetry: () -> Unit = {}) {
    val theme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == MessageRole.USER
    val hasError = message.error != null

    val container = when {
        hasError -> theme.errorContainer
        isUser -> theme.primaryContainer
        else -> theme.surfaceContainerHigh
    }
    val content = when {
        hasError -> theme.onErrorContainer
        isUser -> theme.onPrimaryContainer
        else -> theme.onSurface
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = container,
            contentColor = content,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
            ),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 4.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { clipboard?.setText(AnnotatedString(message.content)) },
                ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (hasError) {
                    Text("⚠ ${message.error}", style = MaterialTheme.typography.bodyMedium)
                    if (!isUser) {
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                } else if (message.content.isEmpty() && !isUser) {
                    Text("…", style = MaterialTheme.typography.bodyLarge)
                } else if (isUser) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(message.content, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Column {
                            parseSegments(message.content).forEach { segment ->
                                when (segment) {
                                    is Segment.Rich -> Text(
                                        segment.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    is Segment.Code -> Surface(
                                        color = theme.surface.copy(alpha = 0.6f),
                                        contentColor = content,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    ) {
                                        androidx.compose.foundation.text.selection.SelectionContainer {
                                            Text(
                                                segment.text,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                                modifier = Modifier.padding(8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message…") },
            shape = RoundedCornerShape(24.dp),
            minLines = 1,
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        val canSend = enabled && value.isNotBlank()
        FilledIconButton(
            onClick = { if (canSend) onSend() },
            enabled = canSend,
            modifier = Modifier.padding(start = 8.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = "Send",
            )
        }
    }
}

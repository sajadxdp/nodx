package app.nodx.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.nodx.AppContainer
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
        // A session deleted in the Sessions tab must not keep its transcript
        // on this screen — sending from it would write orphan messages.
        vm.dropIfSessionGone()
        container.openSessionRequest.collect { id ->
            if (id != null) {
                vm.openSession(id)
                container.openSessionRequest.value = null
            }
        }
    }

    // Claude-style body switch (greeting → conversation) re-focus handling.
    val conversationFocus = remember { FocusRequester() }
    var focusConversation by remember { mutableStateOf(false) }

    // Keep the view pinned to the newest content while streaming — but only
    // when the user is already at the bottom, so scrolling up during a long
    // reply isn't fought by the auto-scroll. Jump (not animate) so per-token
    // recompositions don't restart animations.
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty() && !listState.canScrollForward) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
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
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
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
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            // Claude-style body: a centered greeting + input on an empty
            // chat; the same input slides to the bottom once conversation
            // starts. State (draft text) is hoisted so both branches share
            // the field seamlessly.
            AnimatedContent(
                targetState = state.messages.isEmpty(),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(260)) togetherWith
                        fadeOut(animationSpec = tween(200)))
                },
                label = "chatBody",
            ) { empty ->
                if (empty) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Greeting()
                        Spacer(Modifier.size(28.dp))
                        Composer(
                            value = text,
                            onValueChange = { text = it },
                            canSend = !state.isGenerating,
                            onSend = {
                                if (state.messages.isEmpty()) focusConversation = true
                                vm.send(text)
                                text = ""
                            },
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
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
                            itemsIndexed(state.messages, key = { _, m -> m.id }) { index, message ->
                                ChatBubble(
                                    message = message,
                                    // Retry belongs to the last bubble only —
                                    // regenerate() drops the last assistant message,
                                    // so a Retry on an older error bubble would
                                    // delete the wrong one.
                                    canRetry = message.error != null &&
                                        index == state.messages.lastIndex,
                                    onRetry = vm::regenerate,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        Composer(
                            value = text,
                            onValueChange = { text = it },
                            // Keep the field draftable while a reply streams; only the
                            // send button gates on generation state.
                            canSend = !state.isGenerating,
                            onSend = {
                                vm.send(text)
                                text = ""
                            },
                            focusRequester = conversationFocus,
                        )
                    }
                }
            }
        }
    }

    // Keep the keyboard open across the greeting→conversation transition:
    // the first send swaps the composer node, so re-focus the new one.
    LaunchedEffect(focusConversation, state.messages.isEmpty()) {
        if (focusConversation && state.messages.isNotEmpty()) {
            withFrameNanos { }
            conversationFocus.requestFocus()
            focusConversation = false
        }
    }
}

/** Time-of-day greeting, Claude-app style — no logo, no marketing copy. */
private fun greetingForNow(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5 until 12 -> "Good morning"
        in 12 until 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun Greeting() {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(animationSpec = tween(450)) +
            slideInVertically(animationSpec = tween(450)) { it / 10 },
    ) {
        Text(
            greetingForNow(),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    canRetry: Boolean = true,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
        Modifier.fillMaxWidth().then(modifier),
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
                    if (!isUser && canRetry) {
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                } else if (message.content.isEmpty() && !isUser) {
                    TypingDots(color = content)
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
                                        color = theme.surfaceContainerHighest,
                                        contentColor = content,
                                        shape = MaterialTheme.shapes.small,
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

/** Claude-style pulsing typing indicator for a reply that hasn't
 * produced text yet (live bubble or a placeholder). */
@Composable
private fun TypingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(550),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 180),
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    focusRequester: FocusRequester? = null,
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
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            placeholder = { Text("Message…") },
            shape = MaterialTheme.shapes.extraLarge,
            minLines = 1,
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        val sendable = canSend && value.isNotBlank()
        FilledIconButton(
            onClick = { if (sendable) onSend() },
            enabled = sendable,
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

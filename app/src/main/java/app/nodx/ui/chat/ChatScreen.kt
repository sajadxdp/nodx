package app.nodx.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
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

    // A composer draft belongs to the chat it was typed in — starting a new
    // chat (or opening an old one) starts with a clean field.
    LaunchedEffect(state.sessionId) { text = "" }

    // Keep the view pinned to the newest content while streaming. Following
    // the reply is the default; a user drag toward earlier content pauses it,
    // and settling back at the bottom resumes it. A canScrollForward-style
    // guard can't express this: once a reply outgrows the viewport the list
    // sits short of the end, the guard goes false, and pinning silently dies
    // mid-stream (seen on a real phone — the tail of the reply hid behind
    // the composer). User intent is read through a nestedScroll connection,
    // which programmatic jumps never pass through, so they can't fight the
    // user's hand. Jumps land on the true end (scrollBy consumes the
    // remainder scrollToItem leaves).
    val atBottom = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || (last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 1)
        }
    }
    val followBottom = remember { mutableStateOf(true) }
    val followInput = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y < 0f) {
                    followBottom.value = false
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                followBottom.value = atBottom.value
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty() && followBottom.value && !listState.isScrollInProgress) {
            listState.scrollToItem(state.messages.lastIndex)
            listState.scrollBy(1e9f)
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
            // Empty-chat body, built on the "shark on vacation" motif: sea
            // creatures drift across the screen while a centered greeting
            // and the input sit slightly above center — anchored as one
            // group instead of floating dead-center. The input settles to
            // the bottom once the conversation starts. Draft text is
            // hoisted so both branches share the field seamlessly.
            AnimatedContent(
                targetState = state.messages.isEmpty(),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(260)) togetherWith
                        fadeOut(animationSpec = tween(200)))
                },
                label = "chatBody",
            ) { empty ->
                if (empty) {
                    Box(Modifier.fillMaxSize()) {
                        SwimmingSea(Modifier.matchParentSize())
                        Column(
                            Modifier
                                .align(BiasAlignment(0f, -0.32f))
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Greeting()
                            Spacer(Modifier.size(20.dp))
                            Composer(
                                value = text,
                                onValueChange = { text = it },
                                canSend = !state.isGenerating,
                                onSend = {
                                    if (state.messages.isEmpty()) focusConversation = true
                                    vm.send(text)
                                    text = ""
                                },
                                hint = "What's on your mind?",
                            )
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .nestedScroll(followInput),
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

/**
 * The "shark on vacation" scene, tuned for nodx: a few ASCII sea
 * creatures and hearts drift across the empty chat, in brand colors at
 * low opacity so the effect reads as ambience, not decoration. Each
 * creature animates its own horizontal drift plus a gentle vertical
 * bob; reads are deferred into the offset lambda so the per-frame
 * motion never recomposes the tree.
 */
@Composable
private fun SwimmingSea(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        Creature(
            "><(((º>", h, w,
            yFrac = 0.16f, durMs = 15000, delayMs = 0,
            color = SeaTeal, alpha = 0.34f,
        )
        Creature(
            "<3", h, w,
            yFrac = 0.28f, durMs = 19000, delayMs = 6000,
            color = SeaViolet, alpha = 0.26f, reverse = true,
        )
        Creature(
            "><(((º>", h, w,
            yFrac = 0.46f, durMs = 17000, delayMs = 9000,
            color = SeaViolet, alpha = 0.28f, reverse = true,
        )
        Creature(
            "♡", h, w,
            yFrac = 0.62f, durMs = 14000, delayMs = 3000,
            color = SeaTeal, alpha = 0.28f,
        )
        Creature(
            "><(((º>", h, w,
            yFrac = 0.80f, durMs = 13000, delayMs = 11000,
            color = SeaViolet, alpha = 0.20f,
        )
    }
}

private val SeaTeal = Color(0xFF2FB8A6)   // the post's turquoise, muted for nodx
private val SeaViolet = Color(0xFF883DEA) // brand accent

@Composable
private fun Creature(
    text: String,
    maxHeight: Dp,
    maxWidth: Dp,
    yFrac: Float,
    durMs: Int,
    delayMs: Int,
    color: Color,
    alpha: Float,
    reverse: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "sea")
    // Horizontal position: a 0..1 loop across the width (plus bleed at
    // both ends so creatures enter and leave the screen).
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durMs, easing = LinearEasing),
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "drift",
    )
    // Vertical bob: a full sine cycle inside the drift.
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durMs / 3, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "bob",
    )
    // Gentle alpha breathing so creatures feel alive, not pasted on.
    val glow by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durMs / 4, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "glow",
    )
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        ),
        color = color.copy(alpha = alpha * glow),
        modifier = Modifier.offset {
            val x = if (reverse) (1.12f - drift) else (drift - 0.12f)
            val y = yFrac + sin(bob * 2f * PI.toFloat()) * 0.012f
            IntOffset(
                (x * maxWidth.toPx()).toInt(),
                (y * maxHeight.toPx()).toInt(),
            )
        },
    )
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    focusRequester: FocusRequester? = null,
    hint: String = "Message…",
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
            placeholder = { Text(hint) },
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

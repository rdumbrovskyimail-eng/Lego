@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.legode.friedrich.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.core.Timex
import com.legode.friedrich.core.int
import com.legode.friedrich.core.parseObjectOrNull
import com.legode.friedrich.core.parseStringList
import com.legode.friedrich.core.str
import com.legode.friedrich.data.Attachment
import com.legode.friedrich.data.AttachmentStore
import com.legode.friedrich.data.Repository
import com.legode.friedrich.data.db.MessageEntity
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.ConfirmDialog
import com.legode.friedrich.ui.common.EmojiAvatar
import com.legode.friedrich.ui.common.FAvatar
import com.legode.friedrich.ui.common.InputDialog
import com.legode.friedrich.ui.common.MarkdownText
import com.legode.friedrich.ui.theme.Fx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed class ChatRow {
    abstract val key: String

    data class Day(val label: String, override val key: String) : ChatRow()
    data class Msg(val m: MessageEntity) : ChatRow() {
        override val key: String get() = "m" + m.id
    }
}

private val UMLAUTS = listOf("ä", "ö", "ü", "ß", "Ä", "Ö", "Ü")

@Composable
fun ChatScreen(sessionId: Long, main: MainViewModel, onBack: () -> Unit) {
    val vm: ChatViewModel = viewModel(key = "chat-$sessionId")
    val session by vm.session.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val typing by vm.typing.collectAsStateWithLifecycle()
    val settings by vm.settings.flow.collectAsStateWithLifecycle()
    val playing by vm.player.playing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var input by remember(sessionId) { mutableStateOf(TextFieldValue("")) }
    var draftLoaded by remember(sessionId) { mutableStateOf(false) }
    val latestInput by rememberUpdatedState(input)

    var menuOpen by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var listDialog by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var actionFor by remember { mutableStateOf<MessageEntity?>(null) }
    var selectFor by remember { mutableStateOf<MessageEntity?>(null) }
    var viewImage by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    // ── привязка к сессии, видимость, черновик ──
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sessionId) {
        vm.bind(sessionId)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onVisible(true)
                Lifecycle.Event.ON_PAUSE -> {
                    vm.onVisible(false)
                    vm.saveDraft(latestInput.text)
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) vm.onVisible(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onVisible(false)
            vm.saveDraft(latestInput.text)
            vm.finishRecording(false)
            vm.player.stop()
        }
    }

    LaunchedEffect(session?.id) {
        val s = session
        if (s != null && !draftLoaded) {
            draftLoaded = true
            if (s.draft.isNotEmpty() && input.text.isEmpty()) {
                input = TextFieldValue(s.draft, TextRange(s.draft.length))
            }
        }
    }

    // ── «Поделиться» из другого приложения ──
    LaunchedEffect(main.shared) {
        val p = main.shared
        if (p != null) {
            main.shared = null
            if (p.text.isNotBlank()) input = TextFieldValue(p.text, TextRange(p.text.length))
            if (p.uris.isNotEmpty()) vm.addUris(p.uris)
        }
    }

    LaunchedEffect(vm) {
        vm.toasts.collectLatest { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    }

    // ── выбор файлов, камера, микрофон ──
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        vm.addUris(uris)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addUris(uris)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        cameraFile = null
        if (f != null) {
            if (ok) vm.addCameraFile(f) else f.delete()
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
        else Toast.makeText(context, "Без доступа к микрофону голосовые не записать", Toast.LENGTH_LONG).show()
    }

    // ── строки ленты ──
    val rows = remember(messages) {
        val out = ArrayList<ChatRow>(messages.size + 8)
        var prevDay = ""
        for (m in messages) {
            val day = Timex.dayOf(m.createdAt)
            if (day != prevDay) {
                out.add(ChatRow.Day(Timex.humanDay(m.createdAt), "d$day"))
                prevDay = day
            }
            out.add(ChatRow.Msg(m))
        }
        out.reversed()
    }
    val lastMessage = messages.lastOrNull()
    val quick = remember(lastMessage) {
        if (lastMessage != null && lastMessage.role == Repository.ROLE_FRIEDRICH) parseStringList(lastMessage.quickReplies)
        else emptyList()
    }
    val lastFriedrichId = remember(messages) {
        messages.lastOrNull { it.role == Repository.ROLE_FRIEDRICH }?.id ?: 0L
    }
    val canRegenerate = lastMessage != null && lastMessage.role == Repository.ROLE_FRIEDRICH && !typing &&
        messages.any { it.role == Repository.ROLE_USER }

    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    LaunchedEffect(messages.size, typing) {
        if (listState.firstVisibleItemIndex <= 3 || lastMessage?.role == Repository.ROLE_USER) {
            listState.animateScrollToItem(0)
        }
    }

    fun insert(s: String) {
        val sel = input.selection
        val text = input.text.replaceRange(sel.min, sel.max, s)
        input = TextFieldValue(text, TextRange(sel.min + s.length))
    }

    fun sendNow() {
        if (input.text.isBlank() && vm.pending.isEmpty()) return
        vm.send(input.text)
        input = TextFieldValue("")
    }

    val bubbleMax = (LocalConfiguration.current.screenWidthDp * 0.86f).dp

    Column(
        Modifier
            .fillMaxSize()
            .background(Fx.Ink)
            .imePadding()
    ) {
        // ───────── шапка ─────────
        Row(
            Modifier
                .fillMaxWidth()
                .background(Fx.Panel)
                .statusBarsPadding()
                .heightIn(min = 58.dp)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Fx.Text)
            }
            val s = session
            if (s == null || s.isHome) FAvatar(38.dp) else EmojiAvatar(s.emoji, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (s == null || s.isHome) "Friedrich" else s.title,
                    color = Fx.Text, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub = when {
                    typing -> "печатает…"
                    s == null -> ""
                    s.listId != 0L -> "📋 отработка списка"
                    s.mode != Skills.FREE -> Skills.label(s.mode, s.modeArg)
                    else -> "наставник немецкого · A0–A1"
                }
                Text(
                    sub, color = if (typing) Fx.Gold else Fx.TextDim,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Меню", tint = Fx.Text)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Новый чат") }, onClick = {
                        menuOpen = false
                        main.newSession(Skills.FREE)
                    })
                    if (canRegenerate) {
                        DropdownMenuItem(text = { Text("Переспросить (другой ответ)") }, onClick = {
                            menuOpen = false
                            vm.regenerate()
                        })
                    }
                    if (session?.isHome == false) {
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = {
                            menuOpen = false
                            renameOpen = true
                        })
                    }
                    if ((session?.listId ?: 0L) != 0L) {
                        DropdownMenuItem(text = { Text("Остановить отработку списка") }, onClick = {
                            menuOpen = false
                            vm.detachList()
                        })
                    }
                    DropdownMenuItem(text = { Text("Экспорт / поделиться") }, onClick = {
                        menuOpen = false
                        scope.launch {
                            val text = vm.exportText()
                            if (text.isNotBlank()) {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                try {
                                    context.startActivity(Intent.createChooser(send, "Экспорт чата"))
                                } catch (_: Throwable) {
                                }
                            }
                        }
                    })
                    DropdownMenuItem(text = { Text("Справочник A0–A1") }, onClick = {
                        menuOpen = false
                        main.open(Screen.Reference(null))
                    })
                    DropdownMenuItem(text = { Text("Досье") }, onClick = {
                        menuOpen = false
                        main.open(Screen.Dossier)
                    })
                    DropdownMenuItem(text = { Text("Очистить историю") }, onClick = {
                        menuOpen = false
                        clearConfirm = true
                    })
                    if (session?.isHome == false) {
                        DropdownMenuItem(text = { Text("Удалить чат", color = Fx.Red) }, onClick = {
                            menuOpen = false
                            deleteConfirm = true
                        })
                    }
                }
            }
        }

        // ───────── лента ─────────
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (typing) {
                    item(key = "typing") { TypingBubble() }
                }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is ChatRow.Day -> DayChip(row.label)
                        is ChatRow.Msg -> MessageRow(
                            m = row.m,
                            maxWidth = bubbleMax,
                            playingPath = playing,
                            onLongPress = { actionFor = row.m },
                            onRetry = { vm.retry(row.m.id) },
                            onImage = { viewImage = it },
                            onPlay = { vm.player.toggle(it) }
                        )
                    }
                }
            }
            if (messages.isEmpty() && !typing) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FAvatar(64.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Напиши Фридриху — по-русски или по-немецки.\nМожно прислать список слов, фото учебника или голосовое.",
                        color = Fx.TextDim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (showJump) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Fx.CardHigh)
                        .clickable { scope.launch { listState.animateScrollToItem(0) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз", tint = Fx.Text)
                }
            }
        }

        // ───────── ввод ─────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(Fx.Panel)
                .navigationBarsPadding()
        ) {
            if (quick.isNotEmpty() && !typing) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quick) { q -> Chip(q, onClick = { vm.send(q) }, accent = true) }
                }
            }
            if (vm.pending.isNotEmpty() || vm.importing) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(vm.pending.toList(), key = { it.path }) { a ->
                        PendingAttachment(a) { vm.removePending(a) }
                    }
                    if (vm.importing) {
                        item { CircularProgressIndicator(Modifier.size(22.dp), color = Fx.Gold, strokeWidth = 2.dp) }
                    }
                }
            }
            if (!vm.recording) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(UMLAUTS) { u ->
                        Box(
                            Modifier
                                .size(width = 34.dp, height = 32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Fx.CardHigh)
                                .clickable { insert(u) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(u, color = Fx.Gold, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                    items(Skills.quickCommands) { c -> Chip(c.label, onClick = { vm.send(c.text) }) }
                }
            }

            if (vm.recording) {
                var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        tick = System.currentTimeMillis()
                        delay(250)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Fx.Red)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Запись " + Timex.duration(tick - vm.recordStartedAt) + " · говори по-немецки",
                        color = Fx.Text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { vm.finishRecording(false) }) { Text("Отмена", color = Fx.TextDim) }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Fx.Gold)
                            .clickable { vm.finishRecording(true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Fx.OnGold)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(onClick = { attachOpen = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Прикрепить", tint = Fx.TextDim)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Fx.Ink)
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        if (input.text.isEmpty()) {
                            Text("Сообщение…", color = Fx.TextFaint, style = MaterialTheme.typography.bodyLarge)
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Fx.Text),
                            cursorBrush = SolidColor(Fx.Gold),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val hasContent = input.text.isNotBlank() || vm.pending.isNotEmpty()
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (hasContent) Fx.Gold else Fx.CardHigh)
                            .clickable {
                                if (hasContent) {
                                    sendNow()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) vm.startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasContent) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Fx.OnGold)
                        } else {
                            Icon(Icons.Filled.Mic, contentDescription = "Голосовое", tint = Fx.Text)
                        }
                    }
                }
            }
        }
    }

    // ───────── листы и диалоги ─────────

    if (attachOpen) {
        ModalBottomSheet(
            onDismissRequest = { attachOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Fx.Card
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                SheetAction(Icons.Filled.Image, "Фото из галереи", "страница учебника, упражнение, вывеска") {
                    attachOpen = false
                    try {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (_: Throwable) {
                    }
                }
                SheetAction(Icons.Filled.PhotoCamera, "Камера", "сфотографировать и спросить") {
                    attachOpen = false
                    try {
                        val target = vm.newCameraTarget()
                        cameraFile = target.first
                        takePhoto.launch(target.second)
                    } catch (_: Throwable) {
                        Toast.makeText(context, "Камера недоступна", Toast.LENGTH_SHORT).show()
                    }
                }
                SheetAction(Icons.Filled.Description, "Файл", "PDF, текст, DOCX, аудио") {
                    attachOpen = false
                    try {
                        pickFiles.launch(
                            arrayOf(
                                "application/pdf", "text/*", "audio/*", "image/*", "application/json",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                        )
                    } catch (_: Throwable) {
                    }
                }
                SheetAction(Icons.Filled.TextFields, "Список слов или фраз", "вставить текстом — Friedrich сохранит и отработает") {
                    attachOpen = false
                    listDialog = true
                }
            }
        }
    }

    if (listDialog) {
        InputDialog(
            title = "Список для отработки",
            hint = "Каждое слово или предложение — с новой строки. Можно с переводом: «der Tisch — стол». Чего не хватает, Friedrich допишет сам.",
            multiline = true,
            confirm = "Отправить",
            onConfirm = { text ->
                vm.send("📋 Вот мой список для отработки. Сохрани его и давай тренировать, пока не выучу:\n$text")
            },
            onDismiss = { listDialog = false }
        )
    }

    actionFor?.let { m ->
        ModalBottomSheet(
            onDismissRequest = { actionFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Fx.Card
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                if (m.text.isNotBlank()) {
                    SheetAction(Icons.Filled.ContentCopy, "Копировать", "") {
                        clipboard.setText(AnnotatedString(Norm.plain(m.text)))
                        actionFor = null
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    }
                    SheetAction(Icons.Filled.VolumeUp, "Озвучить немецкий", "") {
                        vm.speak(m.text)
                        actionFor = null
                    }
                    SheetAction(Icons.Filled.TextFields, "Выделить текст", "") {
                        selectFor = m
                        actionFor = null
                    }
                }
                SheetAction(
                    if (m.starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    if (m.starred) "Убрать из избранного" else "В избранное", ""
                ) {
                    vm.toggleStar(m)
                    actionFor = null
                }
                if (m.role == Repository.ROLE_FRIEDRICH && m.id == lastFriedrichId && canRegenerate) {
                    SheetAction(Icons.Filled.Refresh, "Переспросить (другой ответ)", "") {
                        vm.regenerate()
                        actionFor = null
                    }
                }
                if (m.role == Repository.ROLE_USER && m.status == "error") {
                    SheetAction(Icons.Filled.Refresh, "Отправить ещё раз", "") {
                        vm.retry(m.id)
                        actionFor = null
                    }
                }
                SheetAction(Icons.Filled.Delete, "Удалить сообщение", "", danger = true) {
                    vm.delete(m.id)
                    actionFor = null
                }
            }
        }
    }

    selectFor?.let { m ->
        AlertDialog(
            onDismissRequest = { selectFor = null },
            confirmButton = { TextButton(onClick = { selectFor = null }) { Text("Готово", color = Fx.Gold) } },
            text = {
                SelectionContainer {
                    Text(
                        Norm.plain(m.text), color = Fx.Text, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            containerColor = Fx.Card
        )
    }

    if (renameOpen) {
        InputDialog(
            title = "Название чата",
            initial = session?.title ?: "",
            onConfirm = { vm.rename(it) },
            onDismiss = { renameOpen = false }
        )
    }
    if (clearConfirm) {
        ConfirmDialog(
            title = "Очистить историю?",
            text = "Все сообщения этого чата будут удалены. Досье (слова, ошибки, прогресс) сохранится.",
            confirm = "Очистить", danger = true,
            onConfirm = { vm.clear() },
            onDismiss = { clearConfirm = false }
        )
    }
    if (deleteConfirm) {
        ConfirmDialog(
            title = "Удалить чат?",
            text = "Чат и его сообщения будут удалены. Досье сохранится.",
            confirm = "Удалить", danger = true,
            onConfirm = {
                main.deleteSession(sessionId)
                onBack()
            },
            onDismiss = { deleteConfirm = false }
        )
    }

    viewImage?.let { path ->
        Dialog(onDismissRequest = { viewImage = null }) {
            val bitmap by produceState<ImageBitmap?>(null, path) {
                value = withContext(Dispatchers.IO) { AttachmentStore.decodeForDisplay(path, 1600)?.asImageBitmap() }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .clickable { viewImage = null },
                contentAlignment = Alignment.Center
            ) {
                val b = bitmap
                if (b != null) {
                    Image(b, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                } else {
                    CircularProgressIndicator(Modifier.padding(40.dp), color = Fx.Gold)
                }
            }
        }
    }
}

// ───────────────────────── Элементы ленты ─────────────────────────

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Fx.Card)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            color = Fx.TextDim, style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TypingBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
            .background(Fx.CardHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (i in 0..2) {
            val a by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(520, delayMillis = i * 170), RepeatMode.Reverse),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(a)
                    .clip(CircleShape)
                    .background(Fx.TextDim)
            )
        }
    }
}

private data class MetaChip(val text: String, val color: Color)

private fun metaChips(m: MessageEntity): List<MetaChip> {
    val o = parseObjectOrNull(m.meta) ?: return emptyList()
    val out = ArrayList<MetaChip>()
    when (o.str("result")) {
        "correct" -> out.add(MetaChip("✓ верно", Fx.Green))
        "partial" -> out.add(MetaChip("≈ почти", Fx.Gold))
        "wrong" -> out.add(MetaChip("✗ повторим", Fx.Red))
    }
    val words = o.int("words")
    if (words > 0) out.add(MetaChip("+$words в словарь", Fx.Blue))
    val fixes = o.int("fixes")
    if (fixes > 0) out.add(MetaChip("✏️ $fixes", Fx.TextDim))
    if (o.str("source") == "offline") out.add(MetaChip("офлайн", Fx.TextFaint))
    return out
}

@Composable
private fun MessageRow(
    m: MessageEntity,
    maxWidth: Dp,
    playingPath: String,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onImage: (String) -> Unit,
    onPlay: (String) -> Unit
) {
    if (m.role == Repository.ROLE_INFO) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                m.text, color = Fx.TextDim, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Fx.Card)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        return
    }
    val mine = m.role == Repository.ROLE_USER
    val atts = remember(m.attachments) { Attachment.listFromJson(m.attachments) }
    val chips = remember(m.meta) { if (mine) emptyList() else metaChips(m) }
    val error = remember(m.meta, m.status) {
        if (m.status == "error") parseObjectOrNull(m.meta)?.str("error") ?: "Не отправлено" else ""
    }
    val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    val bg = if (mine) Fx.Gold else Fx.CardHigh
    val fg = if (mine) Fx.OnGold else Fx.Text

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bg)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (a in atts) {
                when {
                    a.isImage -> AttachmentImage(a.path) { onImage(a.path) }
                    a.isAudio -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (mine) Fx.OnGold.copy(alpha = 0.15f) else Fx.Ink)
                                .clickable { onPlay(a.path) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (playingPath == a.path) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Прослушать", tint = fg
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (a.durationMs > 0) "Голосовое · " + Timex.duration(a.durationMs) else a.name,
                            color = fg, style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(a.name, color = fg, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (m.text.isNotBlank()) {
                MarkdownText(
                    text = m.text,
                    color = fg,
                    style = MaterialTheme.typography.bodyLarge,
                    boldColor = if (mine) Color.Unspecified else Fx.Gold,
                    lineColor = if (mine) Fx.OnGold.copy(alpha = 0.25f) else Fx.Line,
                    headerBg = if (mine) Fx.OnGold.copy(alpha = 0.08f) else Fx.Ink.copy(alpha = 0.5f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in chips) {
                    Text(c.text, color = c.color, style = MaterialTheme.typography.labelSmall)
                }
                if (m.kind == "ping") Text("смс", color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall)
                if (m.starred) Icon(Icons.Filled.Star, contentDescription = null, tint = if (mine) Fx.OnGold else Fx.Gold, modifier = Modifier.size(12.dp))
                Text(
                    Timex.hhmm(m.createdAt),
                    color = if (mine) Fx.OnGold.copy(alpha = 0.6f) else Fx.TextFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (error.isNotEmpty()) {
            Row(
                Modifier
                    .widthIn(max = maxWidth)
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠ $error", color = Fx.Red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text("Повторить", color = Fx.Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AttachmentImage(path: String, onClick: () -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { AttachmentStore.decodeForDisplay(path, 720)?.asImageBitmap() }
    }
    val b = bitmap
    if (b != null) {
        val ratio = (b.width.toFloat() / b.height.toFloat()).coerceIn(0.6f, 1.9f)
        Image(
            b, contentDescription = "Фото", contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(230.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
        )
    } else {
        Box(
            Modifier
                .size(width = 230.dp, height = 150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Fx.Ink.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun PendingAttachment(a: Attachment, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Fx.CardHigh)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (a.isImage) {
            val bitmap by produceState<ImageBitmap?>(null, a.path) {
                value = withContext(Dispatchers.IO) { AttachmentStore.decodeForDisplay(a.path, 160)?.asImageBitmap() }
            }
            val b = bitmap
            if (b != null) {
                Image(
                    b, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(Modifier.size(40.dp))
            }
        } else {
            Icon(Icons.Filled.Description, contentDescription = null, tint = Fx.Gold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                a.name, color = Fx.Text, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Убрать", tint = Fx.TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Fx.Red else Fx.Gold)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = if (danger) Fx.Red else Fx.Text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

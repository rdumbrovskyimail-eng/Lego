@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.legode.friedrich.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.PingScheduler
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.core.Timex
import com.legode.friedrich.data.db.SessionEntity
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.Screen
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.ConfirmDialog
import com.legode.friedrich.ui.common.EmojiAvatar
import com.legode.friedrich.ui.common.FAvatar
import com.legode.friedrich.ui.common.InputDialog
import com.legode.friedrich.ui.common.Meter
import com.legode.friedrich.ui.common.Panel
import com.legode.friedrich.ui.common.Pill
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx
import kotlinx.coroutines.delay

@Composable
fun SessionsScreen(main: MainViewModel) {
    val sessions by main.sessions.collectAsStateWithLifecycle()
    val profile by main.profile.collectAsStateWithLifecycle()
    val topics by main.topics.collectAsStateWithLifecycle()
    val vocab by main.vocab.collectAsStateWithLifecycle()
    val settings by main.settingsFlow.collectAsStateWithLifecycle()
    val nextPing by main.nextPing.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var newOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<SessionEntity?>(null) }
    var renameFor by remember { mutableStateOf<SessionEntity?>(null) }
    var clearFor by remember { mutableStateOf<SessionEntity?>(null) }
    var deleteFor by remember { mutableStateOf<SessionEntity?>(null) }

    var now by remember { mutableLongStateOf(Timex.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Timex.now()
            delay(20_000)
        }
    }

    val mastery = remember(topics) { topics.associate { it.topicId to it.mastery } }
    val estimate = remember(mastery) { Curriculum.estimate(mastery) }
    val due = remember(vocab, now) { vocab.count { it.dueAt <= now } }
    val shown = remember(sessions, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) sessions
        else sessions.filter { it.title.lowercase().contains(q) || it.lastPreview.lowercase().contains(q) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Fx.Ink)
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Friedrich", subtitle = "Deutsch A0–A1 · твой наставник") {
                IconButton(onClick = { main.open(Screen.Dossier) }) {
                    Icon(Icons.Filled.Insights, contentDescription = "Досье", tint = Fx.Text)
                }
                IconButton(onClick = { main.open(Screen.Reference(null)) }) {
                    Icon(Icons.Filled.MenuBook, contentDescription = "Справочник", tint = Fx.Text)
                }
                IconButton(onClick = { main.open(Screen.Settings) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Fx.Text)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "summary") {
                    Panel(onClick = { main.open(Screen.Dossier) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (profile?.name?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") + "уровень " + estimate.label,
                                    color = Fx.Text, style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Meter(estimate.overall, Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Программа A0–A1 пройдена на ${estimate.overall}%",
                                    color = Fx.TextDim, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Pill("🔥 ${profile?.streak ?: 0} дн", Fx.Gold)
                                Pill("⭐ ${profile?.xp ?: 0} XP")
                                Pill("🃏 $due к повторению", if (due > 0) Fx.Blue else Fx.TextDim)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val pingText = when {
                                !settings.pingsEnabled -> "СМС от Фридриха выключены"
                                nextPing <= 0L -> "Friedrich скоро напишет"
                                nextPing <= now -> "Friedrich вот-вот напишет…"
                                else -> "Следующее смс ≈ " + Timex.hhmm(nextPing) + " · " +
                                    PingScheduler.level(settings.pingIntensity).title.lowercase()
                            }
                            Text(pingText, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Chip("Напиши сейчас", onClick = { main.pingNow() }, accent = true)
                        }
                    }
                }
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Поиск по чатам", color = Fx.TextFaint) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(shown, key = { it.id }) { s ->
                    SessionRow(
                        s = s,
                        now = now,
                        onClick = { main.openChat(s.id) },
                        onLong = { menuFor = s }
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Fx.Gold)
                .clickable { newOpen = true }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Fx.OnGold)
            Spacer(Modifier.width(8.dp))
            Text("Новый чат", color = Fx.OnGold, fontWeight = FontWeight.SemiBold)
        }
    }

    if (newOpen) {
        NewSessionSheet(
            mastery = mastery,
            onDismiss = { newOpen = false },
            onPick = { mode, arg ->
                newOpen = false
                main.newSession(mode, arg)
            }
        )
    }

    menuFor?.let { s ->
        ModalBottomSheet(
            onDismissRequest = { menuFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Fx.Card
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    s.title, color = Fx.Text, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                )
                if (!s.isHome) {
                    SheetLine(if (s.pinned) "Открепить" else "Закрепить наверху") {
                        main.pinSession(s.id, !s.pinned)
                        menuFor = null
                    }
                    SheetLine("Переименовать") {
                        renameFor = s
                        menuFor = null
                    }
                }
                if (settings.pingSessionId != s.id && !(s.isHome && settings.pingSessionId == 0L)) {
                    SheetLine("Присылать смс в этот чат") {
                        main.update { it.copy(pingSessionId = if (s.isHome) 0L else s.id) }
                        menuFor = null
                    }
                }
                SheetLine("Очистить историю") {
                    clearFor = s
                    menuFor = null
                }
                if (!s.isHome) {
                    SheetLine("Удалить чат", danger = true) {
                        deleteFor = s
                        menuFor = null
                    }
                }
            }
        }
    }

    renameFor?.let { s ->
        InputDialog(
            title = "Название чата", initial = s.title,
            onConfirm = { main.renameSession(s.id, it) },
            onDismiss = { renameFor = null }
        )
    }
    clearFor?.let { s ->
        ConfirmDialog(
            title = "Очистить «${s.title}»?",
            text = "Сообщения будут удалены. Досье (слова, ошибки, прогресс) сохранится.",
            confirm = "Очистить", danger = true,
            onConfirm = { main.clearSession(s.id) },
            onDismiss = { clearFor = null }
        )
    }
    deleteFor?.let { s ->
        ConfirmDialog(
            title = "Удалить «${s.title}»?",
            text = "Чат и его сообщения будут удалены. Досье сохранится.",
            confirm = "Удалить", danger = true,
            onConfirm = { main.deleteSession(s.id) },
            onDismiss = { deleteFor = null }
        )
    }
}

@Composable
private fun SessionRow(s: SessionEntity, now: Long, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (s.isHome) Fx.CardHigh else Fx.Card)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (s.isHome) FAvatar(46.dp) else EmojiAvatar(s.emoji, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.title, color = Fx.Text, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (s.pinned && !s.isHome) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = Fx.TextFaint, modifier = Modifier.size(14.dp))
                }
            }
            val preview = when {
                s.draft.isNotBlank() -> "Черновик: " + s.draft
                s.lastPreview.isNotBlank() -> s.lastPreview
                s.isHome -> "Главный чат: сюда приходят смс"
                else -> Skills.mode(s.mode).hint
            }
            Text(
                preview, color = if (s.draft.isNotBlank()) Fx.Red else Fx.TextDim,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Timex.shortStamp(s.updatedAt, now), color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            if (s.unread > 0) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Fx.Gold)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (s.unread > 99) "99+" else s.unread.toString(),
                        color = Fx.OnGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetLine(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (danger) Fx.Red else Fx.Text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    )
}

/** Выбор режима новой беседы; для урока — тема, для сценки — ситуация. */
@Composable
fun NewSessionSheet(mastery: Map<String, Int>, onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    var step by remember { mutableStateOf("modes") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Fx.Card
    ) {
        when (step) {
            "topics" -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                item {
                    Text("Тема урока", color = Fx.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
                    val next = Curriculum.focus(mastery, 1).firstOrNull()
                    if (next != null) {
                        Spacer(Modifier.height(6.dp))
                        Chip("▶ По программе: ${next.title}", onClick = { onPick(Skills.LESSON, next.id) }, accent = true)
                    }
                }
                for (block in Curriculum.blocks) {
                    item(key = "b" + block.id) {
                        Text(
                            block.emoji + " " + block.title, color = Fx.Gold, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                        )
                    }
                    items(Curriculum.inBlock(block.id), key = { it.id }) { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(Skills.LESSON, t.id) }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                                Text(t.de + " · " + t.level, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${mastery[t.id] ?: 0}%", color = Fx.TextDim, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            "scenarios" -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                item {
                    Text("Сценка", color = Fx.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(Skills.scenarios, key = { it.id }) { sc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(Skills.ROLEPLAY, sc.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmojiAvatar(sc.emoji, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(sc.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                item {
                    Text("Новый чат", color = Fx.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(Skills.modes, key = { it.id }) { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                when {
                                    m.needsTopic -> step = "topics"
                                    m.needsScenario -> step = "scenarios"
                                    else -> onPick(m.id, "")
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmojiAvatar(m.emoji, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(m.hint, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

package com.legode.friedrich.ui.reference

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.KbEntry
import com.legode.friedrich.brain.Norm
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.brain.Topic
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.MarkdownText
import com.legode.friedrich.ui.common.Meter
import com.legode.friedrich.ui.common.Panel
import com.legode.friedrich.ui.common.Pill
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Атлас практик: 30 методологических протоколов + вся библиотека цитат, КПТ-ловушек, соматики и прорывов. */
@Composable
fun ReferenceScreen(main: MainViewModel, initialTopic: String?, onBack: () -> Unit) {
    val skills by main.skills.collectAsStateWithLifecycle()
    val settings by main.settingsFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mastery = remember(skills) { skills.associate { it.skillId to it.mastery } }

    var tab by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf(initialTopic) }
    var query by remember { mutableStateOf("") }
    var kbKind by remember { mutableStateOf("all") }

    val kbAll by produceState(initialValue = emptyList<KbEntry>()) {
        value = withContext(Dispatchers.IO) {
            main.kb.ensureLoaded()
            main.kb.allByKind("cbt") +
                main.kb.allByKind("philosophy") +
                main.kb.allByKind("practice") +
                main.kb.allByKind("breakthrough")
        }
    }

    val open = openId?.let { Curriculum.topic(it) }
    BackHandler(enabled = open != null && initialTopic == null) { openId = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fx.Ink)
    ) {
        if (open != null) {
            TopBar(
                title = open.title,
                subtitle = "${open.school} · ${open.stage}",
                onBack = { if (initialTopic == null) openId = null else onBack() }
            ) {
                IconButton(onClick = {
                    if (settings.ttsEnabled) main.speaker.speak(open.sheet, settings.ttsRate)
                }) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Озвучить", tint = Fx.Text)
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Meter(mastery[open.id] ?: 0, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text("${mastery[open.id] ?: 0}%", color = Fx.TextDim, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("🧠 Практиковать с Атласом", onClick = { main.newSession(Skills.LESSON, open.id) }, accent = true)
                }
                Spacer(Modifier.height(14.dp))
                MarkdownText(
                    text = open.sheet,
                    color = Fx.Text,
                    style = MaterialTheme.typography.bodyLarge,
                    boldColor = Fx.Gold,
                    lineColor = Fx.Line,
                    headerBg = Fx.Card
                )
                Spacer(Modifier.height(40.dp))
            }
        } else {
            TopBar(
                title = "Атлас и База",
                subtitle = "${Curriculum.topics.size} протоколов · ${kbAll.size} записей мудрости и КПТ",
                onBack = onBack
            )
            TabRow(
                selectedTabIndex = tab,
                containerColor = Fx.Panel,
                contentColor = Fx.Gold
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Протоколы (${Curriculum.topics.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("База знаний (${kbAll.size})") })
            }

            if (tab == 0) {
                val q = Norm.normalizeYo(query.trim().lowercase())
                val found: List<Topic> = remember(q) {
                    if (q.isEmpty()) emptyList()
                    else Curriculum.topics.filter { t ->
                        Norm.normalizeYo(t.title.lowercase()).contains(q) ||
                            Norm.normalizeYo(t.school.lowercase()).contains(q) ||
                            t.keywords.any { it.contains(q) } ||
                            Norm.normalizeYo(t.sheet.lowercase()).contains(q)
                    }
                }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "search_proto") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Поиск: паника, стоики, тупик, прокрастинация…", color = Fx.TextFaint) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (q.isNotEmpty()) {
                        if (found.isEmpty()) {
                            item { Text("Ничего не нашлось", color = Fx.TextDim, modifier = Modifier.padding(16.dp)) }
                        }
                        items(found, key = { "f" + it.id }) { t -> TopicRow(t, mastery[t.id] ?: 0) { openId = t.id } }
                    } else {
                        for (block in Curriculum.blocks) {
                            item(key = "b" + block.id) {
                                Text(
                                    block.emoji + "  " + block.title,
                                    color = Fx.Gold, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }
                            items(Curriculum.inBlock(block.id), key = { it.id }) { t ->
                                TopicRow(t, mastery[t.id] ?: 0) { openId = t.id }
                            }
                        }
                    }
                }
            } else {
                val q = Norm.normalizeYo(query.trim().lowercase())
                val shown = remember(kbAll, kbKind, q) {
                    kbAll.filter { e ->
                        val byKind = kbKind == "all" || e.kind == kbKind
                        val byQuery = q.isEmpty() ||
                            Norm.normalizeYo(e.title.lowercase()).contains(q) ||
                            Norm.normalizeYo(e.core.lowercase()).contains(q) ||
                            Norm.normalizeYo(e.source.lowercase()).contains(q) ||
                            Norm.normalizeYo(e.analysis.lowercase()).contains(q) ||
                            e.tags.any { it.contains(q) } ||
                            e.states.any { it.contains(q) }
                        byKind && byQuery
                    }
                }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "search_kb") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Поиск: Аврелий, Франкл, стыд, бессонница…", color = Fx.TextFaint) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "kb_filters") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val filters = listOf(
                                "all" to "Все",
                                "cbt" to "🔍 КПТ-ловушки",
                                "philosophy" to "🏛️ Философия",
                                "practice" to "🌊 Успокоение",
                                "breakthrough" to "⚡ Прорывы"
                            )
                            items(filters) { (id, title) ->
                                Chip(title, onClick = { kbKind = id }, selected = kbKind == id)
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        item { Text("Ничего не найдено", color = Fx.TextDim, modifier = Modifier.padding(16.dp)) }
                    }
                    items(shown, key = { it.id }) { e ->
                        KbCard(
                            e = e,
                            onSpeak = {
                                if (settings.ttsEnabled) {
                                    main.speaker.speak("${e.title}. ${e.core}. ${e.analysis}. ${e.action}", settings.ttsRate)
                                }
                            },
                            onAnchor = {
                                main.addAnchor(
                                    phrase = e.core,
                                    why = e.analysis.ifBlank { e.action },
                                    source = e.source,
                                    trigger = e.states.joinToString(", ")
                                )
                                Toast.makeText(context, "Сохранено в твои якоря силы", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(t: Topic, mastery: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Fx.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
            Text(t.school, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Meter(mastery, Modifier.fillMaxWidth(), height = 4.dp)
        }
        Spacer(Modifier.width(12.dp))
        Pill(t.stage)
    }
}

@Composable
private fun KbCard(e: KbEntry, onSpeak: () -> Unit, onAnchor: () -> Unit) {
    val badge = when (e.kind) {
        "cbt" -> "🔍 КПТ"
        "philosophy" -> "🏛️ Философия"
        "practice" -> "🌊 Практика"
        else -> "⚡ Прорыв"
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(badge, Fx.Gold)
            if (e.source.isNotBlank()) Pill(e.source)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSpeak, modifier = Modifier.width(34.dp).height(34.dp)) {
                Icon(Icons.Filled.VolumeUp, contentDescription = "Озвучить", tint = Fx.TextDim)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(e.title, color = Fx.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("«${e.core}»", color = Fx.Gold, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (e.analysis.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(e.analysis, color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
        if (e.action.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("🎯 ${e.action}", color = Fx.Text, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Chip("⚓ В мои якоря", onClick = onAnchor)
    }
}
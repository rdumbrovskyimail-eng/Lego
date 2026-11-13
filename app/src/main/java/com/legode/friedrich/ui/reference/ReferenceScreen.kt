package com.legode.friedrich.ui.reference

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legode.friedrich.brain.Curriculum
import com.legode.friedrich.brain.Skills
import com.legode.friedrich.brain.Topic
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.common.MarkdownText
import com.legode.friedrich.ui.common.Meter
import com.legode.friedrich.ui.common.Pill
import com.legode.friedrich.ui.common.TopBar
import com.legode.friedrich.ui.theme.Fx

/** Справочник A0–A1: все темы программы со шпаргалками — те же таблицы, по которым сверяется Friedrich. */
@Composable
fun ReferenceScreen(main: MainViewModel, initialTopic: String?, onBack: () -> Unit) {
    val topics by main.topics.collectAsStateWithLifecycle()
    val settings by main.settingsFlow.collectAsStateWithLifecycle()
    val mastery = remember(topics) { topics.associate { it.topicId to it.mastery } }
    var openId by remember { mutableStateOf(initialTopic) }
    var query by remember { mutableStateOf("") }

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
                subtitle = open.de + " · " + open.level,
                onBack = { if (initialTopic == null) openId = null else onBack() }
            ) {
                IconButton(onClick = {
                    if (settings.ttsEnabled) main.speaker.speak(open.sheet, settings.ttsRate)
                }) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "Озвучить немецкий", tint = Fx.Text)
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
                    Chip("📚 Урок с Фридрихом", onClick = { main.newSession(Skills.LESSON, open.id) }, accent = true)
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
            TopBar(title = "Справочник A0–A1", subtitle = "${Curriculum.topics.size} тем: правила и таблицы", onBack = onBack)
            val q = query.trim().lowercase()
            val found: List<Topic> = remember(q) {
                if (q.isEmpty()) emptyList()
                else Curriculum.topics.filter { t ->
                    t.title.lowercase().contains(q) || t.de.lowercase().contains(q) ||
                        t.keywords.any { it.contains(q) } || t.sheet.lowercase().contains(q)
                }
            }
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Поиск: Akkusativ, часы, kein…", color = Fx.TextFaint) },
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
            Text(t.de, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Meter(mastery, Modifier.fillMaxWidth(), height = 4.dp)
        }
        Spacer(Modifier.width(12.dp))
        Pill(t.level)
    }
}

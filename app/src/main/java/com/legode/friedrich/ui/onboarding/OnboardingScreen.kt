package com.legode.friedrich.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.legode.friedrich.brain.PingScheduler
import com.legode.friedrich.ui.MainViewModel
import com.legode.friedrich.ui.common.Chip
import com.legode.friedrich.ui.settings.isIgnoringBattery
import com.legode.friedrich.ui.settings.openUrl
import com.legode.friedrich.ui.settings.requestIgnoreBattery
import com.legode.friedrich.ui.theme.AtlasAvatar
import com.legode.friedrich.ui.theme.Fx

private val GOALS = listOf(
    "Выйти из тупика и апатии",
    "Убрать тревогу и панику",
    "Пробить прокрастинацию",
    "Пережить кризис / спад",
    "Собрать дисциплину",
    "Восстановить сон и покой"
)

private val STATES = listOf(
    "Сейчас тяжело / в яме" to "Нет сил, накрывает апатия, тревога или безысходность",
    "Штормит и качает" to "То держусь, то откатывает назад в негатив и прокрастинацию",
    "Хочу мощный рывок" to "В целом держусь, нужен чёткий план, тонус и анти-саботаж"
)

private val PUSH_MODES = listOf(
    "Напористо ⚡" to "Жёсткий союзник: вскрывает отговорки, не даёт слиться (но бережёт в панике/ночью)",
    "Сбалансированно" to "Твёрдая опора, логика КПТ и спокойное подталкивание",
    "Бережно" to "Мягкая стабилизация без нажима, приоритет на покой"
)

/** Первичная настройка Атласа: знакомство, API-ключ, состояние, стиль напора и разрешения. */
@Composable
fun OnboardingScreen(main: MainViewModel) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var key by remember { mutableStateOf(main.settings.apiKey()) }
    var keyOk by remember { mutableStateOf(key.isNotBlank()) }
    var name by remember { mutableStateOf("") }
    var selfState by remember { mutableIntStateOf(1) }
    var goal by remember { mutableStateOf("") }
    var pushChoice by remember { mutableIntStateOf(0) } // 0 = Напористо (pushiness = 2)
    var intensity by remember { mutableIntStateOf(3) }
    var refresh by remember { mutableIntStateOf(0) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    BackHandler(enabled = step > 0) { step-- }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fx.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            for (i in 0..3) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= step) Fx.Gold else Fx.Line)
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (step) {
                0 -> {
                    AtlasAvatar(84.dp, Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "На связи Атлас.",
                        color = Fx.Text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Твой личный психо-эмоциональный стратег и мотивационный движок. Вытаскиваю из тупиков, тревоги и апатии к ясности, силе и действиям.",
                        color = Fx.TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Bullet("⚡", "Напористый, но не слепой", "Ломаю отговорки и самообман, когда ты залип — и мгновенно переключаюсь на бережное заземление, если накрыла паника или острая боль.")
                    Bullet("🧠", "КПТ, стоики, Франкл, ACT и соматика", "Встроенная крупная база цитат с глубокими разборами, антидоты ловушек ума и дыхательные практики с паузами.")
                    Bullet("💬", "Пишу тебе первым", "Присылаю СМС-вызовы, напоминаю твои якоря силы и дожимаю обещанные шаги прямо в уведомлениях.")
                    Bullet("🔒", "Всё остаётся на телефоне", "Переписка, триггеры, динамика настроения и планы хранятся локально в твоём устройстве.")
                }

                1 -> {
                    Title(
                        "Ключ Gemini API",
                        "Движок работает на модели Gemini 3.1 Flash-Lite. Нужен твой API-ключ — он хранится только на этом телефоне в зашифрованном виде."
                    )
                    OutlinedTextField(
                        value = key, onValueChange = {
                            key = it
                            keyOk = false
                        },
                        label = { Text("API-ключ") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(
                            if (main.keyChecking) "Проверяю…" else "Проверить ключ",
                            onClick = { if (!main.keyChecking && key.isNotBlank()) main.saveAndTestKey(key) { ok -> keyOk = ok } },
                            accent = true
                        )
                        Chip("Получить ключ", onClick = { openUrl(context, "https://aistudio.google.com/apikey") })
                    }
                    if (main.keyCheck.isNotEmpty()) {
                        Text(main.keyCheck, color = if (keyOk) Fx.Green else Fx.TextDim, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Как получить: открой aistudio.google.com/apikey → войди в Google → «Create API key» → вставь сюда.\n\nМожно пропустить: без ключа Атлас всё равно будет присылать глубокие разборы, цитаты и практики из офлайн-базы.",
                        color = Fx.TextFaint, style = MaterialTheme.typography.bodySmall
                    )
                }

                2 -> {
                    Title("Точка А и характер", "Это попадёт в досье, чтобы удары по тупикам и поддержка били точно в цель.")
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Как к тебе обращаться?") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Text("Как ты сейчас себя ощущаешь?", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    STATES.forEachIndexed { i, pair ->
                        Choice(pair.first, pair.second, selfState == i) { selfState = i }
                    }
                    Text("Какой стиль общения нужен?", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    PUSH_MODES.forEachIndexed { i, pair ->
                        Choice(pair.first, pair.second, pushChoice == i) { pushChoice = i }
                    }
                    Text("Главная задача сейчас", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (rowGoals in GOALS.chunked(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (g in rowGoals) Chip(g, onClick = { goal = g }, selected = goal == g)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = goal, onValueChange = { goal = it },
                        label = { Text("Или своими словами") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    Title("Как часто выходить на связь?", "Атлас пишет в течение дня, контролирует выполнение шагов и перехватывает спады. Частоту и тихие часы легко поменять потом.")
                    PingScheduler.INTENSITY.forEachIndexed { i, lv ->
                        Choice(lv.title, lv.about, intensity == i) { intensity = i }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Чтобы я не засыпал в фоне", color = Fx.Text, style = MaterialTheme.typography.titleMedium)
                    val notifOn = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
                    val battery = remember(refresh) { isIgnoringBattery(context) }
                    Permission(
                        "Уведомления", if (notifOn) "разрешены ✅" else "без них ты не увидишь мои СМС", notifOn
                    ) {
                        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        refresh++
                    }
                    Permission(
                        "Работа без ограничений батареи",
                        if (battery) "разрешена ✅" else "сними ограничение, иначе система усыпит приложение",
                        battery
                    ) {
                        requestIgnoreBattery(context)
                        refresh++
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                Text(
                    "Назад", color = Fx.TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { step-- }
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            val label = when (step) {
                0 -> "Поехали ⚡"
                1 -> if (key.isBlank()) "Пропустить" else "Дальше"
                2 -> "Дальше"
                else -> "Запустить Атласа 🚀"
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Fx.Gold)
                    .clickable {
                        when (step) {
                            1 -> {
                                if (key.isNotBlank() && !keyOk) main.settings.setApiKey(key.trim())
                                step++
                            }

                            3 -> {
                                val pushinessVal = when (pushChoice) {
                                    0 -> 2
                                    1 -> 1
                                    else -> 0
                                }
                                main.completeOnboarding(name, selfState, goal, intensity, pushinessVal)
                            }

                            else -> step++
                        }
                    }
                    .padding(horizontal = 26.dp, vertical = 14.dp)
            ) {
                Text(label, color = Fx.OnGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(title, color = Fx.Text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullet(emoji: String, title: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Fx.Card)
            .padding(14.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Fx.Text, style = MaterialTheme.typography.titleMedium)
            Text(text, color = Fx.TextDim, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Fx.GoldDim else Fx.Card)
            .border(1.dp, if (selected) Fx.Gold else Fx.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) Fx.Gold else Fx.Line)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Permission(title: String, text: String, ok: Boolean, onClick: () -> Unit) {
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
            Text(title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
            Text(text, color = if (ok) Fx.Green else Fx.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        if (!ok) Chip("Разрешить", onClick = onClick, accent = true)
    }
}
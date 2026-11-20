package com.legode.friedrich.brain

import android.content.Context
import com.legode.friedrich.core.Logx
import com.legode.friedrich.core.Timex
import com.legode.friedrich.core.arr
import com.legode.friedrich.core.str
import com.legode.friedrich.core.strings
import org.json.JSONObject
import kotlin.random.Random

/** Одна единица базы знаний: КПТ-ловушка, философская мысль с разбором, практика стабилизации или шаг прорыва. */
data class KbEntry(
    val id: String,
    /** cbt | philosophy | practice | breakthrough */
    val kind: String,
    /** cbt | act | stoicism | logotherapy | existential | somatic | behavioral | zen | psychoanalysis */
    val school: String,
    /** Целевые состояния: depression, deadend, apathy, panic, anxiety, rumination, guilt, anger, loneliness, burnout, failure, procrastination, night */
    val states: List<String>,
    /** calm | firm | push | deep */
    val tone: String,
    val title: String,
    val core: String,
    val source: String,
    val analysis: String,
    val action: String,
    val tags: List<String>
) {
    /** Предвычисленные стеммированные корни для сверхбыстрого поиска. */
    val searchStems: Set<String> by lazy {
        val raw = buildString {
            append(title).append(' ')
            append(core).append(' ')
            append(source).append(' ')
            append(analysis).append(' ')
            for (t in tags) append(t).append(' ')
            for (s in states) append(s).append(' ')
        }
        Norm.stemTokens(raw)
    }
}

/**
 * Локальная база знаний и RAG-поисковик «Атласа».
 *
 * Загружает все файлы *.jsonl из assets/kb/. Способна без задержек индексировать тысячи записей:
 *  • классифицирует состояние пользователя по тексту и времени суток;
 *  • ранжирует записи по совпадению состояния, тона, школы и смысловых корней;
 *  • гарантирует разнообразие типов в выборке (КПТ + философия + практика/действие) и защиту от повторов.
 */
class KnowledgeBase(private val context: Context) {

    data class Query(
        val userText: String = "",
        val states: Set<String> = emptySet(),
        val preferredTones: Set<String> = emptySet(),
        val preferredKinds: Set<String> = emptySet(),
        val excludeIds: Set<String> = emptySet(),
        val limit: Int = 6,
        val diversify: Boolean = true
    )

    private val loadLock = Any()

    @Volatile
    private var loaded = false
    private val entries = ArrayList<KbEntry>(512)
    private val byId = HashMap<String, KbEntry>()
    private val byState = HashMap<String, MutableList<Int>>()
    private val byKind = HashMap<String, MutableList<Int>>()

    fun size(): Int {
        ensureLoaded()
        return entries.size
    }

    fun get(id: String): KbEntry? {
        ensureLoaded()
        return byId[id]
    }

    fun allByKind(kind: String): List<KbEntry> {
        ensureLoaded()
        return byKind[kind]?.map { entries[it] } ?: emptyList()
    }

    fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            try {
                val files = context.assets.list("kb")?.sorted().orEmpty()
                for (name in files) {
                    if (!name.endsWith(".jsonl", ignoreCase = true)) continue
                    loadJsonl("kb/$name")
                }
            } catch (t: Throwable) {
                Logx.w("kb list failed", t)
            }
            if (entries.isEmpty()) {
                Logx.w("kb is empty, seeding emergency fallback entries")
                seedEmergencyEntries()
            }
            loaded = true
            Logx.d("KnowledgeBase loaded: ${entries.size} entries")
        }
    }

    private fun loadJsonl(assetPath: String) {
        try {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                    try {
                        val o = JSONObject(line)
                        val entry = KbEntry(
                            id = o.str("id").ifBlank { "${assetPath}_$idx" },
                            kind = o.str("kind", "cbt").lowercase(),
                            school = o.str("school", "cbt").lowercase(),
                            states = o.arr("states")?.strings()?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty(),
                            tone = o.str("tone", "firm").lowercase(),
                            title = o.str("title").trim(),
                            core = o.str("core").trim(),
                            source = o.str("source").trim(),
                            analysis = o.str("analysis").trim(),
                            action = o.str("action").trim(),
                            tags = o.arr("tags")?.strings()?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }.orEmpty()
                        )
                        if (entry.core.isNotEmpty() && !byId.containsKey(entry.id)) {
                            addEntry(entry)
                        }
                    } catch (t: Throwable) {
                        Logx.w("bad kb line in $assetPath:$idx", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Logx.w("failed reading $assetPath", t)
        }
    }

    private fun addEntry(e: KbEntry) {
        val idx = entries.size
        entries.add(e)
        byId[e.id] = e
        byKind.getOrPut(e.kind) { ArrayList() }.add(idx)
        for (st in e.states) {
            byState.getOrPut(st) { ArrayList() }.add(idx)
        }
        // Прогреваем ленивый индекс корней при загрузке, чтобы первый поиск не тормозил.
        e.searchStems
    }

    /** Определяет доминирующие эмоциональные состояния и тупики по тексту и времени суток. */
    fun inferStates(text: String, nowMillis: Long = Timex.now()): Set<String> {
        val out = LinkedHashSet<String>()
        val low = Norm.looseKey(text)
        val stems = Norm.stemTokens(text)

        for ((state, signals) in STATE_SIGNALS) {
            val matched = signals.phrases.any { low.contains(it) } ||
                signals.stems.any { it in stems }
            if (matched) out.add(state)
        }

        if (Timex.isNightRisk(nowMillis)) {
            out.add("night")
            if (out.isEmpty()) out.add("rumination")
        }
        if (out.isEmpty()) out.add("deadend")
        return out
    }

    /** Интеллектуальный подбор записей из всей базы под текущий запрос и состояние. */
    fun retrieve(query: Query): List<KbEntry> {
        ensureLoaded()
        if (entries.isEmpty()) return emptyList()

        val queryStems = Norm.stemTokens(query.userText)
        val targetStates = if (query.states.isNotEmpty()) query.states else inferStates(query.userText)

        // Собираем кандидатов: сначала через быстрый индекс по состояниям, либо всю базу при общем запросе.
        val candidateIndices = LinkedHashSet<Int>()
        for (st in targetStates) {
            byState[st]?.let { candidateIndices.addAll(it) }
        }
        if (candidateIndices.size < 25) {
            for (i in entries.indices) candidateIndices.add(i)
        }

        val scored = ArrayList<Pair<KbEntry, Double>>(candidateIndices.size)
        for (idx in candidateIndices) {
            val e = entries[idx]
            if (e.id in query.excludeIds) continue

            var score = 0.0
            for (st in e.states) {
                if (st in targetStates) score += 4.5
            }
            if (e.tone in query.preferredTones) score += 3.0
            if (e.kind in query.preferredKinds) score += 2.5

            if (queryStems.isNotEmpty()) {
                var hits = 0
                for (qStem in queryStems) {
                    if (qStem in e.searchStems) hits++
                }
                score += hits * 2.2
            }

            // Небольшой штраф, если запись ночная, а сейчас день (и наоборот).
            if ("night" in e.states && "night" !in targetStates) score -= 2.0

            if (score > 0.0 || query.userText.isBlank()) {
                scored.add(e to score)
            }
        }

        scored.sortByDescending { it.second }

        if (!query.diversify) {
            return scored.take(query.limit).map { it.first }
        }

        // Гарантируем баланс: не больше 2 записей одного типа (kind), чтобы ИИ получал и КПТ, и философию, и практику/действие.
        val picked = ArrayList<KbEntry>(query.limit)
        val kindCount = HashMap<String, Int>()
        val schoolCount = HashMap<String, Int>()
        val maxPerKind = maxOf(1, (query.limit + 1) / 3)

        for ((e, _) in scored) {
            if (picked.size >= query.limit) break
            val kc = kindCount[e.kind] ?: 0
            val sc = schoolCount[e.school] ?: 0
            if (kc < maxPerKind && sc < 2) {
                picked.add(e)
                kindCount[e.kind] = kc + 1
                schoolCount[e.school] = sc + 1
            }
        }
        // Если лимит ещё не добран — добираем лучшими по рейтингу.
        if (picked.size < query.limit) {
            val usedIds = picked.map { it.id }.toHashSet()
            for ((e, _) in scored) {
                if (picked.size >= query.limit) break
                if (e.id !in usedIds) picked.add(e)
            }
        }
        return picked
    }

    /** Подбирает одну мощную запись для проактивного СМС-пинга или офлайн-режима. */
    fun pickForPing(
        rnd: Random,
        states: Set<String>,
        preferredTones: Set<String>,
        preferredKinds: Set<String>,
        excludeIds: Set<String>
    ): KbEntry? {
        val top = retrieve(
            Query(
                states = states,
                preferredTones = preferredTones,
                preferredKinds = preferredKinds,
                excludeIds = excludeIds,
                limit = 12,
                diversify = false
            )
        )
        if (top.isEmpty()) return null
        val pool = top.take(minOf(6, top.size))
        return pool[rnd.nextInt(pool.size)]
    }

    /** Форматирует найденные записи в плотный контекст для промпта Gemini. */
    fun formatForPrompt(picked: List<KbEntry>): String {
        if (picked.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("# ОПОРНАЯ БАЗА ЗНАНИЙ (используй как точный материал: не цитируй всё подряд, выбери 1–2 самых метких инструмента под контекст)\n")
        for (e in picked) {
            sb.append("• [").append(e.id).append(" | ").append(e.kind.uppercase()).append('/').append(e.school).append("] ")
                .append(e.title)
            if (e.source.isNotBlank()) sb.append(" (").append(e.source).append(')')
            sb.append('\n')
            sb.append("  Суть: ").append(e.core).append('\n')
            if (e.analysis.isNotBlank()) sb.append("  Механика: ").append(e.analysis).append('\n')
            if (e.action.isNotBlank()) sb.append("  Ход: ").append(e.action).append('\n')
        }
        return sb.toString()
    }

    private fun seedEmergencyEntries() {
        addEntry(
            KbEntry(
                id = "em_01",
                kind = "cbt",
                school = "cbt",
                states = listOf("depression", "deadend", "apathy"),
                tone = "firm",
                title = "Ловушка «сначала почувствовать силы, потом делать»",
                core = "Мотивация не предшествует действию — она рождается как побочный эффект первых 3 минут движения.",
                source = "Поведенческая активация (КПТ)",
                analysis = "В депрессии дофаминовый стартёр отключён. Ждать вдохновения в таком состоянии — всё равно что ждать, пока машина согреется с выключенным двигателем.",
                action = "Выбери действие на 120 секунд и запусти его без права на оценку результата.",
                tags = listOf("апатия", "силы", "мотивация", "депрессия", "тупик")
            )
        )
        addEntry(
            KbEntry(
                id = "em_02",
                kind = "philosophy",
                school = "stoicism",
                states = listOf("anxiety", "deadend", "failure"),
                tone = "firm",
                title = "Дихотомия контроля",
                core = "Препятствие к действию продвигает действие. То, что стоит на пути, становится путём.",
                source = "Марк Аврелий",
                analysis = "Потеря энергии происходит там, где ты пытаешься управлять прошлым или чужими реакциями вместо своего следующего шага.",
                action = "Проведи черту: что на 100% в твоих руках в ближайший час?",
                tags = listOf("контроль", "препятствие", "путь", "тупик", "страх")
            )
        )
    }

    private data class StateSignal(val phrases: List<String>, val stems: Set<String>)

    private companion object {
        val STATE_SIGNALS: Map<String, StateSignal> = mapOf(
            "depression" to StateSignal(
                listOf("ничего не радует", "нет смысла", "все серое", "на дне", "жить не хочется", "опустились руки"),
                setOf("депресс", "бессмыслен", "безнадеж", "пустот", "апатия", "ангедон", "отчаян", "дно", "уныни")
            ),
            "deadend" to StateSignal(
                listOf("выхода нет", "не знаю что делать", "в тупике", "куда идти", "все рухнуло", "безвыход"),
                setOf("тупик", "застрял", "безвыход", "безысход", "распуть", "потерял", "рухнул")
            ),
            "apathy" to StateSignal(
                listOf("не могу встать", "ноль сил", "нет энергии", "ничего не хочу", "нет сил"),
                setOf("апат", "лен", "вял", "обессил", "истощ", "слабост", "прокрастин", "откладыва", "залип")
            ),
            "procrastination" to StateSignal(
                listOf("откладываю на потом", "не могу начать", "сижу в телефоне", "тяжело начать"),
                setOf("прокрастин", "откладыва", "саботаж", "завтра", "ленив", "залипа", "тянул")
            ),
            "panic" to StateSignal(
                listOf("паническая атака", "не могу дышать", "сердце колотится", "накрыло", "трясет"),
                setOf("паник", "задыха", "колот", "тряс", "ужас", "накрыл", "захлест")
            ),
            "anxiety" to StateSignal(
                listOf("вдруг не получится", "что если", "боюсь что", "накручиваю себя"),
                setOf("тревог", "тревож", "страх", "страшн", "боюсь", "волну", "пережива", "нервнич", "катастроф")
            ),
            "rumination" to StateSignal(
                listOf("кручу в голове", "не могу уснуть", "одни и те же мысли", "пережевываю"),
                setOf("руминац", "накручива", "загоня", "навязчив", "бессонниц", "мыслемешалк", "оверсинкинг")
            ),
            "guilt" to StateSignal(
                listOf("сам во всем виноват", "я неудачник", "я слабак", "ненавижу себя", "стыдно за себя"),
                setOf("вин", "виноват", "стыд", "стыдн", "ничтожеств", "слабак", "лузер", "неудачн", "самобичеван")
            ),
            "anger" to StateSignal(
                listOf("все бесит", "меня достало", "срываюсь на", "несправедливо"),
                setOf("бесит", "злост", "злюсь", "ярост", "ненавист", "раздража", "гнев", "взбесил", "достал")
            ),
            "loneliness" to StateSignal(
                listOf("никому не нужен", "совсем один", "меня не понимают", "пусто вокруг"),
                setOf("одинок", "одиночеств", "бросил", "ненужн", "изоляц", "чужой", "покинул")
            ),
            "burnout" to StateSignal(
                listOf("сил больше нет", "выжат как лимон", "устал от всего", "нет ресурса"),
                setOf("выгор", "устал", "выжат", "перегруз", "измота", "переутом")
            ),
            "failure" to StateSignal(
                listOf("все испортил", "опять облажался", "не получилось", "все зря"),
                setOf("провал", "облажал", "ошибк", "потерпел", "рухнул", "сорвал", "сломал")
            )
        )
    }
}

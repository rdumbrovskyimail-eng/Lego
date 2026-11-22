package com.legode.friedrich.brain

import android.content.Context
import com.legode.friedrich.core.Logx
import com.legode.friedrich.core.Timex
import com.legode.friedrich.core.arr
import com.legode.friedrich.core.str
import com.legode.friedrich.core.strings
import org.json.JSONObject
import kotlin.math.ln
import kotlin.random.Random

/** Одна единица базы знаний: КПТ, философия, практика, прорыв, ментальная модель, дебаисинг или метод обучения. */
data class KbEntry(
    val id: String,
    /** cbt | philosophy | practice | breakthrough | model | bias | learning */
    val kind: String,
    /** cbt | act | stoicism | logotherapy | existential | somatic | behavioral | zen | psychoanalysis | systems | probability | logic | learning | strategy */
    val school: String,
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
    /** Предвычисленные веса термов для быстрого BM25-ранжирования. */
    val termFreqs: Map<String, Int> by lazy {
        val map = HashMap<String, Int>(32)
        fun add(text: String, weight: Int) {
            for (stem in Norm.stemList(text)) {
                map[stem] = (map[stem] ?: 0) + weight
            }
        }
        add(title, 3)
        add(core, 2)
        add(source, 2)
        add(analysis, 1)
        for (t in tags) add(t, 3)
        for (s in states) add(s, 1)
        map
    }

    val docLength: Int by lazy {
        var total = 0
        for (v in termFreqs.values) total += v
        total.coerceAtLeast(1)
    }
}

/**
 * Локальная база знаний и BM25 RAG-поисковик «Атласа».
 *
 * Индексирует все файлы *.jsonl из assets/kb/. Способна без задержек индексировать тысячи записей:
 *  • классифицирует эмоциональные и когнитивные состояния по тексту и времени суток;
 *  • ранжирует записи через Okapi BM25 + совпадение состояния, тона и типа;
 *  • гарантирует разнообразие школ/типов и проверяет целостность ассетов при загрузке.
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

    data class IntegrityReport(
        val loadedFiles: Int = 0,
        val totalEntries: Int = 0,
        val duplicateIds: Int = 0,
        val invalidLines: Int = 0,
        val warnings: List<String> = emptyList()
    )

    private val loadLock = Any()

    @Volatile
    private var loaded = false
    private val entries = ArrayList<KbEntry>(512)
    private val byId = HashMap<String, KbEntry>()
    private val byState = HashMap<String, MutableList<Int>>()
    private val byKind = HashMap<String, MutableList<Int>>()
    private val postings = HashMap<String, MutableList<Int>>()
    private val idfMap = HashMap<String, Double>()
    private var avgDocLen = 20.0

    @Volatile
    var integrity: IntegrityReport = IntegrityReport()
        private set

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

    fun allEntries(): List<KbEntry> {
        ensureLoaded()
        return entries.toList()
    }

    fun ensureLoaded() {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            var fileCount = 0
            var duplicates = 0
            var invalid = 0
            val warns = ArrayList<String>()

            try {
                val files = context.assets.list("kb")?.sorted().orEmpty()
                for (name in files) {
                    if (!name.endsWith(".jsonl", ignoreCase = true)) continue
                    fileCount++
                    val res = loadJsonl("kb/$name", warns)
                    duplicates += res.first
                    invalid += res.second
                }
            } catch (t: Throwable) {
                Logx.w("kb list failed", t)
                warns.add("Ошибка чтения папки assets/kb: ${t.message}")
            }
            if (entries.isEmpty()) {
                Logx.w("kb is empty, seeding emergency fallback entries")
                seedEmergencyEntries()
                warns.add("Активирован аварийный резерв KB (файлы jsonl не загружены)")
            }

            rebuildBm25Index()
            integrity = IntegrityReport(
                loadedFiles = fileCount,
                totalEntries = entries.size,
                duplicateIds = duplicates,
                invalidLines = invalid,
                warnings = warns.take(20)
            )
            loaded = true
            Logx.d("KnowledgeBase loaded: ${entries.size} entries from $fileCount files (invalid=$invalid, dup=$duplicates)")
        }
    }

    private fun loadJsonl(assetPath: String, warns: MutableList<String>): Pair<Int, Int> {
        var dup = 0
        var inv = 0
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
                        if (entry.core.isEmpty() || entry.title.isEmpty()) {
                            inv++
                            warns.add("$assetPath:${idx + 1}: пустой title или core")
                            return@forEachIndexed
                        }
                        if (entry.kind !in VALID_KINDS) {
                            warns.add("$assetPath:${idx + 1}: нестандартный kind=${entry.kind}")
                        }
                        if (byId.containsKey(entry.id)) {
                            dup++
                            warns.add("$assetPath:${idx + 1}: дубликат id=${entry.id}")
                            return@forEachIndexed
                        }
                        addEntry(entry)
                    } catch (t: Throwable) {
                        inv++
                        warns.add("$assetPath:${idx + 1}: битый JSON")
                        Logx.w("bad kb line in $assetPath:$idx", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Logx.w("failed reading $assetPath", t)
            warns.add("Не прочитан $assetPath: ${t.message}")
        }
        return dup to inv
    }

    private fun addEntry(e: KbEntry) {
        val idx = entries.size
        entries.add(e)
        byId[e.id] = e
        byKind.getOrPut(e.kind) { ArrayList() }.add(idx)
        for (st in e.states) {
            byState.getOrPut(st) { ArrayList() }.add(idx)
        }
        e.termFreqs
    }

    private fun rebuildBm25Index() {
        postings.clear()
        idfMap.clear()
        if (entries.isEmpty()) return
        var sumLen = 0L
        for (i in entries.indices) {
            val e = entries[i]
            sumLen += e.docLength
            for (term in e.termFreqs.keys) {
                postings.getOrPut(term) { ArrayList() }.add(i)
            }
        }
        avgDocLen = (sumLen.toDouble() / entries.size).coerceAtLeast(1.0)
        val n = entries.size.toDouble()
        for ((term, docList) in postings) {
            val df = docList.size.toDouble()
            idfMap[term] = ln(1.0 + (n - df + 0.5) / (df + 0.5))
        }
    }

    /** Определяет доминирующие эмоциональные и когнитивные состояния по тексту и времени суток. */
    fun inferStates(text: String, nowMillis: Long = Timex.now()): Set<String> {
        val out = LinkedHashSet<String>()
        val low = Norm.looseKey(text)
        val stems = Norm.stemTokens(text)

        for ((state, signals) in STATE_SIGNALS) {
            val phraseHit = signals.phrases.any { low.contains(it) }
            val stemHit = stems.any { s ->
                signals.exactStems.contains(s) || signals.prefixes.any { p -> s.startsWith(p) }
            }
            if (phraseHit || stemHit) out.add(state)
        }

        if (Timex.isNightRisk(nowMillis)) {
            if (out.isEmpty()) out.add("rumination")
            out.add("night")
        }
        if (out.isEmpty()) out.add("deadend")
        return out
    }

    /** Гибридный BM25 + мета-ранжировщик по всей базе. */
    fun retrieve(query: Query): List<KbEntry> {
        ensureLoaded()
        if (entries.isEmpty()) return emptyList()

        val queryStems = Norm.stemTokens(query.userText)
        val targetStates = if (query.states.isNotEmpty()) query.states else inferStates(query.userText)

        val candidateIndices = LinkedHashSet<Int>()
        for (st in targetStates) {
            byState[st]?.let { candidateIndices.addAll(it) }
        }
        for (term in queryStems) {
            postings[term]?.let { candidateIndices.addAll(it) }
        }
        if (candidateIndices.size < 30) {
            for (i in entries.indices) candidateIndices.add(i)
        }

        val k1 = 1.4
        val b = 0.75
        val scored = ArrayList<Pair<KbEntry, Double>>(candidateIndices.size)

        for (idx in candidateIndices) {
            val e = entries[idx]
            if (e.id in query.excludeIds) continue

            var score = 0.0
            for (st in e.states) {
                if (st in targetStates) score += 3.2
            }
            if (e.tone in query.preferredTones) score += 2.2
            if (e.kind in query.preferredKinds) score += 2.0

            if (queryStems.isNotEmpty()) {
                val dl = e.docLength.toDouble()
                var bm25 = 0.0
                for (qTerm in queryStems) {
                    val tf = (e.termFreqs[qTerm] ?: 0).toDouble()
                    if (tf <= 0.0) continue
                    val idf = idfMap[qTerm] ?: 0.5
                    bm25 += idf * (tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * dl / avgDocLen))
                }
                score += bm25 * 2.4
            }

            if ("night" in e.states && "night" !in targetStates) score -= 2.5

            if (score > 0.0 || query.userText.isBlank()) {
                scored.add(e to score)
            }
        }

        scored.sortByDescending { it.second }

        if (!query.diversify) {
            return scored.take(query.limit).map { it.first }
        }

        val picked = ArrayList<KbEntry>(query.limit)
        val kindCount = HashMap<String, Int>()
        val schoolCount = HashMap<String, Int>()
        val maxPerKind = maxOf(1, (query.limit + 2) / 3)

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
                limit = 14,
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
        sb.append("# ОПОРНАЯ БАЗА ЗНАНИЙ (выбери 1–2 самых метких инструмента под контекст, не перечисляй всё подряд)\n")
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
                kind = "model",
                school = "stoicism",
                states = listOf("anxiety", "deadend", "failure", "decision"),
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

    private data class StateSignal(
        val phrases: List<String>,
        val prefixes: List<String>,
        val exactStems: Set<String> = emptySet()
    )

    private companion object {
        val VALID_KINDS = setOf("cbt", "philosophy", "practice", "breakthrough", "model", "bias", "learning")

        val STATE_SIGNALS: Map<String, StateSignal> = mapOf(
            "depression" to StateSignal(
                phrases = listOf("ничего не радует", "нет смысла", "все серое", "на дне", "опустились руки"),
                prefixes = listOf("депресс", "бессмыслен", "безнадеж", "пустот", "ангедон", "отчаян", "уныни", "тоск"),
                exactStems = setOf("дно")
            ),
            "deadend" to StateSignal(
                phrases = listOf("выхода нет", "не знаю что делать", "в тупике", "куда идти", "все рухнуло", "безвыход"),
                prefixes = listOf("тупик", "застря", "безвыход", "безысход", "распуть", "рухнул")
            ),
            "apathy" to StateSignal(
                phrases = listOf("не могу встать", "ноль сил", "нет энергии", "ничего не хочу", "нет сил"),
                prefixes = listOf("апат", "лен", "вял", "обессил", "истощ", "слабост", "залип"),
                exactStems = setOf("лень", "слаб")
            ),
            "procrastination" to StateSignal(
                phrases = listOf("откладываю на потом", "не могу начать", "сижу в телефоне", "тяжело начать"),
                prefixes = listOf("прокрастин", "откладыва", "саботаж", "залипа", "отмаз")
            ),
            "panic" to StateSignal(
                phrases = listOf("паническая атака", "не могу дышать", "сердце колотится", "накрыло", "трясет"),
                prefixes = listOf("паник", "задыха", "колот", "тряс", "ужас", "захлест")
            ),
            "anxiety" to StateSignal(
                phrases = listOf("вдруг не получится", "что если", "боюсь что", "накручиваю себя"),
                prefixes = listOf("тревог", "тревож", "страх", "страшн", "боюсь", "волну", "пережива", "нервнич", "катастроф")
            ),
            "rumination" to StateSignal(
                phrases = listOf("кручу в голове", "не могу уснуть", "одни и те же мысли", "пережевываю"),
                prefixes = listOf("руминац", "накручива", "загоня", "навязчив", "бессонниц", "мыслемешалк", "оверсинк")
            ),
            "guilt" to StateSignal(
                phrases = listOf("сам во всем виноват", "я неудачник", "я слабак", "ненавижу себя", "стыдно за себя"),
                prefixes = listOf("виноват", "стыд", "ничтожеств", "слабак", "лузер", "неудачн", "самобичеван"),
                exactStems = setOf("вин", "вина", "слаб")
            ),
            "anger" to StateSignal(
                phrases = listOf("все бесит", "меня достало", "срываюсь на", "несправедливо"),
                prefixes = listOf("бесит", "злост", "злюсь", "ярост", "ненавист", "раздража", "гнев", "взбесил", "достал")
            ),
            "loneliness" to StateSignal(
                phrases = listOf("никому не нужен", "совсем один", "меня не понимают", "пусто вокруг"),
                prefixes = listOf("одинок", "одиночеств", "бросил", "ненужн", "изоляц", "покинул")
            ),
            "burnout" to StateSignal(
                phrases = listOf("сил больше нет", "выжат как лимон", "устал от всего", "нет ресурса"),
                prefixes = listOf("выгор", "устал", "выжат", "перегруз", "измота", "переутом")
            ),
            "failure" to StateSignal(
                phrases = listOf("все испортил", "опять облажался", "не получилось", "все зря"),
                prefixes = listOf("провал", "облажал", "ошибк", "потерпел", "сорвал", "сломал")
            ),
            "decision" to StateSignal(
                phrases = listOf("не могу выбрать", "принять решение", "стоит ли", "какова вероятность", "как решить"),
                prefixes = listOf("решени", "выбор", "вероятн", "прогноз", "риск", "ставк", "альтернатив", "сомнева")
            ),
            "logic" to StateSignal(
                phrases = listOf("проверь аргумент", "в чем ошибка", "логично ли", "как доказать", "спор"),
                prefixes = listOf("аргумент", "логик", "искажен", "софизм", "опроверг", "гипотез", "критич", "факт")
            ),
            "systems" to StateSignal(
                phrases = listOf("замкнутый круг", "все связано", "точка рычага", "почему повторяется"),
                prefixes = listOf("систем", "обратн", "рычаг", "последств", "баланс", "горлышк", "метрик", "стимул")
            ),
            "learning" to StateSignal(
                phrases = listOf("как выучить", "быстро забываю", "сложная книга", "разобрать статью", "не понимаю тему"),
                prefixes = listOf("учить", "обучен", "запомн", "книг", "стать", "лекци", "конспект", "фейнман", "фокус")
            )
        )
    }
}
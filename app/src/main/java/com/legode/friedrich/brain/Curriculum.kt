package com.legode.friedrich.brain

import kotlin.math.roundToInt

/**
 * Программа A0–A1: 47 тем, у каждой — шпаргалка (правила + таблицы).
 *
 * Это единый источник правды:
 *  • «Справочник» в приложении показывает шпаргалки как есть;
 *  • в системный промпт Фридриха они попадают как эталон, с которым он сверяется;
 *  • «Досье» хранит освоенность каждой темы (0–100) и строит по ним уровень и план.
 */
data class Topic(
    val id: String,
    /** Блок программы — см. Curriculum.blocks. */
    val block: String,
    /** A0 | A1.1 | A1.2 */
    val level: String,
    val title: String,
    val de: String,
    /** Подстроки в нижнем регистре: по ним определяется, что разговор касается темы. */
    val keywords: List<String>,
    /** Шпаргалка в Markdown. */
    val sheet: String
)

data class LevelEstimate(
    val label: String,
    val overall: Int,
    val a0: Int,
    val a11: Int,
    val a12: Int
)

object Curriculum {

    data class Block(val id: String, val title: String, val emoji: String)

    val blocks: List<Block> = listOf(
        Block("start", "Старт: звуки, приветствия, числа", "🚀"),
        Block("sentence", "Как строится предложение", "🧱"),
        Block("noun", "Существительное, артикли, падежи", "📦"),
        Block("verb", "Глаголы: модальные, приставки, прошедшее", "⚙️"),
        Block("timeplace", "Время, место, количество", "🧭"),
        Block("life", "Ситуации и темы A1", "🏙️")
    )

    /** Все темы в порядке блоков (так они показаны в справочнике). */
    val topics: List<Topic> =
        startTopics + sentenceTopics + nounTopics + verbTopics + timePlaceTopics + lifeTopics

    private val byId: Map<String, Topic> = topics.associateBy { it.id }

    /** Порядок прохождения: грамматика перемежается с жизненными темами. */
    val path: List<String> = listOf(
        // A0
        "alphabet", "greetings", "intro", "pronouns_nom", "sein_haben", "numbers", "classroom", "du_sie",
        "verb_present", "w_questions", "word_order", "yes_no", "gender_articles", "indef_articles", "family",
        "negation",
        // A1.1
        "plural", "nom_akk", "food", "strong_verbs", "time_clock", "days_dates", "prep_time", "daily",
        "separable_verbs", "modal_verbs", "likes", "possessive", "shopping", "quantities", "conjunctions",
        "freetime",
        // A1.2
        "dative", "pronouns_cases", "prep_place", "city", "home", "imperative", "perfekt", "praeteritum",
        "work", "health", "appointments", "adjectives", "weather_clothes", "forms_letters", "exam_a1"
    )

    fun topic(id: String): Topic? = byId[id]

    fun inBlock(blockId: String): List<Topic> = topics.filter { it.block == blockId }

    /**
     * Модель иногда присылает id темы неточно («akkusativ», «Perfekt», «w-fragen»).
     * Пытаемся понять, что имелось в виду.
     */
    fun resolve(raw: String): Topic? {
        val key = raw.trim().lowercase().replace(NOT_WORD, "_").trim('_')
        if (key.isEmpty()) return null
        byId[key]?.let { return it }
        val flat = key.replace("_", "")
        topics.firstOrNull { it.id.replace("_", "") == flat }?.let { return it }
        ALIASES[flat]?.let { alias -> byId[alias]?.let { return it } }
        val spaced = key.replace('_', ' ')
        topics.firstOrNull { flatten(it.de).contains(spaced) || flatten(it.title).contains(spaced) }
            ?.let { return it }
        if (key.length >= 4) {
            topics.firstOrNull { it.id.contains(key) || key.contains(it.id) }?.let { return it }
        }
        return match(spaced, 1).firstOrNull()
    }

    private val NOT_WORD = Regex("[^\\p{L}\\p{Nd}]+")

    private fun flatten(s: String): String = s.lowercase().replace(NOT_WORD, " ").trim()

    private val ALIASES: Map<String, String> = mapOf(
        "akkusativ" to "nom_akk", "nominativ" to "nom_akk", "kasus" to "nom_akk", "dativ" to "dative",
        "wfragen" to "w_questions", "fragewörter" to "w_questions", "modalverben" to "modal_verbs",
        "trennbareverben" to "separable_verbs", "artikel" to "gender_articles", "genus" to "gender_articles",
        "zahlen" to "numbers", "uhrzeit" to "time_clock", "uhr" to "time_clock", "präsens" to "verb_present",
        "praesens" to "verb_present", "konjugation" to "verb_present", "possessivartikel" to "possessive",
        "possessivpronomen" to "possessive", "imperativ" to "imperative", "verneinung" to "negation",
        "wortstellung" to "word_order", "satzbau" to "word_order", "präteritum" to "praeteritum",
        "familie" to "family", "essen" to "food", "einkaufen" to "shopping", "wohnen" to "home",
        "wohnung" to "home", "stadt" to "city", "verkehr" to "city", "tagesablauf" to "daily",
        "freizeit" to "freetime", "hobbys" to "freetime", "arbeit" to "work", "beruf" to "work",
        "gesundheit" to "health", "arzt" to "health", "termin" to "appointments", "termine" to "appointments",
        "wetter" to "weather_clothes", "kleidung" to "weather_clothes", "formular" to "forms_letters",
        "brief" to "forms_letters", "prüfung" to "exam_a1", "pruefung" to "exam_a1",
        "pronomen" to "pronouns_nom", "personalpronomen" to "pronouns_nom", "adjektive" to "adjectives",
        "wechselpräpositionen" to "prep_place", "ortspräpositionen" to "prep_place",
        "zeitpräpositionen" to "prep_time", "datum" to "days_dates", "wochentage" to "days_dates",
        "begrüßung" to "greetings", "vorstellung" to "intro", "aussprache" to "alphabet", "mengen" to "quantities",
        "konjunktionen" to "conjunctions", "vokalwechsel" to "strong_verbs", "starkeverben" to "strong_verbs",
        "gern" to "likes", "doch" to "yes_no", "janeindoch" to "yes_no", "seinhaben" to "sein_haben"
    )

    /** Темы, которых касается текст (по ключевым словам), самые релевантные — первыми. */
    fun match(text: String, limit: Int): List<Topic> {
        val low = " " + text.lowercase() + " "
        if (low.isBlank()) return emptyList()
        return topics
            .map { t -> t to t.keywords.count { k -> low.contains(k) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /** Компактный указатель для промпта: по нему модель выбирает id темы для отметок. */
    fun index(mastery: Map<String, Int>): String {
        val sb = StringBuilder()
        var lastLevel = ""
        for (id in path) {
            val t = byId[id] ?: continue
            if (t.level != lastLevel) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append("[").append(t.level).append("] ")
                lastLevel = t.level
            } else {
                sb.append("; ")
            }
            sb.append(t.id).append(" = ").append(t.title).append(" (").append(mastery[t.id] ?: 0).append("%)")
        }
        return sb.toString()
    }

    fun sheetBlock(t: Topic): String = "### ${t.id} — ${t.title} (${t.de}) [${t.level}]\n${t.sheet}"

    /** Полный справочник A0–A1 для системного промпта. */
    val fullReference: String by lazy {
        topics.joinToString("\n\n") { sheetBlock(it) }
    }

    fun sheetsFor(ids: Collection<String>): String =
        ids.mapNotNull { byId[it] }.distinct().joinToString("\n\n") { sheetBlock(it) }

    /** Оценка уровня по карте освоенности тем. */
    fun estimate(mastery: Map<String, Int>): LevelEstimate {
        fun avg(level: String): Int {
            val list = topics.filter { it.level == level }
            if (list.isEmpty()) return 0
            return list.map { mastery[it.id] ?: 0 }.average().roundToInt()
        }

        val a0 = avg("A0")
        val a11 = avg("A1.1")
        val a12 = avg("A1.2")
        val overall = topics.map { mastery[it.id] ?: 0 }.average().roundToInt()
        val label = when {
            a0 < 55 -> "A0"
            a11 < 55 -> "A1.1"
            a12 < 60 -> "A1.2"
            else -> "A1"
        }
        return LevelEstimate(label, overall, a0, a11, a12)
    }

    /** Ближайшие темы по программе: первые по маршруту, что ещё не освоены (< 70%). */
    fun focus(mastery: Map<String, Int>, count: Int = 3): List<Topic> =
        path.mapNotNull { byId[it] }.filter { (mastery[it.id] ?: 0) < 70 }.take(count)

    /** Самые слабые темы среди тех, что уже практиковались. */
    fun weakest(mastery: Map<String, Int>, touched: Set<String>, count: Int = 3): List<Topic> =
        path.mapNotNull { byId[it] }
            .filter { it.id in touched && (mastery[it.id] ?: 0) < 60 }
            .sortedBy { mastery[it.id] ?: 0 }
            .take(count)

    /** Стартовая карта освоенности по самооценке: 0 — с нуля, 1 — немного знаю, 2 — почти A1. */
    fun seedMastery(selfLevel: Int): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (t in topics) {
            out[t.id] = when (selfLevel) {
                1 -> when (t.level) {
                    "A0" -> 45
                    "A1.1" -> 10
                    else -> 0
                }

                2 -> when (t.level) {
                    "A0" -> 70
                    "A1.1" -> 45
                    else -> 20
                }

                else -> 0
            }
        }
        return out
    }
}

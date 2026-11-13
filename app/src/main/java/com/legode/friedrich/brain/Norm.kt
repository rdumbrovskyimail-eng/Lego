package com.legode.friedrich.brain

import java.util.Locale

/** Нормализация строк: ключи словаря, ошибок и фактов (чтобы не плодить дубли). */
object Norm {

    private val ARTICLES = listOf("der ", "die ", "das ", "ein ", "eine ", "einen ", "einem ", "einer ")
    private val PARENS = Regex("\\([^)]*\\)")
    private val SPACES = Regex("\\s+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{Nd} ]")

    /** «der Tisch (die Tische)» → «tisch»; «Guten Morgen!» → «guten morgen». */
    fun vocabKey(de: String): String {
        var s = de.replace(PARENS, " ").lowercase(Locale.GERMAN).trim()
        s = s.replace(SPACES, " ")
        for (a in ARTICLES) {
            if (s.startsWith(a) && s.length > a.length) {
                s = s.substring(a.length)
                break
            }
        }
        s = s.trim().trimEnd('.', '!', '?', ',', ';', ':', '…')
        s = s.trimStart('„', '"', '«', '-', '–', '•', '*').trimEnd('“', '"', '»')
        return s.trim()
    }

    /** Ключ ошибки/факта: только буквы и цифры в нижнем регистре, один пробел между словами. */
    fun looseKey(text: String): String =
        text.lowercase(Locale.GERMAN).replace(NOT_WORD, " ").replace(SPACES, " ").trim()

    /** Грубая оценка «это одно и то же» для фактов: один ключ целиком входит в другой. */
    fun similar(a: String, b: String): Boolean {
        val x = looseKey(a)
        val y = looseKey(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 12 && long.contains(short)
    }

    /** Достаёт артикль из формы «der Tisch»; пусто, если артикля нет. */
    fun articleOf(de: String): String {
        val low = de.trim().lowercase(Locale.GERMAN)
        return when {
            low.startsWith("der ") -> "der"
            low.startsWith("die ") -> "die"
            low.startsWith("das ") -> "das"
            else -> ""
        }
    }

    /** Убирает markdown-разметку — для превью в списке чатов и текста уведомлений. */
    fun plain(md: String): String {
        var s = md
        s = s.replace(Regex("```[\\s\\S]*?```"), " ")
        s = s.replace(Regex("^\\s*\\|?\\s*:?-{2,}.*$", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        s = s.replace("**", "").replace("__", "").replace("~~", "").replace("`", "")
        s = s.replace(Regex("(?<![\\p{L}\\p{Nd}*])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?![\\p{L}\\p{Nd}*])"), "$1")
        s = s.replace(Regex("^\\s*[-•]\\s+", RegexOption.MULTILINE), "• ")
        s = s.replace('|', ' ')
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s.trim()
    }

    fun oneLine(text: String, max: Int): String {
        val flat = plain(text).replace('\n', ' ').replace(SPACES, " ").trim()
        return if (flat.length <= max) flat else flat.substring(0, max - 1).trimEnd() + "…"
    }

    /** Латинские (немецкие) фрагменты смешанного RU/DE-текста — их читает вслух TTS. */
    fun germanParts(text: String): String {
        val flat = plain(text)
        val out = StringBuilder()
        val chunk = StringBuilder()
        fun flush() {
            val c = chunk.toString().trim().trimStart { !it.isLetterOrDigit() }.trimEnd { !it.isLetterOrDigit() && it != '?' && it != '!' && it != '.' }
            // фрагмент считаем немецким, если в нём есть хотя бы одно слово из 2+ латинских букв
            if (Regex("[A-Za-zÄÖÜäöüß]{2,}").containsMatchIn(c)) {
                if (out.isNotEmpty()) out.append(if (out.last() == '.' || out.last() == '?' || out.last() == '!') " " else ". ")
                out.append(c)
            }
            chunk.setLength(0)
        }
        for (ch in flat) {
            val cyr = ch in 'Ѐ'..'ӿ'
            when {
                cyr -> flush()
                ch == '\n' -> flush()
                else -> chunk.append(ch)
            }
        }
        flush()
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }
}

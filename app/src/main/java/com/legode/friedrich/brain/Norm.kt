package com.legode.friedrich.brain

import java.util.Locale

/** Нормализация строк: ключи якорей силы, ловушек ума, фактов и очистка текста. */
object Norm {

    private val RU = Locale.forLanguageTag("ru")
    private val SPACES = Regex("\\s+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{Nd} ]")
    private val NON_SPEAKABLE = Regex("[^\\p{L}\\p{Nd}\\s.,!?:;—–\\-()«»\"'/%+]")

    /** Ключ для якоря / цитаты / рефрейма: убирает кавычки, знаки препинания и дубли. */
    fun anchorKey(text: String): String {
        val cleaned = text
            .trim()
            .trimStart('„', '"', '«', '-', '–', '•', '*', '✦', '⚓')
            .trimEnd('“', '"', '»', '.', '!', '?')
        return looseKey(cleaned).take(90)
    }

    /** Ключ ловушки/факта: только буквы и цифры в нижнем регистре, один пробел между словами. */
    fun looseKey(text: String): String =
        text.lowercase(RU).replace(NOT_WORD, " ").replace(SPACES, " ").trim()

    /** Грубая оценка «это одно и то же» для фактов и убеждений: один ключ входит в другой. */
    fun similar(a: String, b: String): Boolean {
        val x = looseKey(a)
        val y = looseKey(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 12 && long.contains(short)
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

    /** Очищает текст для плавного чтения системным синтезатором речи (убирает эмодзи и лишние знаки). */
    fun speakable(text: String): String {
        val lines = plain(text)
            .replace("→", ", значит ")
            .replace("• ", "")
            .replace(NON_SPEAKABLE, " ")
            .lines()
            .map { it.replace(SPACES, " ").trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        val out = StringBuilder()
        for (line in lines) {
            if (out.isNotEmpty()) {
                val last = out.last()
                out.append(if (last == '.' || last == '!' || last == '?' || last == ':') " " else ". ")
            }
            out.append(line)
        }
        return out.toString().replace(SPACES, " ").trim()
    }
}
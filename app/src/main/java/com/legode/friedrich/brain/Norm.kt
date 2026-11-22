package com.legode.friedrich.brain

import java.util.Locale

/**
 * Нормализация строк:
 *  • ключи якорей силы и когнитивных ловушек ума;
 *  • облегчённый русский/английский стемминг и токенизация для гибридного BM25-поиска;
 *  • очистка markdown-разметки, ссылок и тегов пауз для превью, уведомлений и TTS.
 */
object Norm {

    private val RU = Locale.forLanguageTag("ru")
    private val SPACES = Regex("\\s+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{Nd} ]")
    private val MD_LINK = Regex("\\[([^\\]]+)]\\((https?://[^)]+)\\)")
    private val NON_SPEAKABLE = Regex("[^\\p{L}\\p{Nd}\\s.,!?:;—–\\-()«»\"'/%+]")
    private val PAUSE_MARK = Regex("\\[(?:пауза|pause)(?:\\s+\\d{1,2}\\s*(?:с|сек|s)?)?\\]", RegexOption.IGNORE_CASE)

    private val STOP_WORDS = setOf(
        "и", "в", "во", "на", "с", "со", "но", "а", "или", "да", "нет", "не", "ни",
        "я", "мы", "ты", "вы", "он", "она", "они", "оно", "мне", "меня", "мной",
        "тебе", "тебя", "себе", "себя", "свой", "моя", "мой", "мое", "мои",
        "что", "как", "кто", "где", "когда", "почему", "зачем", "чтобы", "если",
        "это", "этот", "эта", "эти", "того", "том", "тем", "все", "вся",
        "для", "из", "по", "к", "ко", "у", "о", "об", "от", "за", "до", "без", "при",
        "уже", "еще", "очень", "просто", "вообще", "прямо", "сейчас", "про", "бы", "же", "ли",
        "the", "and", "or", "to", "of", "in", "for", "is", "are", "was", "that", "this"
    )

    private val RU_ENDINGS = listOf(
        "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими",
        "ость", "есть", "ение", "ание", "ться", "тся", "ивать", "ывать",
        "ить", "ать", "ять", "еть", "уть", "ыть",
        "ая", "яя", "ое", "ее", "ые", "ие", "ой", "ей", "ий", "ый",
        "ов", "ев", "ам", "ям", "ах", "ях", "ом", "ем", "ут", "ют",
        "а", "я", "о", "е", "и", "ы", "у", "ю", "ь"
    )

    fun normalizeYo(text: String): String = text.replace('ё', 'е').replace('Ё', 'Е')

    /** Ключ для якоря / цитаты / рефрейма: убирает кавычки, знаки препинания и дубли. */
    fun anchorKey(text: String): String {
        val cleaned = text
            .trim()
            .trimStart('„', '"', '«', '-', '–', '—', '•', '*', '✦', '⚓')
            .trimEnd('“', '"', '»', '.', '!', '?')
        return looseKey(cleaned).take(90)
    }

    /** Ключ для когнитивной ловушки ума: связка искажённой мысли и трезвого антидота. */
    fun trapKey(thought: String, antidote: String): String =
        (looseKey(thought).take(110) + ">" + looseKey(antidote).take(110)).take(240)

    /** Ключ ловушки/факта: только буквы и цифры в нижнем регистре, `ё→е`, один пробел. */
    fun looseKey(text: String): String =
        normalizeYo(text).lowercase(RU).replace(NOT_WORD, " ").replace(SPACES, " ").trim()

    /** Грубая оценка «это одно и то же» для фактов и убеждений: один ключ входит в другой. */
    fun similar(a: String, b: String): Boolean {
        val x = looseKey(a)
        val y = looseKey(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 12 && long.contains(short)
    }

    /** Облегчённый русский стемминг (с сохранением базового корня от 4 символов). */
    fun stemRu(word: String): String {
        val w = normalizeYo(word).lowercase(RU).trim()
        if (w.length < 5) return w
        for (suf in RU_ENDINGS) {
            if (w.endsWith(suf) && w.length - suf.length >= 4) {
                return w.substring(0, w.length - suf.length)
            }
        }
        return w
    }

    /** Токены без стоп-слов (в исходной нормализованной форме). */
    fun tokens(text: String): List<String> {
        val flat = looseKey(text)
        if (flat.isEmpty()) return emptyList()
        return flat.split(' ').filter { it.length >= 2 && it !in STOP_WORDS }
    }

    /** Список стеммов с сохранением повторений (для частотного веса BM25). */
    fun stemList(text: String): List<String> {
        val list = tokens(text)
        if (list.isEmpty()) return emptyList()
        val out = ArrayList<String>(list.size)
        for (w in list) out.add(stemRu(w))
        return out
    }

    /** Множество уникальных стеммированных значимых корней для быстрого RAG-матчинга. */
    fun stemTokens(text: String): Set<String> {
        val list = tokens(text)
        if (list.isEmpty()) return emptySet()
        val out = LinkedHashSet<String>(list.size)
        for (w in list) out.add(stemRu(w))
        return out
    }

    /** Убирает markdown-разметку, ссылки и технические теги — для превью и уведомлений. */
    fun plain(md: String): String {
        var s = md.replace(PAUSE_MARK, " ")
        s = s.replace(Regex("```[\\s\\S]*?```"), " ")
        s = s.replace(MD_LINK, "$1")
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
        return if (flat.length <= max) flat else flat.substring(0, (max - 1).coerceAtLeast(1)).trimEnd() + "…"
    }

    /** Обрезка текста без разрыва посреди слова, когда это возможно. */
    fun truncateWords(text: String, max: Int): String {
        val clean = text.trim()
        if (clean.length <= max) return clean
        val cut = clean.substring(0, max)
        val space = cut.lastIndexOf(' ')
        val safe = if (space > max / 2) cut.substring(0, space) else cut
        return safe.trimEnd() + "…"
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
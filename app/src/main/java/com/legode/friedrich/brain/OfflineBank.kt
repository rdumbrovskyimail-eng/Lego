package com.legode.friedrich.brain

import com.legode.friedrich.core.Timex
import kotlin.random.Random

/**
 * Запас заданий «на чёрный день»: когда нет сети, ключа или API отвечает ошибкой,
 * Friedrich всё равно пишет — из встроенного банка слов и шаблонов. Он не замолкает никогда.
 */
object CoreVocab {

    data class Noun(val de: String, val plural: String, val ru: String) {
        val article: String get() = de.substringBefore(' ')
        val bare: String get() = de.substringAfter(' ')
    }

    data class Verb(val inf: String, val ru: String, val er: String, val partizip: String, val aux: String)

    data class Phrase(val de: String, val ru: String)

    private fun nouns(raw: String): List<Noun> = raw.trim().lines().mapNotNull { line ->
        val p = line.trim().split('|')
        if (p.size == 3) Noun(p[0], p[1], p[2]) else null
    }

    private fun verbs(raw: String): List<Verb> = raw.trim().lines().mapNotNull { line ->
        val p = line.trim().split('|')
        if (p.size == 5) Verb(p[0], p[1], p[2], p[3], p[4]) else null
    }

    private fun phrases(raw: String): List<Phrase> = raw.trim().lines().mapNotNull { line ->
        val p = line.trim().split('|')
        if (p.size == 2) Phrase(p[0], p[1]) else null
    }

    val NOUNS: List<Noun> = nouns(
        """
        der Tisch|die Tische|стол
        der Stuhl|die Stühle|стул
        das Bett|die Betten|кровать
        der Schrank|die Schränke|шкаф
        die Lampe|die Lampen|лампа
        das Fenster|die Fenster|окно
        die Tür|die Türen|дверь
        das Haus|die Häuser|дом
        die Wohnung|die Wohnungen|квартира
        das Zimmer|die Zimmer|комната
        die Küche|die Küchen|кухня
        der Schlüssel|die Schlüssel|ключ
        das Buch|die Bücher|книга
        das Heft|die Hefte|тетрадь
        der Stift|die Stifte|ручка, карандаш
        die Tasche|die Taschen|сумка
        das Handy|die Handys|мобильный телефон
        der Computer|die Computer|компьютер
        das Auto|die Autos|машина
        der Bus|die Busse|автобус
        der Zug|die Züge|поезд
        das Fahrrad|die Fahrräder|велосипед
        die Straße|die Straßen|улица
        die Stadt|die Städte|город
        das Land|die Länder|страна
        der Bahnhof|die Bahnhöfe|вокзал
        die Haltestelle|die Haltestellen|остановка
        die Schule|die Schulen|школа
        der Kurs|die Kurse|курс
        der Lehrer|die Lehrer|учитель
        die Lehrerin|die Lehrerinnen|учительница
        der Arzt|die Ärzte|врач
        der Beruf|die Berufe|профессия
        das Büro|die Büros|офис
        die Firma|die Firmen|фирма
        der Kollege|die Kollegen|коллега
        der Preis|die Preise|цена
        die Rechnung|die Rechnungen|счёт
        der Supermarkt|die Supermärkte|супермаркет
        das Geschäft|die Geschäfte|магазин
        die Apotheke|die Apotheken|аптека
        das Brot|die Brote|хлеб
        das Brötchen|die Brötchen|булочка
        das Ei|die Eier|яйцо
        der Apfel|die Äpfel|яблоко
        die Banane|die Bananen|банан
        die Tomate|die Tomaten|помидор
        die Kartoffel|die Kartoffeln|картофелина
        der Fisch|die Fische|рыба
        der Saft|die Säfte|сок
        der Kuchen|die Kuchen|пирог
        die Familie|die Familien|семья
        die Mutter|die Mütter|мать
        der Vater|die Väter|отец
        der Bruder|die Brüder|брат
        die Schwester|die Schwestern|сестра
        der Sohn|die Söhne|сын
        die Tochter|die Töchter|дочь
        das Kind|die Kinder|ребёнок
        der Mann|die Männer|мужчина, муж
        die Frau|die Frauen|женщина, жена
        der Freund|die Freunde|друг
        die Freundin|die Freundinnen|подруга
        der Name|die Namen|имя
        die Adresse|die Adressen|адрес
        die Nummer|die Nummern|номер
        der Tag|die Tage|день
        die Woche|die Wochen|неделя
        der Monat|die Monate|месяц
        das Jahr|die Jahre|год
        die Stunde|die Stunden|час (60 минут)
        die Uhr|die Uhren|часы
        der Abend|die Abende|вечер
        die Nacht|die Nächte|ночь
        der Termin|die Termine|назначенная встреча
        der Kopf|die Köpfe|голова
        die Hand|die Hände|рука (кисть)
        der Fuß|die Füße|нога (стопа)
        das Auge|die Augen|глаз
        die Jacke|die Jacken|куртка
        die Hose|die Hosen|брюки
        der Schuh|die Schuhe|ботинок
        das Hemd|die Hemden|рубашка
        das Kleid|die Kleider|платье
        die Frage|die Fragen|вопрос
        die Antwort|die Antworten|ответ
        das Wort|die Wörter|слово
        der Satz|die Sätze|предложение
        die Sprache|die Sprachen|язык
        das Problem|die Probleme|проблема
        die Fahrkarte|die Fahrkarten|билет (проездной)
        der Brief|die Briefe|письмо
        das Formular|die Formulare|анкета
        der Hund|die Hunde|собака
        die Katze|die Katzen|кошка
        der Park|die Parks|парк
        das Kino|die Kinos|кинотеатр
        das Restaurant|die Restaurants|ресторан
        das Hotel|die Hotels|гостиница
        die Bank|die Banken|банк
        """
    )

    val VERBS: List<Verb> = verbs(
        """
        sein|быть|ist|gewesen|sein
        haben|иметь|hat|gehabt|haben
        machen|делать|macht|gemacht|haben
        gehen|идти|geht|gegangen|sein
        kommen|приходить|kommt|gekommen|sein
        fahren|ехать|fährt|gefahren|sein
        wohnen|жить (проживать)|wohnt|gewohnt|haben
        arbeiten|работать|arbeitet|gearbeitet|haben
        lernen|учить|lernt|gelernt|haben
        sprechen|говорить|spricht|gesprochen|haben
        verstehen|понимать|versteht|verstanden|haben
        lesen|читать|liest|gelesen|haben
        schreiben|писать|schreibt|geschrieben|haben
        hören|слушать|hört|gehört|haben
        sehen|видеть|sieht|gesehen|haben
        essen|есть|isst|gegessen|haben
        trinken|пить|trinkt|getrunken|haben
        kaufen|покупать|kauft|gekauft|haben
        bezahlen|платить|bezahlt|bezahlt|haben
        brauchen|нуждаться|braucht|gebraucht|haben
        nehmen|брать|nimmt|genommen|haben
        geben|давать|gibt|gegeben|haben
        helfen|помогать|hilft|geholfen|haben
        fragen|спрашивать|fragt|gefragt|haben
        suchen|искать|sucht|gesucht|haben
        finden|находить|findet|gefunden|haben
        schlafen|спать|schläft|geschlafen|haben
        aufstehen|вставать|steht auf|aufgestanden|sein
        anrufen|звонить|ruft an|angerufen|haben
        einkaufen|делать покупки|kauft ein|eingekauft|haben
        kochen|готовить|kocht|gekocht|haben
        spielen|играть|spielt|gespielt|haben
        besuchen|навещать|besucht|besucht|haben
        bleiben|оставаться|bleibt|geblieben|sein
        treffen|встречать|trifft|getroffen|haben
        wissen|знать (факт)|weiß|gewusst|haben
        warten|ждать|wartet|gewartet|haben
        fliegen|лететь|fliegt|geflogen|sein
        laufen|бежать|läuft|gelaufen|sein
        bringen|приносить|bringt|gebracht|haben
        """
    )

    val PHRASES: List<Phrase> = phrases(
        """
        Guten Morgen!|Доброе утро!
        Wie geht's?|Как дела?
        Ich verstehe nicht.|Я не понимаю.
        Noch einmal, bitte.|Ещё раз, пожалуйста.
        Was kostet das?|Сколько это стоит?
        Die Rechnung, bitte.|Счёт, пожалуйста.
        Wie spät ist es?|Который час?
        Entschuldigung!|Извините!
        Es tut mir leid.|Мне жаль.
        Ich habe eine Frage.|У меня вопрос.
        Können Sie mir helfen?|Вы можете мне помочь?
        Wo ist die Toilette?|Где туалет?
        Ich habe Hunger.|Я голоден.
        Gute Besserung!|Выздоравливай!
        Bis morgen!|До завтра!
        Keine Ahnung.|Понятия не имею.
        Alles klar.|Всё ясно.
        Ich habe keine Zeit.|У меня нет времени.
        Guten Appetit!|Приятного аппетита!
        Auf Wiedersehen!|До свидания!
        """
    )
}

object OfflineBank {

    data class Ping(val text: String, val quick: List<String>, val kind: String)

    private val OPENERS = listOf(
        "Schnelle Frage ⚡", "Friedrich hier 👋", "Kurzer Test:", "Na, bereit? 😏", "Zwei Minuten Deutsch!",
        "Не расслабляйся 😄", "Aufgabe für dich:", "Так, проверка связи:", "Kleine Pause? Dann los:", "Achtung, Frage!"
    )

    private val NUDGES = listOf(
        "Ты куда пропал? Я тут один с артиклями сижу 😤",
        "Hallo?! Ich warte… ⏳",
        "Молчание — не немецкий. Давай хоть одно слово 🙃",
        "Я не отстану, ты же знаешь 😌",
        "Немецкий сам себя не выучит. Проверено."
    )

    private val PERSONAL = listOf(
        "Was machst du heute Abend? (Что делаешь сегодня вечером?) — ответь по-немецки одним предложением.",
        "Was isst du gern? Напиши по-немецки: **Ich esse gern …**",
        "Wie geht es dir heute? Ответь полным предложением: **Mir geht es …**",
        "Wo wohnst du? Ответь по-немецки полным предложением.",
        "Was trinkst du am Morgen — Kaffee oder Tee? Antworte auf Deutsch 🙂",
        "Wann stehst du auf? Ответь: **Ich stehe um … Uhr auf.**",
        "Hast du Geschwister? Ответь по-немецки (Ja, ich habe … / Nein, ich habe keine …).",
        "Was ist dein Hobby? Напиши: **Mein Hobby ist …**"
    )

    private val W_TASKS = listOf(
        Triple("___ wohnst du? — In Berlin.", listOf("Wo", "Wohin", "Woher"), "Wo"),
        Triple("___ kommst du? — Aus Polen.", listOf("Wo", "Wohin", "Woher"), "Woher"),
        Triple("___ gehst du? — Nach Hause.", listOf("Wo", "Wohin", "Woher"), "Wohin"),
        Triple("___ heißt du? — Anna.", listOf("Wie", "Wer", "Was"), "Wie"),
        Triple("___ kommst du? — Um 8 Uhr.", listOf("Wann", "Wo", "Wie"), "Wann"),
        Triple("___ ist das? — Das ist Paul.", listOf("Wer", "Was", "Wie"), "Wer"),
        Triple("___ kostet das? — 5 Euro.", listOf("Wie viel", "Wie viele", "Wann"), "Wie viel")
    )

    private val NEG_TASKS = listOf(
        "Ich habe ___ Zeit." to "keine",
        "Das ist ___ gut." to "nicht",
        "Ich komme heute ___." to "nicht",
        "Ich habe ___ Auto." to "kein",
        "Er wohnt ___ in Berlin." to "nicht",
        "Wir haben ___ Kinder." to "keine"
    )

    private val SEIN_HABEN = listOf(
        "Ich ___ müde." to "bin", "Du ___ Hunger?" to "hast", "Er ___ 30 Jahre alt." to "ist",
        "Wir ___ in Berlin." to "sind", "Ich ___ einen Bruder." to "habe", "___ du Zeit?" to "Hast",
        "Ihr ___ nett." to "seid", "Sie (она) ___ zwei Kinder." to "hat"
    )

    /**
     * Собирает офлайн-задание.
     * @param dueDe/dueRu слово из словаря ученика, которое пора повторить (если есть) — тогда задание про него.
     * @param unanswered сколько предыдущих «смс» остались без ответа (добавляет ворчание).
     */
    fun make(rnd: Random, dueDe: String?, dueRu: String?, unanswered: Int, now: Long = Timex.now()): Ping {
        val lead = if (unanswered >= 3 && rnd.nextInt(100) < 55) NUDGES.random(rnd) + "\n" else ""
        val opener = OPENERS.random(rnd)

        if (dueDe != null && dueRu != null && rnd.nextInt(100) < 75) {
            val text = if (rnd.nextBoolean()) {
                "$lead🃏 Повторение. Как по-немецки «$dueRu»?"
            } else {
                "$lead🃏 Повторение. Что значит **$dueDe**?"
            }
            return Ping(text, listOf("Не помню 🤷"), "vocab_due")
        }

        return when (rnd.nextInt(12)) {
            0 -> {
                val n = CoreVocab.NOUNS.random(rnd)
                Ping("$lead$opener der, die или das? → ___ **${n.bare}** (${n.ru})", listOf("der", "die", "das"), "article")
            }

            1 -> {
                val n = CoreVocab.NOUNS.random(rnd)
                Ping("$lead$opener множественное число: **${n.de}** → die …?", listOf("Не знаю 🤷"), "plural")
            }

            2 -> {
                val n = CoreVocab.NOUNS.random(rnd)
                Ping("$lead$opener как по-немецки «${n.ru}»? Напиши с артиклем.", listOf("Подсказка"), "translate")
            }

            3 -> {
                val v = CoreVocab.VERBS.random(rnd)
                Ping("$lead$opener спряжение: **${v.inf}** (${v.ru}) → er/sie/es …?", emptyList(), "conjugation")
            }

            4 -> {
                val v = CoreVocab.VERBS.random(rnd)
                Ping(
                    "$lead$opener Perfekt от **${v.inf}** (${v.ru}): Ich ___ ___ . Сначала реши — haben или sein?",
                    listOf("haben", "sein"), "perfekt"
                )
            }

            5 -> {
                val n = listOf(rnd.nextInt(11, 100), rnd.nextInt(100, 1000), rnd.nextInt(21, 100)).random(rnd)
                Ping("$lead$opener напиши число словами по-немецки: **$n**", emptyList(), "number")
            }

            6 -> {
                val h = rnd.nextInt(1, 13)
                val m = listOf(0, 15, 30, 45, 5, 10, 20, 25, 35, 40, 50, 55).random(rnd)
                Ping(
                    "$lead$opener Wie spät ist es? 🕒 **%d:%02d** — напиши по-немецки разговорным способом.".format(h, m),
                    emptyList(), "clock"
                )
            }

            7 -> {
                val t = SEIN_HABEN.random(rnd)
                Ping("$lead$opener sein или haben в нужной форме: **${t.first}**", emptyList(), "sein_haben")
            }

            8 -> {
                val t = W_TASKS.random(rnd)
                Ping("$lead$opener вопросительное слово: **${t.first}**", t.second, "w_question")
            }

            9 -> {
                val t = NEG_TASKS.random(rnd)
                Ping("$lead$opener nicht или kein(e)? **${t.first}**", listOf("nicht", "kein", "keine"), "negation")
            }

            10 -> {
                val p = CoreVocab.PHRASES.random(rnd)
                Ping("$lead$opener как сказать по-немецки: «${p.ru}»", listOf("Подсказка"), "phrase")
            }

            else -> {
                val wd = Timex.dateTime(now).dayOfWeek.value - 1
                if (rnd.nextBoolean()) {
                    Ping("$lead${PERSONAL.random(rnd)}", emptyList(), "question")
                } else {
                    Ping(
                        "$lead$opener Welcher Tag ist heute? Ответь по-немецки полным предложением. (Подсказка: сегодня ${Timex.weekdaysRu[wd]}.)",
                        emptyList(), "today"
                    )
                }
            }
        }
    }
}

package com.legode.friedrich.brain

/* Блок «Предложение»: местоимения, спряжение, sein/haben, порядок слов, вопросы, отрицание, союзы. */

internal val sentenceTopics: List<Topic> = listOf(

    Topic(
        id = "pronouns_nom",
        block = "sentence",
        level = "A0",
        title = "Личные местоимения (кто?)",
        de = "Personalpronomen im Nominativ",
        keywords = listOf("личные местоимения", "personalpronomen", "ich du er", "местоимени"),
        sheet = """
| Лицо | Ед. число | Мн. число |
|---|---|---|
| 1 | ich — я | wir — мы |
| 2 | du — ты | ihr — вы (друзьям) |
| 3 | er — он, sie — она, es — оно | sie — они |
| вежл. | Sie — Вы | Sie — Вы |

- Местоимение заменяет существительное **по роду артикля**, а не по смыслу: der Tisch → **er**, die Lampe → **sie**, das Buch → **es**, die Bücher → **sie**. «Der Tisch ist neu. **Er** ist schön.» «Das Mädchen ist klein. **Es** ist fünf.»
- **man** — «люди вообще», глагол как у er: Hier spricht **man** Deutsch. Wie sagt man das?
- sie / Sie различаем по глаголу: sie komm**t** (она), sie komm**en** (они), Sie komm**en** (Вы).
- Подлежащее в немецком обязательно: «Иду домой» → **Ich** gehe nach Hause. «Холодно» → **Es** ist kalt.
""".trimIndent()
    ),

    Topic(
        id = "verb_present",
        block = "sentence",
        level = "A0",
        title = "Спряжение глаголов в настоящем времени",
        de = "Präsens: regelmäßige Verben",
        keywords = listOf(
            "спряжени", "konjug", "präsens", "окончания глагол", "настоящее время", "презенс", "проспрягай"
        ),
        sheet = """
Инфинитив = основа + **-en**: wohn-en, lern-en, mach-en.

| | окончание | wohnen | arbeiten | heißen |
|---|---|---|---|---|
| ich | -e | wohne | arbeite | heiße |
| du | -st | wohnst | arbeit**e**st | heißt |
| er/sie/es | -t | wohnt | arbeit**e**t | heißt |
| wir | -en | wohnen | arbeiten | heißen |
| ihr | -t | wohnt | arbeit**e**t | heißt |
| sie/Sie | -en | wohnen | arbeiten | heißen |

**Особенности:**
- Основа на **-t, -d** (arbeiten, finden, warten, kosten, antworten) и на -chn, -ffn, -gn (rechnen, öffnen): вставляем **-e-** → du arbeit**e**st, er arbeit**e**t, ihr arbeit**e**t.
- Основа на **-s, -ß, -z, -x** (heißen, reisen, tanzen, sitzen): у du только **-t** → du heißt, du tanzt, du reist.
- Глаголы на -eln: ich samm**le** (sammeln); на -ern: ich wandere.
- wir и sie/Sie всегда = инфинитив: wir wohnen, sie wohnen.

Präsens — это и «сейчас», и «обычно», и **будущее** со словом времени: Morgen **fahre** ich nach Berlin.
Частые глаголы, правильные в Präsens: machen, lernen, wohnen, kommen, gehen, kaufen, kochen, spielen, hören, fragen, sagen, brauchen, suchen, leben, lieben, trinken, schreiben, schwimmen, studieren, besuchen, bezahlen, kosten, arbeiten, warten, öffnen.
""".trimIndent()
    ),

    Topic(
        id = "sein_haben",
        block = "sentence",
        level = "A0",
        title = "sein, haben, werden",
        de = "sein, haben, werden",
        keywords = listOf("sein", "haben", "глагол быть", "глагол иметь", " bin ", " bist ", " hast ", "werden"),
        sheet = """
| | sein (быть) | haben (иметь) | werden (становиться) |
|---|---|---|---|
| ich | bin | habe | werde |
| du | bist | hast | wirst |
| er/sie/es | ist | hat | wird |
| wir | sind | haben | werden |
| ihr | seid | habt | werdet |
| sie/Sie | sind | haben | werden |

**sein:** Ich bin Anna. Ich bin müde. Ich bin 30 Jahre alt. Das ist ein Buch. Wir sind in Berlin. Es ist kalt. Bist du fertig?
**haben + Akkusativ:** Ich habe **einen** Bruder. Hast du Zeit? Ich habe Hunger / Durst / Angst / Glück / frei / Urlaub. Ich habe Kopfschmerzen. Ich habe **keine** Zeit.
**Не путай:** возраст — Ich **bin** 30. Голоден — Ich **habe** Hunger. Мне холодно — **Mir ist** kalt.
«Есть, имеется» — **es gibt** + Akk: Hier gibt es einen Supermarkt.
В прошедшем (A1): ich **war**, du warst, er war, wir waren, ihr wart, sie waren; ich **hatte**, du hattest, er hatte, wir hatten, ihr hattet, sie hatten.
werden на A1: Ich werde Arzt. Es wird kalt. Ich werde 30 (мне исполнится 30).
""".trimIndent()
    ),

    Topic(
        id = "strong_verbs",
        block = "sentence",
        level = "A1.1",
        title = "Глаголы с изменением гласной (sprechen → du sprichst)",
        de = "Verben mit Vokalwechsel",
        keywords = listOf(
            "сильные глаголы", "неправильные глаголы", "изменение гласной", "sprichst", "fährst", "liest",
            "nimmst", "vokalwechsel", "unregelmäßig", "корневая гласная"
        ),
        sheet = """
Корневая гласная меняется **только в du и er/sie/es**. В ich, wir, ihr, sie — как обычно.

| Тип | Инфинитив | du | er/sie/es |
|---|---|---|---|
| e → i | sprechen | sprichst | spricht |
| | essen | isst | isst |
| | geben | gibst | gibt |
| | nehmen | nimmst | nimmt |
| | helfen | hilfst | hilft |
| | treffen | triffst | trifft |
| | vergessen | vergisst | vergisst |
| e → ie | sehen | siehst | sieht |
| | lesen | liest | liest |
| | empfehlen | empfiehlst | empfiehlt |
| a → ä | fahren | fährst | fährt |
| | schlafen | schläfst | schläft |
| | tragen | trägst | trägt |
| | waschen | wäschst | wäscht |
| | anfangen | fängst an | fängt an |
| | einladen | lädst ein | lädt ein |
| | gefallen | gefällst | gefällt |
| | halten | hältst | hält |
| au → äu | laufen | läufst | läuft |
| особые | wissen | weißt | weiß (и ich weiß) |
| | mögen | magst | mag (и ich mag) |

Примеры: Ich spreche Deutsch. — **Sprichst** du Deutsch? — Er **spricht** gut. · Ich fahre nach Hause. — Wann **fährst** du? · Was **isst** du gern? · Er **liest** ein Buch. · **Nimmst** du den Bus?
Не меняются: gehen (du gehst), stehen, kommen, machen, kaufen.
""".trimIndent()
    ),

    Topic(
        id = "word_order",
        block = "sentence",
        level = "A0",
        title = "Порядок слов: глагол на втором месте",
        de = "Wortstellung im Satz",
        keywords = listOf(
            "порядок слов", "wortstellung", "satzbau", "второе место", "втором месте", "инверси", "рамк",
            "satzklammer", "составь предложение", "место глагола"
        ),
        sheet = """
**Правило №1:** в повествовательном предложении спрягаемый глагол всегда на **2-м месте**.

| 1-е место | 2 (глагол) | середина | конец |
|---|---|---|---|
| Ich | lerne | heute | Deutsch. |
| Heute | lerne | ich | Deutsch. |
| Am Montag | fahre | ich | nach Berlin. |
| Deutsch | lerne | ich | seit einem Jahr. |

Если на 1-м месте не подлежащее — подлежащее идёт **сразу после глагола** (инверсия).
«1-е место» — это один член предложения, а не одно слово: [Am Montag um 8 Uhr] **gehe** ich …

**Вопрос с вопросительным словом:** W-слово + глагол + подлежащее: Wo **wohnst** du? Was **machst** du heute?
**Вопрос да/нет:** глагол на 1-м месте: **Wohnst** du in Berlin? **Hast** du Zeit?
**Просьба/приказ:** глагол на 1-м месте: **Komm** bitte! **Kommen** Sie bitte!

**Рамка (Satzklammer):** вторая часть сказуемого уходит в **конец**:
- модальный + инфинитив: Ich **kann** heute nicht **kommen**.
- отделяемая приставка: Ich **stehe** um 7 Uhr **auf**.
- Perfekt: Ich **habe** gestern Pizza **gegessen**.

**Порядок обстоятельств:** когда → как → где/куда: Ich fahre **morgen** **mit dem Bus** **nach Köln**.
Союзы und, oder, aber, denn, sondern места не занимают: Ich komme, **aber** ich **habe** wenig Zeit.
""".trimIndent()
    ),

    Topic(
        id = "w_questions",
        block = "sentence",
        level = "A0",
        title = "Вопросительные слова (W-Fragen)",
        de = "W-Fragen",
        keywords = listOf(
            "вопросительн", "w-frage", "w-вопрос", "w-fragen", "wohin", "woher", "wann", "warum", "wie viel",
            "welche", "вопросительные слова", "задать вопрос", "wer ", "was ", "wem", "wen "
        ),
        sheet = """
| Слово | Значение | Пример |
|---|---|---|
| wer? | кто? | Wer ist das? |
| was? | что? | Was machst du? |
| wo? | где? | Wo wohnst du? — In Berlin. |
| wohin? | куда? | Wohin gehst du? — Nach Hause. / In die Schule. |
| woher? | откуда? | Woher kommst du? — Aus Polen. |
| wann? | когда? | Wann kommst du? — Um 8 Uhr. / Am Montag. |
| wie? | как? | Wie heißt du? Wie geht's? |
| warum? | почему? | Warum lernst du Deutsch? |
| wie viel? | сколько? (неисчисл., цена) | Wie viel kostet das? |
| wie viele? | сколько? (штук) | Wie viele Kinder hast du? |
| wie lange? | как долго? | Wie lange bleibst du? — Zwei Wochen. |
| wie oft? | как часто? | Wie oft machst du Sport? |
| wie alt? | сколько лет? | Wie alt bist du? |
| wie spät? | который час? | Wie spät ist es? = Wie viel Uhr ist es? |
| um wie viel Uhr? | во сколько? | Um wie viel Uhr beginnt der Kurs? |
| welcher / welche / welches? | какой, который? (выбор) | Welche Sprachen sprichst du? Welcher Bus fährt zum Bahnhof? |
| was für ein(e)? | что за, какой? | Was für ein Auto hast du? |
| wen? | кого? (Akk) | Wen besuchst du? |
| wem? | кому? (Dat) | Wem hilfst du? |
| mit wem? | с кем? | Mit wem wohnst du? |
| womit? | на чём, чем? | Womit fährst du? — Mit dem Bus. |

**Порядок:** W-слово (1) + глагол (2) + подлежащее: Wann **kommt** der Bus?
**Не путай:** wo (где → Dativ) · wohin (куда → Akkusativ) · woher (откуда → aus / von + Dativ).
welch- склоняется как артикль: welch**er** Tag (m), welch**e** Stadt (f), welch**es** Buch (n), welch**e** Bücher (Pl); Akk m: welch**en** Bus nimmst du?
""".trimIndent()
    ),

    Topic(
        id = "yes_no",
        block = "sentence",
        level = "A0",
        title = "Ja, Nein, Doch",
        de = "Ja / Nein / Doch",
        keywords = listOf("doch", "ja/nein", "да/нет", "ja oder nein", "вопрос да нет"),
        sheet = """
| Вопрос | Согласие | Несогласие |
|---|---|---|
| Kommst du? (без отрицания) | **Ja**, ich komme. | **Nein**, ich komme nicht. |
| Kommst du **nicht**? (с отрицанием) | **Nein**, ich komme nicht. | **Doch**, ich komme! |
| Hast du **kein** Auto? | **Nein**, ich habe kein Auto. | **Doch**, ich habe ein Auto. |

**Doch** = «нет, наоборот — да»: отвечаем так на вопрос с nicht / kein, когда на самом деле «да».
Полезно: Ja, gern! · Ja, natürlich! · Ja, klar! · Nein, danke. · Nein, leider nicht. · Vielleicht. · Ich weiß nicht. · Ich glaube, ja. · Ich glaube nicht.
""".trimIndent()
    ),

    Topic(
        id = "negation",
        block = "sentence",
        level = "A0",
        title = "Отрицание: nicht и kein",
        de = "Negation: nicht / kein",
        keywords = listOf("отрицани", "nicht", "kein", "negation", "verneinung", "сделай отрицание"),
        sheet = """
**kein** — отрицает существительное с неопределённым артиклем или без артикля (склоняется как ein):
Ich habe ein Auto. → Ich habe **kein** Auto. · Ich habe Zeit. → Ich habe **keine** Zeit. · Das ist **kein** Tisch.

| | m | n | f | Pl |
|---|---|---|---|---|
| Nom | kein | kein | keine | keine |
| Akk | kein**en** | kein | keine | keine |
| Dat | kein**em** | kein**em** | kein**er** | kein**en** (+n) |

**nicht** — отрицает глагол, прилагательное, наречие, существительное с определённым артиклем или притяжательным, имена:
- всё предложение → nicht **в конце**: Ich komme heute **nicht**. Ich verstehe das **nicht**.
- перед прилагательным/наречием: Das ist **nicht** gut. Er wohnt **nicht** hier.
- перед предлогом: Ich komme **nicht** aus Berlin. Ich fahre **nicht** nach Hause.
- перед второй частью сказуемого: Ich kann **nicht** kommen. Ich stehe **nicht** auf. Ich habe **nicht** geschlafen.
- Das ist **nicht** mein Buch. Das ist **nicht** der Lehrer. Das ist **nicht** Peter.

**Другие отрицания:** nichts (ничего), nie (никогда), niemand (никто), noch nicht (ещё не), nicht mehr (больше не), kein … mehr (больше нет).
В немецком **одно** отрицание: Ich habe **nichts**. Ich war **nie** in Berlin.
""".trimIndent()
    ),

    Topic(
        id = "conjunctions",
        block = "sentence",
        level = "A1.1",
        title = "Союзы: und, oder, aber, denn, sondern",
        de = "Konjunktionen",
        keywords = listOf("союз", "oder", "aber", "denn", "sondern", "konjunktion", "deshalb", "соедини предложения"),
        sheet = """
| Союз | Значение | Пример |
|---|---|---|
| und | и | Ich lerne Deutsch **und** (ich) arbeite. |
| oder | или | Trinkst du Tee **oder** Kaffee? |
| aber | но | Ich bin müde, **aber** ich lerne. |
| denn | потому что | Ich bleibe zu Hause, **denn** ich bin krank. |
| sondern | а (после отрицания) | Ich komme nicht aus Polen, **sondern** aus Russland. |

После этих союзов порядок слов обычный (глагол 2-й): …, aber ich **habe** keine Zeit. Запятая перед aber, denn, sondern обязательна.

**Наречия-связки** занимают 1-е место → после них сразу глагол: dann (потом), danach (после этого), zuerst (сначала), später (позже), deshalb (поэтому), trotzdem (всё равно):
Zuerst **frühstücke** ich, dann **gehe** ich zur Arbeit. · Ich bin krank, deshalb **bleibe** ich zu Hause.

weil / dass (придаточные, глагол в конце) — это A2. На A1 достаточно понимать: Ich lerne Deutsch, weil ich in Deutschland **lebe**.
""".trimIndent()
    )
)

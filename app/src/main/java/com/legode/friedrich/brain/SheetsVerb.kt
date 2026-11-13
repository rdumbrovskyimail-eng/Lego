package com.legode.friedrich.brain

/* Блок «Глаголы: шаг 2»: модальные, отделяемые приставки, императив, Perfekt, war/hatte, gern/mögen/gefallen. */

internal val verbTopics: List<Topic> = listOf(

    Topic(
        id = "modal_verbs",
        block = "verb",
        level = "A1.1",
        title = "Модальные глаголы",
        de = "Modalverben",
        keywords = listOf(
            "модальн", "modalverb", "können", "müssen", "wollen", "dürfen", "sollen", "möchte", " kann ",
            " muss ", " darf ", " soll "
        ),
        sheet = """
| | können | müssen | wollen | dürfen | sollen | mögen | möchten |
|---|---|---|---|---|---|---|---|
| значение | мочь, уметь | быть должным | хотеть | иметь разрешение | следует (велели) | любить | хотел бы |
| ich | kann | muss | will | darf | soll | mag | möchte |
| du | kannst | musst | willst | darfst | sollst | magst | möchtest |
| er/sie/es | kann | muss | will | darf | soll | mag | möchte |
| wir | können | müssen | wollen | dürfen | sollen | mögen | möchten |
| ihr | könnt | müsst | wollt | dürft | sollt | mögt | möchtet |
| sie/Sie | können | müssen | wollen | dürfen | sollen | mögen | möchten |

**ich** и **er/sie/es** — одинаковые и **без окончания**: ich kann, er kann (не «er kannt»).
**Рамка:** модальный глагол на 2-м месте, смысловой инфинитив — в **конце**:
Ich **kann** gut **schwimmen**. · Ich **muss** heute lange **arbeiten**. · **Kannst** du mir **helfen**? · Hier **darf** man nicht **rauchen**. · Ich **möchte** einen Kaffee (**bestellen**).

**Значения:**
- können — умею, могу; вежливая просьба: Können Sie mir helfen?
- müssen — необходимость: Ich muss zum Arzt. **nicht müssen** = не обязательно.
- dürfen — разрешено; **nicht dürfen** = нельзя: Hier darf man nicht parken.
- wollen — твёрдое желание, план: Ich will Deutsch lernen. möchten — вежливое «хотел бы» (кафе, магазин).
- sollen — чужое поручение, совет: Der Arzt sagt, ich soll viel trinken. Soll ich helfen?
- mögen + Akk — нравится: Ich mag Pizza. Ich mag **keinen** Fisch.

В прошедшем (узнавать на A1): konnte, musste, wollte, durfte, sollte, mochte — Ich **musste** arbeiten.
""".trimIndent()
    ),

    Topic(
        id = "separable_verbs",
        block = "verb",
        level = "A1.1",
        title = "Отделяемые и неотделяемые приставки",
        de = "Trennbare und untrennbare Verben",
        keywords = listOf(
            "отделяем", "trennbar", "приставк", "aufstehen", "anrufen", "einkaufen", "fernsehen", "präfix",
            "untrennbar", "неотделяем"
        ),
        sheet = """
Отделяемая приставка (ударная) уходит в **конец** предложения:
**auf**stehen → Ich **stehe** um 7 Uhr **auf**. · **an**rufen → Ich **rufe** dich morgen **an**. · **ein**kaufen → Wir **kaufen** im Supermarkt **ein**.

| Глагол | Перевод | Пример |
|---|---|---|
| aufstehen | вставать | Wann stehst du auf? |
| anrufen (+ Akk) | звонить | Ruf mich an! |
| einkaufen | делать покупки | Ich kaufe heute ein. |
| fernsehen | смотреть телевизор | Er sieht abends fern. |
| anfangen | начинать(ся) | Der Kurs fängt um 9 Uhr an. |
| aufhören | прекращать(ся) | Der Regen hört auf. |
| aufmachen / zumachen | открывать / закрывать | Mach bitte das Fenster zu! |
| mitkommen / mitbringen / mitnehmen | пойти вместе / принести / взять с собой | Kommst du mit? |
| abholen | забирать, встречать | Ich hole dich ab. |
| ankommen / abfahren | прибывать / отправляться | Der Zug kommt um 10 Uhr an. |
| einsteigen / aussteigen / umsteigen | садиться / выходить / пересаживаться | Wo steigen wir um? |
| zurückkommen | возвращаться | Wann kommst du zurück? |
| einladen | приглашать | Ich lade dich ein. |
| ausfüllen | заполнять | Füllen Sie das Formular aus. |
| aufräumen | убирать | Ich räume die Wohnung auf. |
| anziehen / ausziehen | надевать / снимать | Zieh die Jacke an! |
| zuhören | слушать | Hör bitte zu! |
| stattfinden | состояться | Der Kurs findet heute statt. |

**Отделяемые:** ab-, an-, auf-, aus-, bei-, ein-, fern-, mit-, nach-, vor-, weg-, zu-, zurück-, zusammen-, her-, hin-, los-, teil-, statt-.
**Неотделяемые** (безударные): be-, ge-, er-, ver-, zer-, ent-, emp-, miss-: besuchen → Ich **besuche** dich. Также: verstehen, bezahlen, erzählen, gefallen, beginnen, bekommen, vergessen, entschuldigen.
С модальным глаголом — слитно в конце: Ich muss früh **aufstehen**.
В Perfekt «ge» встаёт в середину: auf**ge**standen, ein**ge**kauft, an**ge**rufen. В вопросе: **Rufst** du mich **an**? Wann **fängt** der Film **an**?
""".trimIndent()
    ),

    Topic(
        id = "imperative",
        block = "verb",
        level = "A1.2",
        title = "Imperativ: просьбы и указания",
        de = "Imperativ",
        keywords = listOf("императив", "imperativ", "повелительн", "приказ", "попроси", "скажи вежливо", "указани"),
        sheet = """
| Инфинитив | du | ihr | Sie |
|---|---|---|---|
| kommen | Komm! | Kommt! | Kommen Sie! |
| machen | Mach! | Macht! | Machen Sie! |
| warten (-t, -d) | Warte! | Wartet! | Warten Sie! |
| nehmen (e → i) | Nimm! | Nehmt! | Nehmen Sie! |
| geben | Gib! | Gebt! | Geben Sie! |
| essen | Iss! | Esst! | Essen Sie! |
| lesen | Lies! | Lest! | Lesen Sie! |
| sprechen | Sprich! | Sprecht! | Sprechen Sie! |
| helfen | Hilf! | Helft! | Helfen Sie! |
| sehen | Sieh! | Seht! | Sehen Sie! |
| fahren (a → ä не меняется) | Fahr! | Fahrt! | Fahren Sie! |
| schlafen | Schlaf! | Schlaft! | Schlafen Sie! |
| sein | Sei! | Seid! | Seien Sie! |
| haben | Hab! | Habt! | Haben Sie! |
| aufstehen | Steh auf! | Steht auf! | Stehen Sie auf! |
| anrufen | Ruf an! | Ruft an! | Rufen Sie an! |

**Как образовать:**
- **du:** берём «du kommst», убираем -st и du → **Komm!** Изменение e → i сохраняется (Nimm!), умлаут a → ä — нет (Fahr!).
- **ihr:** как в Präsens, без ihr → **Kommt!**
- **Sie:** глагол + Sie → **Kommen Sie!**

Вежливее с bitte / mal / doch: Komm **bitte**! · Warten Sie **bitte** einen Moment! · Gib mir **mal** das Buch.
Отрицание: Rauch **nicht**! · Haben Sie **keine** Angst!
Типичное: Entschuldigen Sie! · Hören Sie zu! · Sprechen Sie bitte langsam! · Nehmen Sie Platz! · Unterschreiben Sie bitte hier. · Gehen Sie geradeaus!
""".trimIndent()
    ),

    Topic(
        id = "perfekt",
        block = "verb",
        level = "A1.2",
        title = "Perfekt: прошедшее время в разговоре",
        de = "Perfekt",
        keywords = listOf(
            "perfekt", "перфект", "прошедшее время", "прошедшем времени", "partizip", "причасти", "gemacht",
            "gegangen", "что ты делал", "gestern", "поставь в прошедшее"
        ),
        sheet = """
**Perfekt = haben / sein (на 2-м месте) + Partizip II (в конце).**
Ich **habe** gestern Pizza **gegessen**. · Ich **bin** nach Berlin **gefahren**.

| Тип глагола | Partizip II | Примеры |
|---|---|---|
| правильные | **ge** + основа + **t** | machen → gemacht, kaufen → gekauft, lernen → gelernt, wohnen → gewohnt, spielen → gespielt, kochen → gekocht, hören → gehört, fragen → gefragt, arbeiten → gearbeitet |
| сильные | **ge** + (изменённая) основа + **en** | essen → gegessen, trinken → getrunken, lesen → gelesen, schreiben → geschrieben, sehen → gesehen, sprechen → gesprochen, nehmen → genommen, finden → gefunden, helfen → geholfen, schlafen → geschlafen, treffen → getroffen, geben → gegeben |
| отделяемые | приставка + **ge** + … | einkaufen → eingekauft, aufstehen → aufgestanden, anrufen → angerufen, fernsehen → ferngesehen, mitkommen → mitgekommen, anfangen → angefangen |
| без ge-: неотделяемые приставки и -ieren | | besuchen → besucht, bezahlen → bezahlt, erzählen → erzählt, verstehen → verstanden, vergessen → vergessen, bekommen → bekommen, beginnen → begonnen, studieren → studiert, telefonieren → telefoniert |
| смешанные | **ge** + изменённая основа + **t** | bringen → gebracht, denken → gedacht, kennen → gekannt, wissen → gewusst |

**С sein** — движение из точки в точку и смена состояния (+ sein, bleiben, werden, passieren):
gehen → ist gegangen · fahren → ist gefahren · kommen → ist gekommen · fliegen → ist geflogen · laufen → ist gelaufen · reisen → ist gereist · aufstehen → ist aufgestanden · einschlafen → ist eingeschlafen · ankommen → ist angekommen · umziehen → ist umgezogen · bleiben → ist geblieben · sein → ist gewesen · werden → ist geworden · passieren → ist passiert · fallen → ist gefallen · schwimmen → ist geschwommen.
**Все остальные — с haben** (в том числе все глаголы с Akkusativ).

Отрицание: Ich habe **nicht** geschlafen. Ich habe **kein** Brot gekauft. Вопрос: **Hast** du schon **gegessen**? Was **hast** du gestern **gemacht**? Wo **bist** du **gewesen**?
Для sein и haben чаще говорят **war / hatte**: Ich war zu Hause. Ich hatte keine Zeit.
Слова-сигналы: gestern, vorgestern, letzte Woche, letztes Jahr, letzten Monat, am Wochenende, vor zwei Tagen, früher, schon, noch nicht.
""".trimIndent()
    ),

    Topic(
        id = "praeteritum",
        block = "verb",
        level = "A1.2",
        title = "war и hatte (Präteritum от sein / haben)",
        de = "Präteritum: war, hatte",
        keywords = listOf("präteritum", "претерит", " war ", "hatte", "waren", "warst", "был ", "была ", "были "),
        sheet = """
| | sein → war | haben → hatte |
|---|---|---|
| ich | war | hatte |
| du | warst | hattest |
| er/sie/es | war | hatte |
| wir | waren | hatten |
| ihr | wart | hattet |
| sie/Sie | waren | hatten |

ich и er/sie/es — одинаковые и без окончания: ich **war**, er **war**; ich **hatte**, er **hatte**.
Wo **warst** du gestern? — Ich **war** zu Hause / im Kino / bei meiner Freundin. · Wie **war** das Wetter? — Es **war** schön. · Ich **hatte** keine Zeit. · **Hattest** du Urlaub? · Wir **hatten** viel Spaß. · Früher **hatte** ich einen Hund.
**es gab** — было, имелось: Es gab kein Brot.
Модальные в прошедшем (узнавать): ich konnte, musste, wollte, durfte, sollte — Ich **konnte** nicht kommen. Ich **musste** arbeiten.
Остальные глаголы на A1 рассказываем в Perfekt: Ich **habe** gearbeitet. Ich **bin** gegangen.
""".trimIndent()
    ),

    Topic(
        id = "likes",
        block = "verb",
        level = "A1.1",
        title = "Нравится и хочется: gern, mögen, möchten, gefallen, schmecken",
        de = "gern, mögen, möchten, gefallen",
        keywords = listOf(
            "gern", "lieber", "am liebsten", "mögen", " mag ", "gefallen", "gefällt", "schmeckt", "нравит",
            "люблю", "любишь", "предпочита"
        ),
        sheet = """
| Что хочу сказать | Конструкция | Пример |
|---|---|---|
| люблю **делать** | глагол + **gern** | Ich schwimme gern. Ich koche **nicht gern**. |
| больше / больше всего | **lieber** / **am liebsten** | Ich trinke gern Tee, aber lieber Kaffee. Am liebsten trinke ich Saft. |
| люблю вещь, еду, человека | **mögen** + Akk | Ich mag Pizza. Ich mag **keinen** Fisch. Magst du Hunde? |
| хотел бы (сейчас, вежливо) | **möchten** + Akk / инфинитив | Ich möchte einen Tee. Ich möchte nach Hause gehen. |
| нравится (впечатление) | **gefallen** + Dat | Die Stadt gefällt **mir**. Die Schuhe gefallen **mir**. Wie gefällt **dir** Berlin? |
| вкусно | **schmecken** + Dat | Die Suppe schmeckt **mir** gut. Schmeckt es **dir**? |
| считаю каким-то | **finden** + Akk + прилагательное | Ich finde den Film gut. Wie findest du das? |
| хобби | Mein Hobby ist … / Meine Hobbys sind … | Mein Hobby ist Kochen. |

**Степени:** gern – lieber – am liebsten · gut – besser – am besten · viel – mehr – am meisten.
Was machst du gern? — Ich lese gern. / Ich spiele gern Fußball. / Ich höre gern Musik.
**gern** стоит после глагола (и после подлежащего при инверсии): Am Wochenende **gehe ich gern** ins Kino.
Подлежащее у gefallen / schmecken — то, что нравится: Die Schuhe (мн.) gefall**en** mir.
""".trimIndent()
    )
)

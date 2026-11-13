package com.legode.friedrich.brain

/* Блок «Старт»: алфавит и чтение, приветствия, знакомство, du/Sie, числа, спасательные фразы. */

internal val startTopics: List<Topic> = listOf(

    Topic(
        id = "alphabet",
        block = "start",
        level = "A0",
        title = "Алфавит и правила чтения",
        de = "Alphabet und Aussprache",
        keywords = listOf(
            "алфавит", "букв", "произнош", "читается", "как читать", "умлаут", "umlaut", "ß", "эсцет",
            "buchstab", "alphabet", "звук", "ударени", "aussprache"
        ),
        sheet = """
**Алфавит:** 26 букв + **ä, ö, ü, ß**.
A [а], B [бэ], C [цэ], D [дэ], E [э], F [эф], G [гэ], H [ха], I [и], J [йот], K [ка], L [эль], M [эм], N [эн], O [о], P [пэ], Q [ку], R [эр], S [эс], T [тэ], U [у], V [фау], W [вэ], X [икс], Y [ипсилон], Z [цэт]; Ä [э], Ö [«ё» без й], Ü [«ю» без й], ß [эс-цэт] = «сс».

| Пишем | Читаем | Примеры |
|---|---|---|
| ei | [ай] | nein, zwei, heißen |
| ie | долгое [и] | sieben, Liebe, wie |
| eu, äu | [ой] | heute, neu, Häuser |
| ch | [х] после a, o, u, au; мягкое [хь] после остальных | Buch, auch — ich, Milch, sprechen |
| sch | [ш] | Schule, schön |
| sp-, st- (в начале слова/корня) | [шп], [шт] | sprechen, Straße, verstehen |
| tsch | [ч] | Deutsch, tschüss |
| z, tz | [ц] | zehn, Platz |
| v | [ф]; в заимствованиях [в] | Vater, vier — Vase, November |
| w | [в] | Wasser, wo |
| j | [й] | ja, Jahr |
| s перед гласной | [з] | Sonne, lesen |
| s в конце, ss, ß | [с] | Haus, Wasser, heißen |
| h после гласной | не читается, гласная долгая | wohnen, Uhr, fahren |
| qu | [кв] | Quittung |
| -ig в конце | [ихь] | richtig, zwanzig |
| chs | [кс] | sechs |
| ck | [к] | Stück, Frühstück |
| ph | [ф] | Alphabet |
| -er в конце | почти [а] | Lehrer, Mutter |

**Ударение** обычно на первом слоге корня: **AR**beit, **WOH**nen. Безударные приставки be-, ge-, er-, ver-, zer-, ent-: be**SU**chen. Отделяемые приставки ударные: **AUF**stehen. Глаголы на -ieren: stu**DIE**ren.
**Все существительные — с большой буквы:** der Tisch, das Haus.
Продиктовать по буквам: «Wie schreibt man das?» — «M-Ü-L-L-E-R». «Können Sie das bitte buchstabieren?»
""".trimIndent()
    ),

    Topic(
        id = "greetings",
        block = "start",
        level = "A0",
        title = "Приветствия, прощания, вежливые слова",
        de = "Begrüßung und Abschied",
        keywords = listOf(
            "привет", "здравств", "прощан", "hallo", "guten morgen", "guten tag", "guten abend", "tschüss",
            "wiedersehen", "как дела", "wie geht", "спасибо", "danke", "bitte", "извин", "entschuld", "begrüß"
        ),
        sheet = """
| Когда | По-немецки | Перевод |
|---|---|---|
| утром (до ~10–11) | Guten Morgen! | Доброе утро! |
| днём | Guten Tag! | Добрый день! |
| вечером (после ~18) | Guten Abend! | Добрый вечер! |
| перед сном | Gute Nacht! | Спокойной ночи! |
| неформально | Hallo! / Hi! | Привет! |
| юг Германии, Австрия | Grüß Gott! / Servus! | Здравствуйте! |
| официальное прощание | Auf Wiedersehen! | До свидания! |
| по телефону | Auf Wiederhören! | До свидания! |
| неформально | Tschüss! / Ciao! | Пока! |
| «до…» | Bis bald! / Bis später! / Bis morgen! / Bis dann! | До скорого / до позже / до завтра / до встречи |
| пожелания | Schönen Tag (noch)! Schönes Wochenende! — Danke, gleichfalls! | Хорошего дня! Хороших выходных! — Спасибо, взаимно! |

**Как дела?** Wie geht's? = Wie geht es dir? (ты) / Wie geht es Ihnen? (Вы)
— Danke, (sehr) gut. / Gut, danke. / Es geht. / So lala. / Nicht so gut. / Schlecht. — Und dir? / Und Ihnen?
Полная форма: **Mir** geht es gut (Dativ!), не «ich gehe gut».

**Вежливость:** bitte (пожалуйста); danke / danke schön / vielen Dank (спасибо); bitte / bitte schön / gern geschehen (в ответ на спасибо); Entschuldigung! / Entschuldigen Sie! (извините); Es tut mir leid (мне жаль); Kein Problem; Macht nichts (ничего страшного); Willkommen! / Herzlich willkommen!
Guten Morgen/Tag/Abend — с **-en** (Akkusativ: «желаю доброго…»), но Gute Nacht (женский род).
""".trimIndent()
    ),

    Topic(
        id = "intro",
        block = "start",
        level = "A0",
        title = "Знакомство: имя, страна, город, возраст, профессия",
        de = "Sich vorstellen",
        keywords = listOf(
            "знакомств", "heiße", "heißt", "зовут", "woher", "komme aus", "откуда", "wohne", "живу в",
            "von beruf", "професси", "sprachen", "представ", "vorstellen", "wie alt", "сколько лет"
        ),
        sheet = """
| Вопрос (du) | Вопрос (Sie) | Ответ |
|---|---|---|
| Wie heißt du? | Wie heißen Sie? | Ich heiße Anna. / Ich bin Anna. / Mein Name ist Anna Schmidt. |
| Woher kommst du? | Woher kommen Sie? | Ich komme aus Russland / aus der Ukraine / aus Kasachstan. |
| Wo wohnst du? | Wo wohnen Sie? | Ich wohne in Berlin. / Ich lebe in Deutschland. |
| Wie alt bist du? | Wie alt sind Sie? | Ich bin 30 (Jahre alt). |
| Was bist du von Beruf? | Was sind Sie von Beruf? | Ich bin Lehrer / Lehrerin. Ich arbeite als Fahrer. Ich bin Student(in). |
| Welche Sprachen sprichst du? | Welche Sprachen sprechen Sie? | Ich spreche Russisch, Englisch und ein bisschen Deutsch. |
| Bist du verheiratet? | Sind Sie verheiratet? | Ja, ich bin verheiratet. / Nein, ich bin ledig / geschieden. |
| Hast du Kinder? | Haben Sie Kinder? | Ja, ich habe zwei Kinder / einen Sohn und eine Tochter. — Nein, ich habe keine Kinder. |
| Was sind deine Hobbys? | Was sind Ihre Hobbys? | Meine Hobbys sind Lesen und Sport. Ich koche gern. |
| Wie ist deine Telefonnummer? | Wie ist Ihre Adresse? | Meine Telefonnummer ist 0176 … |

**Правила:**
- Профессия и национальность — **без артикля**: Ich bin Arzt. Sie ist Ukrainerin.
- Женская форма профессии: + **in** (Lehrer → Lehrerin, Arzt → Ärztin, Koch → Köchin).
- Страны обычно без артикля: aus Deutschland, aus Polen. **Но:** die Schweiz, die Türkei, die Ukraine, die Slowakei, der Iran, der Irak, die USA (мн.), die Niederlande (мн.) → aus **der** Schweiz, aus **der** Türkei, aus **der** Ukraine, aus **dem** Iran, aus **den** USA.
- Возраст — с **sein**: Ich **bin** 30. (не «habe»)
- Представить другого: Das ist Paul. Er kommt aus Polen. Das ist meine Frau / mein Mann / mein Freund / meine Freundin.
- Freut mich! / Angenehm! / Schön, dich kennenzulernen. — Очень приятно!
""".trimIndent()
    ),

    Topic(
        id = "du_sie",
        block = "start",
        level = "A0",
        title = "du или Sie: «ты» и «Вы»",
        de = "du oder Sie",
        keywords = listOf("на вы", "на ты", "du или sie", "du oder sie", "вежливая форма", "формальн", "siezen", "duzen"),
        sheet = """
**du** — друзья, семья, дети, ровесники в неформальной обстановке: Wie heißt **du**?
**Sie** (всегда с большой буквы) — незнакомые взрослые, учреждения, врач, начальник, продавец: Wie heißen **Sie**?
Sie = «Вы» одному и «вы» нескольким вежливо; глагол как у «sie» (они): Sie komm**en**.
**ihr** — «вы» нескольким людям, с которыми на «ты»: Kommt **ihr**?

| | ты | вы (друзья) | Вы (вежливо) |
|---|---|---|---|
| кто? | du | ihr | Sie |
| кого? (Akk) | dich | euch | Sie |
| кому? (Dat) | dir | euch | Ihnen |
| чей? | dein | euer | Ihr |

Обращение: **Herr** Müller, **Frau** Schmidt (только с фамилией). Перейти на «ты»: «Wollen wir uns duzen?» — «Ja, gern!»
Одно и то же двумя способами: Wie geht es **dir**? — Wie geht es **Ihnen**? · Woher kommst **du**? — Woher kommen **Sie**? · Kannst **du** mir helfen? — Können **Sie** mir helfen?
""".trimIndent()
    ),

    Topic(
        id = "numbers",
        block = "start",
        level = "A0",
        title = "Числа, цены, телефон, годы",
        de = "Zahlen",
        keywords = listOf(
            "числ", "цифр", "считать", "счёт до", "zahl", "номер", "nummer", "eins", "zwei", "zwanzig",
            "hundert", "tausend", "цен", "preis", "euro", "сколько стоит", "год рождения"
        ),
        sheet = """
| 0–12 | 13–19 | десятки | дальше |
|---|---|---|---|
| 0 null | 13 dreizehn | 20 zwanzig | 100 (ein)hundert |
| 1 eins | 14 vierzehn | 30 dreißig (ß!) | 101 hunderteins |
| 2 zwei | 15 fünfzehn | 40 vierzig | 200 zweihundert |
| 3 drei | 16 sechzehn (без s) | 50 fünfzig | 1 000 (ein)tausend |
| 4 vier | 17 siebzehn (без en) | 60 sechzig | 10 000 zehntausend |
| 5 fünf | 18 achtzehn | 70 siebzig | 1 000 000 eine Million |
| 6 sechs | 19 neunzehn | 80 achtzig | |
| 7 sieben | | 90 neunzig | |
| 8 acht, 9 neun | | | |
| 10 zehn, 11 elf, 12 zwölf | | | |

**21–99 «наоборот»:** единицы + **und** + десятки, слитно: 21 ein**und**zwanzig, 35 fünfunddreißig, 47 siebenundvierzig, 99 neunundneunzig. (eins → ein: einundvierzig)
365 dreihundertfünfundsechzig; 1 250 (ein)tausendzweihundertfünfzig.
**Годы:** 1999 neunzehnhundertneunundneunzig; 2026 zweitausendsechsundzwanzig. «Ich bin 1990 geboren.» (без «in»!) или «im Jahr 1990».
**Цены:** 2,50 € = zwei Euro fünfzig; 0,99 € = neunundneunzig Cent. Was kostet das? — Das kostet 5 Euro.
**Телефон:** по одной цифре или парами; вместо «zwei» часто говорят «zwo».
**Сколько?** Wie viel? (неисчисляемое, цена) / Wie viele? (штук): Wie viele Kinder hast du?
**Счёт:** 2 + 3 = 5 → zwei plus drei ist (gleich) fünf; minus, mal, (geteilt) durch.
""".trimIndent()
    ),

    Topic(
        id = "classroom",
        block = "start",
        level = "A0",
        title = "Спасательные фразы: «не понимаю», «повторите»",
        de = "Wichtige Sätze im Gespräch",
        keywords = listOf(
            "не понимаю", "verstehe nicht", "wiederholen", "noch einmal", "langsam", "как сказать",
            "wie sagt man", "was bedeutet", "что значит", "как пишется", "не знаю"
        ),
        sheet = """
| По-немецки | Перевод |
|---|---|
| Wie bitte? | Что, простите? |
| Ich verstehe (das) nicht. | Я (этого) не понимаю. |
| Noch einmal, bitte. | Ещё раз, пожалуйста. |
| Können Sie das bitte wiederholen? | Вы можете повторить? |
| Bitte langsam(er). / Können Sie bitte langsamer sprechen? | Помедленнее, пожалуйста. |
| Was bedeutet „…“? / Was heißt „…“? | Что значит «…»? |
| Wie heißt das auf Deutsch? | Как это называется по-немецки? |
| Wie sagt man „…“ auf Deutsch? | Как сказать «…» по-немецки? |
| Wie schreibt man das? | Как это пишется? |
| Ich weiß (es) nicht. / Keine Ahnung. | Не знаю. / Понятия не имею. |
| Ich spreche nur ein bisschen Deutsch. | Я говорю по-немецки совсем немного. |
| Ich lerne Deutsch. | Я учу немецкий. |
| Sprechen Sie Englisch / Russisch? | Вы говорите по-английски / по-русски? |
| Können Sie mir bitte helfen? | Вы не могли бы мне помочь? |
| Ich habe eine Frage. | У меня вопрос. |
| Alles klar. / Ich verstehe. / Genau. / Stimmt. | Всё ясно. / Понимаю. / Именно. / Верно. |
| Richtig. / Falsch. | Правильно. / Неправильно. |
| Einen Moment, bitte. | Минутку, пожалуйста. |
""".trimIndent()
    )
)

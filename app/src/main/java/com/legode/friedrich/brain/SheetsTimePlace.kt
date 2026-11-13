package com.legode.friedrich.brain

/* Блок «Время, место, количество»: часы, дни и даты, предлоги времени и места, количество, прилагательные. */

internal val timePlaceTopics: List<Topic> = listOf(

    Topic(
        id = "time_clock",
        block = "timeplace",
        level = "A1.1",
        title = "Часы: который час?",
        de = "Uhrzeit",
        keywords = listOf(
            "который час", "сколько времени", "wie spät", "uhrzeit", "wie viel uhr", "halb", "viertel",
            "часы", "время по-немецки", " uhr"
        ),
        sheet = """
**Wie spät ist es? = Wie viel Uhr ist es?** — Es ist …

| Время | Разговорно (12 часов) | Официально (24 часа) |
|---|---|---|
| 8:00 | acht (Uhr) | acht Uhr |
| 8:05 | fünf nach acht | acht Uhr fünf |
| 8:10 | zehn nach acht | acht Uhr zehn |
| 8:15 | Viertel nach acht | acht Uhr fünfzehn |
| 8:20 | zwanzig nach acht | acht Uhr zwanzig |
| 8:25 | fünf vor halb neun | acht Uhr fünfundzwanzig |
| 8:30 | **halb neun** | acht Uhr dreißig |
| 8:35 | fünf nach halb neun | acht Uhr fünfunddreißig |
| 8:40 | zwanzig vor neun | acht Uhr vierzig |
| 8:45 | Viertel vor neun | acht Uhr fünfundvierzig |
| 8:55 | fünf vor neun | acht Uhr fünfundfünfzig |
| 20:30 | halb neun (abends) | zwanzig Uhr dreißig |
| 13:00 | eins / ein Uhr | dreizehn Uhr |
| 0:00 | Mitternacht / zwölf | null Uhr |

**Главное:** **halb** + следующий час: halb neun = 8:30 («половина девятого»). **nach** — после, **vor** — до.
1:00 = «Es ist **ein** Uhr» или «Es ist **eins**» (не «eins Uhr»).
**Во сколько?** — Um wie viel Uhr? / Wann? → **um** 8 Uhr, **um** halb neun. Примерно: **gegen** 8 Uhr.
От и до: **von** 9 **bis** 17 Uhr. С какого времени: **ab** 8 Uhr.
Уточнение: morgens / vormittags / mittags / nachmittags / abends / nachts → um 7 Uhr abends.
Официальное время (вокзал, радио, расписание) — всегда 24 часа: Der Zug fährt um 15:42 Uhr (fünfzehn Uhr zweiundvierzig).
die Uhr = часы (прибор) и «час» на циферблате; die Stunde = час (60 минут): zwei Stunden; die Minute, die Sekunde. Es ist früh / spät. Ich habe keine Zeit.
""".trimIndent()
    ),

    Topic(
        id = "days_dates",
        block = "timeplace",
        level = "A1.1",
        title = "Дни недели, месяцы, даты, день рождения",
        de = "Wochentage, Monate, Datum",
        keywords = listOf(
            "дни недели", "день недели", "месяц", "wochentag", "montag", "dienstag", "mittwoch", "donnerstag",
            "freitag", "samstag", "sonntag", "datum", "какое число", "день рождения", "geburtstag",
            "порядковые", "времена года", "jahreszeit"
        ),
        sheet = """
**Дни (все der):** Montag, Dienstag, Mittwoch, Donnerstag, Freitag, Samstag (на севере Sonnabend), Sonntag. das Wochenende, der Wochentag, der Feiertag.
**Месяцы (все der):** Januar, Februar, März, April, Mai, Juni, Juli, August, September, Oktober, November, Dezember.
**Времена года (все der):** Frühling, Sommer, Herbst, Winter.

| Когда? | Предлог | Примеры |
|---|---|---|
| день, дата, часть дня | **am** | am Montag, am Wochenende, am 3. Mai, am Abend (но: **in der** Nacht) |
| месяц, время года | **im** | im Januar, im Sommer |
| год | без предлога / im Jahr | 2024 / im Jahr 2024 |
| время на часах | **um** | um 8 Uhr |
| регулярно | наречие с **-s** | montags, abends, morgens |

**Порядковые числа:** 1. erste, 2. zweite, 3. **dritte**, 4. vierte, 5. fünfte, 6. sechste, 7. **siebte**, 8. **achte**, 9. neunte, 10. zehnte … 19. neunzehnte (число + **te**); с 20: + **ste** — 20. zwanzigste, 21. einundzwanzigste, 30. dreißigste, 31. einunddreißigste.
**Дата:** Welcher Tag ist heute? — Heute ist Montag. · Der Wievielte ist heute? — Heute ist **der** zehn**te** November. · Wann? — **Am** zehn**ten** November. · Пишем: 10.11.2026, Berlin, den 10.11.2026.
**День рождения:** Wann hast du Geburtstag? — Ich habe **am** dritt**en** Mai Geburtstag. / Im Mai. · Wann bist du geboren? — Ich bin **am** 3. Mai 1990 geboren. · Herzlichen Glückwunsch zum Geburtstag! Alles Gute!
**Слова времени:** heute, morgen, übermorgen, gestern, vorgestern; jetzt, gleich, bald, später, sofort; heute Morgen, heute Abend, morgen früh; diese / nächste / letzte Woche; dieses Jahr, nächsten Monat; täglich = jeden Tag.
""".trimIndent()
    ),

    Topic(
        id = "prep_time",
        block = "timeplace",
        level = "A1.1",
        title = "Предлоги времени: um, am, im, von … bis, seit, vor, nach, in",
        de = "Temporale Präpositionen",
        keywords = listOf(
            "предлоги времени", "temporale", " seit ", "vor zwei", "von bis", " ab ", "как долго", "wie lange",
            "с каких пор", "seit wann", "через неделю"
        ),
        sheet = """
| Предлог | Значение | Примеры |
|---|---|---|
| **um** | во сколько (часы) | um 8 Uhr, um halb neun |
| **am** | день, дата, часть дня | am Montag, am 5. Mai, am Vormittag |
| **im** | месяц, время года | im März, im Winter |
| **von … bis** | с … до | von 9 bis 17 Uhr, von Montag bis Freitag |
| **ab** | начиная с | ab morgen, ab 8 Uhr, ab Montag |
| **bis** | до | bis morgen, bis 18 Uhr, bis Freitag |
| **seit** + Dat | уже (началось и длится) | seit einem Jahr, seit zwei Wochen, seit gestern |
| **vor** + Dat | … назад | vor zwei Jahren, vor einer Woche, vor dem Kurs (перед) |
| **nach** + Dat | после | nach dem Essen, nach der Arbeit, nach dem Kurs |
| **in** + Dat | через | in zwei Wochen, in einer Stunde, in fünf Minuten |
| **für** + Akk | на (срок) | für zwei Wochen, für einen Monat |
| **zwischen** | между | zwischen 8 und 10 Uhr |
| **gegen** | около | gegen 8 Uhr |

**Без предлога** (Akkusativ): jeden Tag, jeden Montag, nächste Woche, letztes Jahr, diesen Monat, den ganzen Tag, einen Monat (lang).
**seit + Präsens** — действие продолжается: Ich lerne **seit** drei Monaten Deutsch. Ich wohne **seit** 2022 in Berlin.
**vor** = «назад» → прошедшее: Ich **bin vor** einem Jahr nach Deutschland **gekommen**.
**in** = «через» → будущее: Ich komme **in** zehn Minuten.
Вопросы: Wann? · Wie lange? (zwei Stunden) · Seit wann? (seit Montag) · Bis wann? · Wie oft? (einmal / zweimal pro Woche, jeden Tag) · Um wie viel Uhr?
Частота: immer → meistens → oft → manchmal → selten → nie.
""".trimIndent()
    ),

    Topic(
        id = "prep_place",
        block = "timeplace",
        level = "A1.2",
        title = "Предлоги места: wo? wohin? woher?",
        de = "Lokale Präpositionen",
        keywords = listOf(
            "предлоги места", "lokale", "wechselpräposition", "wo или wohin", "in die", "ins ", " im ", "auf dem",
            "neben", "zwischen", "hinter", "unter ", "nach hause", "zu hause", "куда", "где лежит", "где стоит"
        ),
        sheet = """
**9 предлогов с двумя падежами:** in, an, auf, unter, über, vor, hinter, neben, zwischen.
**Wo?** (где, покой) → **Dativ** · **Wohin?** (куда, движение) → **Akkusativ**.

| Предлог | Wo? + Dativ | Wohin? + Akkusativ |
|---|---|---|
| in (в) | Ich bin **im** Kino / **in der** Schule. | Ich gehe **ins** Kino / **in die** Schule. |
| auf (на, сверху) | Das Buch liegt **auf dem** Tisch. | Ich lege das Buch **auf den** Tisch. |
| an (у, на вертикали) | Das Bild hängt **an der** Wand. | Ich hänge das Bild **an die** Wand. |
| unter (под) | Die Katze ist **unter dem** Bett. | Die Katze geht **unter das** Bett. |
| über (над) | Die Lampe hängt **über dem** Tisch. | Ich hänge die Lampe **über den** Tisch. |
| vor (перед) | Das Auto steht **vor dem** Haus. | Ich stelle das Auto **vor das** Haus. |
| hinter (за) | Der Garten ist **hinter dem** Haus. | Ich gehe **hinter das** Haus. |
| neben (рядом) | Die Bank ist **neben der** Post. | Ich stelle den Stuhl **neben den** Tisch. |
| zwischen (между) | Der Stuhl steht **zwischen dem** Tisch und **dem** Bett. | |

**Слияния:** in + dem = **im** · an + dem = **am** · in + das = **ins** · an + das = **ans**.

| | Wo? | Wohin? | Woher? |
|---|---|---|---|
| город, страна без артикля | **in** Berlin, in Polen | **nach** Berlin, nach Polen | **aus** Berlin, aus Polen |
| страна с артиклем | in **der** Schweiz, in **den** USA | in **die** Schweiz, in **die** USA | aus **der** Schweiz, aus **den** USA |
| человек, врач, фирма | **bei** Anna, **beim** Arzt | **zu** Anna, **zum** Arzt | **von** Anna, **vom** Arzt |
| дом | **zu Hause** | **nach Hause** | von zu Hause |
| здание, учреждение | in der Bank, im Supermarkt | zur Bank, zum Supermarkt / in den Supermarkt | aus dem Supermarkt |

**Глаголы-пары:** stehen (стоять) – stellen (ставить) · liegen (лежать) – legen (класть) · sitzen (сидеть) – sich setzen (садиться) · hängen (висеть / вешать).
**Наречия места:** hier, da, dort; links, rechts, geradeaus; oben, unten; vorne, hinten; in der Mitte; in der Nähe; gegenüber (von); an der Ecke; draußen, drinnen.
""".trimIndent()
    ),

    Topic(
        id = "quantities",
        block = "timeplace",
        level = "A1.1",
        title = "Количество: упаковки, вес, viel / wenig",
        de = "Mengen und Maße",
        keywords = listOf(
            "количеств", "упаковк", "menge", " kilo", "gramm", "liter", "flasche", "packung", "viel", "wenig",
            "ein bisschen", "сколько нужно", "stück"
        ),
        sheet = """
| Мера | Пример |
|---|---|
| ein Kilo (kg) / ein Pfund (500 g) / 100 Gramm | ein Kilo Tomaten, 200 Gramm Käse |
| ein Liter (l) | ein Liter Milch |
| eine Flasche | eine Flasche Wasser |
| ein Glas | ein Glas Saft, ein Glas Marmelade |
| eine Tasse | eine Tasse Tee |
| eine Packung | eine Packung Nudeln |
| eine Dose | eine Dose Cola |
| ein Becher | ein Becher Joghurt |
| eine Tüte | eine Tüte Chips |
| ein Stück | ein Stück Kuchen |
| eine Scheibe | eine Scheibe Brot |
| eine Tafel | eine Tafel Schokolade |
| ein Kasten | ein Kasten Bier |

**Правила:**
- Между мерой и продуктом ничего нет: eine Flasche Wasser (не «von Wasser»).
- Меры мужского и среднего рода после числа — в единственном: zwei **Kilo**, drei **Glas** Wasser, zwei **Stück** Kuchen, 500 **Gramm**, 10 **Euro**. Женского рода — во множественном: zwei Flasche**n**, drei Tasse**n**, zwei Packung**en**, zwei Dose**n**.
- **viel** (много, неисчисляемое) / **viele** (много, штук): viel Zeit, viel Geld — viele Freunde, viele Bücher.
- **wenig / wenige** (мало), **ein bisschen = etwas** (немного), **ein paar** (несколько), **genug** (достаточно), **mehr** (больше), **alle** (все), **alles** (всё), **nichts** (ничего).
- **zu viel**, **zu teuer** — слишком. **sehr** viel — очень много.
- **noch ein(e)** — ещё один: Noch einen Kaffee, bitte. **kein(e) … mehr** — больше нет: Wir haben keine Milch mehr.
- Wie viel kostet …? — Ein Kilo kostet 2 Euro. · Wie viele brauchen Sie? — Sechs Stück.
- Ich hätte gern / Ich möchte / Ich brauche + количество: Ich hätte gern 200 Gramm Käse.
""".trimIndent()
    ),

    Topic(
        id = "adjectives",
        block = "timeplace",
        level = "A1.2",
        title = "Прилагательные, цвета, сравнение",
        de = "Adjektive",
        keywords = listOf(
            "прилагательн", "adjektiv", "цвет", "farbe", "сравнени", "größer", "besser", "антоним", "противополож",
            "sehr ", "ziemlich", "какой он"
        ),
        sheet = """
После **sein / werden / finden** прилагательное не меняется: Das Haus ist **groß**. Die Wohnung ist **schön**. Die Kinder sind **klein**. Ich finde den Film **gut**.

**Пары:** groß – klein · alt – neu (вещь) / jung (человек) · gut – schlecht · schön – hässlich · teuer – billig / günstig · schnell – langsam · leicht – schwer · einfach – schwierig · warm – kalt · heiß – kalt · lang – kurz · hell – dunkel · laut – leise / ruhig · früh – spät · richtig – falsch · voll – leer · offen / geöffnet – geschlossen · sauber – schmutzig · gesund – krank · glücklich – traurig · interessant – langweilig · nah – weit · frei – besetzt · müde · nett, freundlich · wichtig · fertig · lecker.
**Усилители:** sehr (очень), zu (слишком), ziemlich (довольно), ganz (вполне), wirklich (действительно), nicht so (не так уж), ein bisschen (немного).
**Цвета:** rot, blau, grün, gelb, schwarz, weiß, grau, braun, orange, rosa, lila; hell- / dunkel-: hellblau, dunkelgrün; bunt. Welche Farbe hat das Auto? — Es ist rot.

**Сравнение:** + **er**, + **am …sten**: klein – kleiner – am kleinsten; schnell – schneller – am schnellsten. С умлаутом: groß – größer – am größten; alt – älter – am ältesten; jung – jünger; lang – länger; warm – wärmer; kalt – kälter; kurz – kürzer. Особые: gut – **besser** – am besten; viel – **mehr** – am meisten; gern – **lieber** – am liebsten; teuer – teurer; hoch – höher – am höchsten.
Berlin ist größer **als** Bonn. · Anna ist **so** alt **wie** Paul. · Das ist **zu** teuer.

**Перед существительным появляется окончание** (на A1 — узнавать и использовать в готовых фразах):

| | m | n | f | Pl |
|---|---|---|---|---|
| Nom, определённый | der klein**e** Tisch | das klein**e** Kind | die klein**e** Lampe | die klein**en** Kinder |
| Nom, неопределённый | ein klein**er** Tisch | ein klein**es** Kind | eine klein**e** Lampe | klein**e** Kinder |
| Akk | den / einen klein**en** Tisch | как в Nom | как в Nom | как в Nom |

Готовые фразы: Guten Tag! · Schönes Wochenende! · Viel**en** Dank! · Herzlich**en** Glückwunsch! · Liebe Grüße! · Gute Besserung! · Guten Appetit! · Frohe Weihnachten!
""".trimIndent()
    )
)

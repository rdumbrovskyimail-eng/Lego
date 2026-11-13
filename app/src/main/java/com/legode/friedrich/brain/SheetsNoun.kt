package com.legode.friedrich.brain

/* Блок «Существительное»: род и артикли, множественное число, Nominativ / Akkusativ / Dativ, местоимения, притяжательные. */

internal val nounTopics: List<Topic> = listOf(

    Topic(
        id = "gender_articles",
        block = "noun",
        level = "A0",
        title = "Род и артикли der / die / das",
        de = "Genus und bestimmter Artikel",
        keywords = listOf(
            "артикл", "род существительн", "какой род", "der die das", "der, die", "genus", "artikel",
            "мужской род", "женский род", "средний род", "der oder die"
        ),
        sheet = """
У каждого существительного есть род: **der** (мужской), **die** (женский), **das** (средний). Во множественном числе всегда **die**. Слово учим **вместе с артиклем**.

| der | die | das |
|---|---|---|
| мужчины, профессии: der Mann, der Lehrer | женщины: die Frau, die Lehrerin | детёныши и уменьшительные: das Kind, das Baby |
| дни, месяцы, времена года: der Montag, der Mai, der Sommer | большинство слов на **-e**: die Lampe, die Straße, die Schule | **-chen, -lein**: das Mädchen, das Brötchen |
| части суток: der Morgen, der Abend (но die Nacht) | **-ung, -heit, -keit, -schaft**: die Wohnung, die Freiheit | **-um, -ment, -o**: das Zentrum, das Dokument, das Auto, das Kino |
| погода, стороны света: der Regen, der Schnee, der Norden | **-ion, -tät, -ik, -ie, -ei, -ur**: die Station, die Universität, die Musik, die Bäckerei | инфинитив как существительное: das Essen, das Leben |
| **-er** (деятель, прибор): der Computer, der Fahrer | цифры: die Eins | языки и цвета: das Deutsch, das Rot |
| **-ling, -ismus, -or, -ant**: der Motor | деревья, многие цветы: die Rose | многие на Ge-: das Gemüse, das Geschenk, das Gespräch |
| алкоголь (но das Bier), марки машин: der Wein, der BMW | | гостиницы, кафе, кино: das Hotel, das Café |

Исключения есть всегда: **das** Mädchen (из-за -chen), **der** Junge, **der** Name, **der** Käse, **das** Ende, **das** Auge.
В сложном слове род определяет **последнее** слово: die Haus**tür** (das Haus + die Tür), der Kinder**garten**, das Wochen**ende**.
Определённый артикль — про известное или уже названное: Das ist **ein** Hund. **Der** Hund heißt Rex.
""".trimIndent()
    ),

    Topic(
        id = "indef_articles",
        block = "noun",
        level = "A0",
        title = "ein / eine, kein и нулевой артикль",
        de = "Unbestimmter Artikel, Nullartikel",
        keywords = listOf(
            "неопределённый артикль", "неопределенный артикль", "нулевой артикль", "без артикля",
            "unbestimmt", "ein oder eine", "когда ein"
        ),
        sheet = """
| | m | n | f | Pl |
|---|---|---|---|---|
| определённый | der | das | die | die |
| неопределённый | ein | ein | eine | — |
| отрицание | kein | kein | keine | keine |

**ein / eine** — впервые, «какой-то», один из многих: Das ist **ein** Tisch. Ich habe **eine** Frage. Во множественном числе неопределённого артикля нет: Das sind Tische.

**Без артикля:**
- профессия, национальность: Ich bin Arzt. Sie ist Ukrainerin.
- вещества и неисчисляемое: Ich trinke Kaffee. Ich brauche Geld. Ich habe Zeit / Hunger / Durst.
- неопределённое множественное: Ich kaufe Äpfel.
- города, большинство стран, имена: Ich wohne in Berlin. Das ist Anna.
- языки после sprechen / lernen: Ich lerne Deutsch.
- устойчивые сочетания: Auto fahren, Fußball spielen, Musik hören, nach Hause, zu Hause, mit Karte.

Сначала **ein**, потом **der**: Ich habe **einen** Hund. **Der** Hund ist klein.
«У меня нет…» → **kein**: Ich habe **keinen** Hund / **keine** Zeit / **kein** Geld / **keine** Kinder.
""".trimIndent()
    ),

    Topic(
        id = "plural",
        block = "noun",
        level = "A1.1",
        title = "Множественное число",
        de = "Plural",
        keywords = listOf("множествен", "plural", "мн. ч", "мн.ч", "множин", "во множественном"),
        sheet = """
Во множественном числе артикль всегда **die**. Форму учим вместе со словом. Пять типов:

| Окончание | Кто обычно | Примеры |
|---|---|---|
| **-e** (часто + умлаут) | многие слова мужского рода, односложные | der Tisch → die Tische, der Stuhl → die Stühle, die Stadt → die Städte, das Jahr → die Jahre, der Tag → die Tage |
| **-(e)n** | почти весь женский род; слова на -e, -ung, -heit, -keit, -ion | die Lampe → die Lampen, die Frau → die Frauen, die Zeitung → die Zeitungen, der Junge → die Jungen; -in → -innen: die Lehrerin → die Lehrerinnen |
| **-er** (+ умлаут) | односложные среднего рода | das Kind → die Kinder, das Buch → die Bücher, das Haus → die Häuser, der Mann → die Männer |
| **-s** | заимствования, слова на -a, -o, -i, -y | das Auto → die Autos, das Hotel → die Hotels, das Handy → die Handys, das Foto → die Fotos |
| **–** (без окончания, иногда умлаут) | мужской/средний род на -er, -en, -el, -chen, -lein | der Lehrer → die Lehrer, das Mädchen → die Mädchen, der Apfel → die Äpfel, der Bruder → die Brüder, die Mutter → die Mütter, die Tochter → die Töchter |

Умлаут возможен только у a → ä, o → ö, u → ü, au → äu.
**Только единственное:** das Obst, das Gemüse, die Milch, das Geld, das Wetter. **Только множественное:** die Eltern, die Leute, die Ferien, die Geschwister, die Lebensmittel, die Möbel.
**Dativ Plural:** к существительному + **n**: mit den Kinder**n**, mit den Freunde**n** (кроме слов на -n и -s: mit den Autos).
После числа — множественное: zwei Äpfel, drei Bücher. Но меры мужского/среднего рода — в единственном: zwei Kilo, drei Glas Wasser, 10 Euro, 5 Prozent.
В словаре пишут так: der Tisch, -e · das Buch, ¨-er · der Apfel, ¨- (¨ = умлаут).
""".trimIndent()
    ),

    Topic(
        id = "nom_akk",
        block = "noun",
        level = "A1.1",
        title = "Nominativ и Akkusativ",
        de = "Nominativ und Akkusativ",
        keywords = listOf(
            "akkusativ", "аккузатив", "винительн", "nominativ", "номинатив", "именительн", "падеж", "kasus",
            "den oder der", "einen"
        ),
        sheet = """
**Nominativ** (wer? was?) — подлежащее, а также после sein / heißen / werden: **Der** Mann ist nett. Das ist **ein** Tisch.
**Akkusativ** (wen? was?) — прямое дополнение: Ich sehe **den** Mann. Ich habe **einen** Bruder.
Меняется **только мужской род**: der → **den**, ein → **einen**, kein → **keinen**, mein → **meinen**.

| | m | n | f | Pl |
|---|---|---|---|---|
| Nom | der / ein / kein / mein | das / ein / kein / mein | die / eine / keine / meine | die / – / keine / meine |
| Akk | **den / einen / keinen / meinen** | das / ein / kein / mein | die / eine / keine / meine | die / – / keine / meine |

**Глаголы с Akkusativ:** haben, brauchen, kaufen, nehmen, möchten, essen, trinken, sehen, hören, lesen, suchen, finden, besuchen, kennen, lieben, machen, bezahlen, bestellen, fragen, verstehen, es gibt.
Ich brauche **einen** Stift. · Ich nehme **den** Bus. · Ich möchte **einen** Kaffee. · Es gibt **einen** Supermarkt. · Hast du **ein** Auto? · Ich kaufe **eine** Lampe. · Ich habe **keinen** Hunger.

**Предлоги всегда с Akkusativ:** für, ohne, durch, gegen, um, bis: für **meinen** Bruder, ohne **dich**, durch **den** Park, um 8 Uhr.
Проверка: кто делает? → Nominativ; кого/что (видит, имеет, покупает)? → Akkusativ.
""".trimIndent()
    ),

    Topic(
        id = "dative",
        block = "noun",
        level = "A1.2",
        title = "Dativ",
        de = "Dativ",
        keywords = listOf(
            "dativ", "датив", "дательн", "mit dem", "wem ", "einem", "einer ", "предлоги с dativ", "zum ", "zur "
        ),
        sheet = """
**Dativ** отвечает на wem? (кому?) и wo? (где?).

| | m | n | f | Pl |
|---|---|---|---|---|
| определённый | **dem** | **dem** | **der** | **den** + n |
| неопределённый | einem | einem | einer | – |
| kein / mein | keinem / meinem | keinem / meinem | keiner / meiner | keinen / meinen + n |

**Предлоги всегда с Dativ:** aus, bei, mit, nach, seit, von, zu (а также gegenüber, außer, ab):
mit **dem** Bus · mit **meiner** Frau · bei**m** Arzt · zu**m** Bahnhof · zu**r** Schule · vo**m** Arzt · aus **der** Türkei · nach **dem** Kurs · seit **einem** Jahr · mit **den** Kinder**n**.
**Слияния:** bei + dem = beim · von + dem = vom · zu + dem = zum · zu + der = zur · in + dem = im · an + dem = am.

**Глаголы с Dativ:** helfen (Ich helfe **dem** Mann / **dir**), danken (Ich danke **Ihnen**), gehören (Das Buch gehört **mir**), gefallen (Die Stadt gefällt **mir**), schmecken (Die Pizza schmeckt **mir**), passen, antworten, gratulieren, wehtun (Der Kopf tut **mir** weh), es geht (Wie geht es **dir**? — **Mir** geht es gut).

**Два дополнения:** кому (Dat) + что (Akk): Ich gebe **dem Kind** einen Apfel. · Ich schreibe **meiner Mutter** eine E-Mail. · Kannst du **mir** das Buch geben?
**Wo?** + in / an / auf… → Dativ: Ich bin in **der** Schule / i**m** Kino / a**m** Bahnhof.
""".trimIndent()
    ),

    Topic(
        id = "pronouns_cases",
        block = "noun",
        level = "A1.2",
        title = "Местоимения в Akkusativ и Dativ (mich, mir, dich, dir…)",
        de = "Personalpronomen im Akkusativ und Dativ",
        keywords = listOf("mich", "dich", " mir", " dir", " ihn", " ihm", "ihnen", " uns", "euch", "местоимения в"),
        sheet = """
| Nom | ich | du | er | sie | es | wir | ihr | sie | Sie |
|---|---|---|---|---|---|---|---|---|---|
| Akk (кого?) | mich | dich | ihn | sie | es | uns | euch | sie | Sie |
| Dat (кому?) | mir | dir | ihm | ihr | ihm | uns | euch | ihnen | Ihnen |

**Akkusativ:** Ich liebe **dich**. · Ich sehe **ihn**. · Kennst du **sie**? · Ich verstehe **Sie** nicht. · Das ist für **mich**. · Ruf **mich** an!
**Dativ:** Wie geht es **dir**? — **Mir** geht es gut. · Kannst du **mir** helfen? · Ich danke **Ihnen**. · Das gefällt **mir**. · Ich komme mit **dir**. · Gib **mir** bitte das Buch. · Wie geht es **ihm** / **ihr**?

**Порядок:** местоимение стоит перед существительным: Ich gebe **dir** das Buch. Два местоимения — сначала Akk, потом Dat: Ich gebe **es dir**.
Вещь тоже заменяем по роду: Ich kaufe den Tisch. → Ich kaufe **ihn**. · Ich lese die Zeitung. → Ich lese **sie**. · Ich nehme das Buch. → Ich nehme **es**.
""".trimIndent()
    ),

    Topic(
        id = "possessive",
        block = "noun",
        level = "A1.1",
        title = "Притяжательные местоимения (mein, dein, sein, ihr…)",
        de = "Possessivartikel",
        keywords = listOf(
            "притяжательн", "possessiv", "mein", "dein", "unser", "euer", "мой твой", "чей", "wessen"
        ),
        sheet = """
| ich | du | er | sie (она) | es | wir | ihr | sie (они) | Sie |
|---|---|---|---|---|---|---|---|---|
| mein | dein | sein | ihr | sein | unser | euer | ihr | Ihr |

Окончания — как у **ein / kein**:

| | m | n | f | Pl |
|---|---|---|---|---|
| Nom | mein | mein | meine | meine |
| Akk | mein**en** | mein | meine | meine |
| Dat | mein**em** | mein**em** | mein**er** | mein**en** (+n) |

Das ist **mein** Bruder / **meine** Schwester / **mein** Kind. Das sind **meine** Eltern.
Ich besuche **meinen** Bruder (Akk). · Ich wohne bei **meiner** Schwester (Dat). · Ich fahre mit **meinen** Kindern (Dat Pl).
euer + окончание → **eur-**: eure Mutter, euren Vater.
**sein** = его (владелец er / es), **ihr** = её или их: Paul und **sein** Hund. · Anna und **ihr** Hund. · Die Kinder und **ihre** Eltern.
В немецком нет слова «свой»: Ich liebe **meine** Familie. Er liebt **seine** Familie.
Чей? — Das ist Annas Buch. / Das ist das Buch **von** Anna.
""".trimIndent()
    )
)

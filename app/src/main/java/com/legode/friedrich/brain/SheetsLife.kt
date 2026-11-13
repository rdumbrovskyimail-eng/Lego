package com.legode.friedrich.brain

/* Блок «Ситуации и темы A1»: семья, еда, покупки, жильё, город, день, досуг, работа, здоровье, встречи, погода, письма, экзамен. */

internal val lifeTopics: List<Topic> = listOf(

    Topic(
        id = "family",
        block = "life",
        level = "A0",
        title = "Семья",
        de = "Familie",
        keywords = listOf(
            "семь", "familie", "mutter", "vater", "bruder", "schwester", "eltern", "geschwister", "родител",
            "брат", "сестр", "дет", "kinder", "муж", "жена", "verheiratet"
        ),
        sheet = """
| Он | Она | Вместе (Pl) |
|---|---|---|
| der Vater (Väter) — отец; Papa | die Mutter (Mütter) — мать; Mama | die Eltern — родители |
| der Sohn (Söhne) — сын | die Tochter (Töchter) — дочь | die Kinder — дети (das Kind) |
| der Bruder (Brüder) — брат | die Schwester (-n) — сестра | die Geschwister — братья и сёстры |
| der Großvater / Opa — дедушка | die Großmutter / Oma — бабушка | die Großeltern |
| der Enkel — внук | die Enkelin (-nen) — внучка | die Enkelkinder |
| der Onkel — дядя | die Tante (-n) — тётя | |
| der Cousin (-s) — двоюродный брат | die Cousine (-n) — двоюродная сестра | |
| der Neffe (-n) — племянник | die Nichte (-n) — племянница | |
| der (Ehe)Mann — муж | die (Ehe)Frau — жена | das Ehepaar |
| der Partner, der Freund | die Partnerin, die Freundin | |

**mein Freund / meine Freundin** = мой парень / моя девушка; просто друг — **ein Freund von mir**.
**Семейное положение:** ledig (не женат / не замужем), verheiratet (женат / замужем), geschieden (в разводе), verwitwet (вдовец / вдова), getrennt (раздельно).
**Фразы:** Ich habe **einen** Bruder und zwei Schwestern. · Ich habe **keine** Geschwister. Ich bin Einzelkind. · Meine Mutter heißt Olga. Sie ist 60 Jahre alt. · Mein Vater ist Ingenieur. · Meine Eltern leben in Kiew. · Ich bin verheiratet und habe zwei Kinder. · Mein Sohn ist fünf, meine Tochter ist acht. · Wir sind zu viert (нас четверо).
**Вопросы:** Hast du Geschwister? · Bist du verheiratet? · Hast du Kinder? · Wie heißt deine Frau / dein Mann? · Wo wohnen deine Eltern? · Wie alt ist dein Bruder?
""".trimIndent()
    ),

    Topic(
        id = "food",
        block = "life",
        level = "A1.1",
        title = "Еда, напитки, кафе и ресторан",
        de = "Essen und Trinken",
        keywords = listOf(
            "еда", "напит", "essen", "trinken", "restaurant", "кафе", "ресторан", "speisekarte", "rechnung",
            "frühstück", "завтрак", "обед", "ужин", "kaffee", "brot", "заказ", "bestellen", "вкусно", "lecker"
        ),
        sheet = """
**Приёмы пищи:** das Frühstück → frühstücken · das Mittagessen → zu Mittag essen · das Abendessen → zu Abend essen.

| Группа | Слова |
|---|---|
| хлеб, молочное | das Brot, das Brötchen, die Butter, der Käse, die Milch, der Joghurt, das Ei (die Eier), die Marmelade, der Honig |
| мясо, рыба | das Fleisch, das Hähnchen, die Wurst, der Schinken, der Fisch |
| гарниры, блюда | der Reis, die Nudeln (Pl), die Kartoffel (-n), die Pommes (Pl), die Suppe, der Salat, die Pizza |
| овощи (das Gemüse) | die Tomate (-n), die Gurke, die Zwiebel, die Karotte, die Paprika |
| фрукты (das Obst) | der Apfel (Äpfel), die Banane, die Orange, die Birne, die Erdbeere, die Zitrone |
| сладкое | der Kuchen, die Schokolade, das Eis, der Zucker |
| напитки (das Getränk, -e) | das Wasser, der Saft, der Kaffee, der Tee, die Cola, das Bier, der Wein |
| приправы | das Salz, der Pfeffer, das Öl |
| посуда | der Teller, das Glas, die Tasse, die Gabel, das Messer, der Löffel |

**В кафе:** Die Speisekarte, bitte. — Was möchten Sie? / Was darf es sein? — Ich möchte … / Ich nehme … / Ich hätte gern … / Für mich bitte eine Suppe. — Und zu trinken? — Ein Wasser, bitte. — Noch etwas? — Nein, danke, das ist alles.
Guten Appetit! — Danke, gleichfalls! · Prost! · Hat es geschmeckt? — Ja, sehr gut / sehr lecker.
**Счёт:** Zahlen, bitte! = Die Rechnung, bitte! — Zusammen oder getrennt? — Das macht 12,50 Euro. — 14 Euro, stimmt so. (сдачи не надо)
**О себе:** Ich habe Hunger / Durst. · Ich bin satt. · Ich esse gern Pizza. · Ich esse kein Fleisch. Ich bin Vegetarier(in). · Ich trinke keinen Alkohol. · Zum Frühstück esse ich Brot mit Käse und trinke Kaffee. · Was isst du gern? · Was trinkst du am liebsten?
**Вкус:** lecker, süß, sauer, salzig, scharf, frisch, heiß, kalt. Заказ — в Akkusativ: einen Kaffee, eine Cola, ein Bier.
""".trimIndent()
    ),

    Topic(
        id = "shopping",
        block = "life",
        level = "A1.1",
        title = "Покупки и магазины",
        de = "Einkaufen",
        keywords = listOf(
            "покупк", "магазин", "einkaufen", "supermarkt", "kasse", "kosten", "kostet", "teuer", "billig",
            "größe", "anprobieren", "bezahlen", "mit karte", "сколько стоит", "примерить", "bäckerei"
        ),
        sheet = """
**Магазины:** der Supermarkt, die Bäckerei (булочная), die Metzgerei (мясная), die Apotheke, die Drogerie, der Markt, das Kaufhaus, der Kiosk, das Geschäft / der Laden.
**Слова:** die Kasse, der Preis, das Angebot (im Angebot — по акции), der Rabatt, die Quittung / der Kassenbon (чек), das Wechselgeld (сдача), die Tüte (пакет), der Einkaufswagen; geöffnet – geschlossen, die Öffnungszeiten.

| Ситуация | Фразы |
|---|---|
| продавец | Kann ich Ihnen helfen? · Was darf es sein? · Sonst noch etwas? |
| ищу | Ich suche … · Haben Sie …? · Wo finde ich …? — Nein danke, ich schaue nur. |
| цена | Was kostet das? = Wie viel kostet das? · Was kosten die Äpfel? — Das kostet 3 Euro. |
| оценка | Das ist (zu) teuer / billig / günstig. · Das gefällt mir (nicht). |
| беру | Ich nehme das. · Ich hätte gern ein Kilo Tomaten. · Das ist alles, danke. |
| оплата | Das macht 8,40 Euro. · Bar oder mit Karte? — Mit Karte, bitte. · Kann ich mit Karte zahlen? |
| одежда | Haben Sie das in Größe M / 40? · Kann ich das anprobieren? · Wo ist die Umkleidekabine? |
| подходит ли | Das passt (mir) gut / nicht. · Zu groß / zu klein / zu eng / zu lang. · Haben Sie das in Blau? |
| возврат | Kann ich das umtauschen? · Ich möchte das zurückgeben. |

Цена: 2,99 € = zwei Euro neunundneunzig. Единственное: Was kost**et** das Brot? Множественное: Was kost**en** die Eier?
Akkusativ: Ich kaufe **einen** Pullover / **eine** Jacke / **ein** Hemd. Ich brauche **keine** Tüte.
""".trimIndent()
    ),

    Topic(
        id = "home",
        block = "life",
        level = "A1.2",
        title = "Жильё, комнаты, мебель, аренда",
        de = "Wohnen",
        keywords = listOf(
            "квартир", "wohnung", "zimmer", "комнат", "мебел", "möbel", "miete", "аренд", "küche", "vermieter",
            "жиль", "дом ", "nachbar", "umziehen"
        ),
        sheet = """
**Жильё:** die Wohnung (-en), das Haus (Häuser), das Zimmer (-), die Küche, das Bad / Badezimmer, das Wohnzimmer, das Schlafzimmer, das Kinderzimmer, der Flur, der Balkon, die Toilette, der Keller, der Garten, die Treppe, der Aufzug, das Erdgeschoss (EG), der Stock / die Etage: **im** ersten Stock.
**Мебель и техника:** der Tisch, der Stuhl (Stühle), das Bett, der Schrank, das Sofa, der Sessel, das Regal, die Lampe, der Teppich, der Spiegel, der Kühlschrank, der Herd, die Waschmaschine, die Spülmaschine, der Fernseher, die Tür, das Fenster, die Heizung.
**Аренда:** die Miete (kalt — без коммунальных, warm — с ними), die Nebenkosten (Pl), die Kaution (залог), der Vermieter / die Vermieterin, der Mieter, der Nachbar / die Nachbarin, der Quadratmeter (m²), mieten, vermieten, umziehen, einziehen, ausziehen, besichtigen.

**Описать:** Ich wohne in einer Wohnung / in einem Haus. · Meine Wohnung hat drei Zimmer, eine Küche und ein Bad. · Die Wohnung ist 60 Quadratmeter groß. · Sie ist hell / dunkel / ruhig / laut / groß / klein / teuer / günstig / möbliert. · Die Miete beträgt 700 Euro warm. · Im Wohnzimmer gibt es **ein** Sofa und **einen** Tisch. · Das Bad ist klein, aber modern. · Ich wohne im zweiten Stock.
**Спросить:** Ich suche eine Wohnung. · Ist the Wohnung noch frei? · Wie hoch ist die Miete? · Wie groß ist die Wohnung? · Wann kann ich die Wohnung besichtigen? · Gibt es einen Balkon? · Wo ist das Bad? — Hier links.
**В объявлениях:** 2 Zi. = 2 Zimmer · KM = Kaltmiete · WM = Warmmiete · NK = Nebenkosten · EG = Erdgeschoss · OG = Obergeschoss · ab sofort (сразу).
""".trimIndent()
    ),

    Topic(
        id = "city",
        block = "life",
        level = "A1.2",
        title = "Город, транспорт, как пройти",
        de = "In der Stadt, Verkehr, Wegbeschreibung",
        keywords = listOf(
            "город", "stadt", "транспорт", "bahnhof", "вокзал", "автобус", "bus", "u-bahn", "zug", "поезд",
            "fahrkarte", "билет", "wie komme ich", "как пройти", "дорог", "geradeaus", "links", "rechts",
            "haltestelle", "gleis", "umsteigen"
        ),
        sheet = """
**Места:** der Bahnhof (Hauptbahnhof), die Haltestelle, der Flughafen, die Post, die Bank, das Rathaus, die Apotheke, das Krankenhaus, die Schule, der Kindergarten, die Kirche, der Park, der Platz, die Straße, die Brücke, die Ampel, die Kreuzung, die Ecke, das Zentrum, das Museum, das Kino, das Hotel, die Polizei, das Amt (das Bürgeramt, die Ausländerbehörde, das Jobcenter).
**Транспорт:** der Bus, die U-Bahn, die S-Bahn, die Straßenbahn, der Zug, das Taxi, das Auto, das Fahrrad, das Flugzeug. → **mit dem** Bus, **mit der** U-Bahn, **mit dem** Fahrrad; пешком — **zu Fuß**.
**Билеты:** die Fahrkarte / das Ticket; einfach (в одну сторону) / hin und zurück (туда и обратно); das Gleis (путь), die Abfahrt, die Ankunft, die Verspätung, einsteigen, aussteigen, umsteigen.

| Спросить дорогу | Объяснить |
|---|---|
| Entschuldigung, wo ist der Bahnhof? | Gehen Sie geradeaus. |
| Wie komme ich **zum** Bahnhof / **zur** Post? | Dann links / rechts. |
| Gibt es hier in der Nähe eine Apotheke? | Die erste / zweite Straße links. |
| Ist das weit? | An der Ampel rechts. · Bis zur Kreuzung. |
| Wo ist die nächste Haltestelle? | Das ist gleich um die Ecke / da vorne / gegenüber. |
| Welcher Bus fährt zum Zentrum? | Nehmen Sie den Bus Nummer 5. |
| | Nein, nur fünf Minuten zu Fuß. |
| | Tut mir leid, ich bin nicht von hier. |

**На вокзале:** Eine Fahrkarte nach Köln, bitte. — Einfach oder hin und zurück? · Wann fährt der nächste Zug nach Hamburg? · Von welchem Gleis? — Von Gleis 3. · Muss ich umsteigen? · Der Zug hat zehn Minuten Verspätung. · Ist der Platz frei? · Wo muss ich aussteigen?
zu + dem = **zum** (m, n): zum Bahnhof, zum Rathaus. zu + der = **zur** (f): zur Post, zur Schule.
""".trimIndent()
    ),

    Topic(
        id = "daily",
        block = "life",
        level = "A1.1",
        title = "Распорядок дня",
        de = "Tagesablauf",
        keywords = listOf(
            "распорядок", "tagesablauf", "мой день", "утром", "вечером", "aufwachen", "duschen", "ins bett",
            "что ты делаешь", "каждый день", "morgens", "abends", "обычно"
        ),
        sheet = """
**Действия:** aufwachen (просыпаться) · aufstehen · duschen · sich anziehen (Ich ziehe mich an) · frühstücken · zur Arbeit gehen / fahren · arbeiten · Pause machen · zu Mittag essen · nach Hause kommen · einkaufen · kochen · zu Abend essen · aufräumen · fernsehen · lesen · ins Bett gehen · schlafen.

**Пример:** Ich stehe um 7 Uhr auf. Dann dusche ich und frühstücke. Um 8 Uhr fahre ich zur Arbeit. Ich arbeite von 9 bis 17 Uhr. Am Abend koche ich und sehe fern. Um 23 Uhr gehe ich ins Bett.

| Часть дня | Один раз | Регулярно |
|---|---|---|
| утро | am Morgen | morgens |
| до обеда | am Vormittag | vormittags |
| полдень | am Mittag | mittags |
| после обеда | am Nachmittag | nachmittags |
| вечер | am Abend | abends |
| ночь | in der Nacht | nachts |

**Как часто:** immer – meistens – oft – manchmal – selten – nie; jeden Tag; einmal / zweimal pro Woche; am Wochenende; montags.
**Порядок:** zuerst → dann → danach → später → zum Schluss. После них сразу глагол: Zuerst **frühstücke** ich.
**Вопросы:** Wann stehst du auf? · Was machst du am Abend? · Was machst du am Wochenende? · Wie lange arbeitest du? · Wann gehst du ins Bett? · Was machst du nach der Arbeit?
Помни про рамку: Ich **stehe** um 7 Uhr **auf**. Ich **sehe** abends **fern**. Ich **kaufe** am Samstag **ein**.
""".trimIndent()
    ),

    Topic(
        id = "freetime",
        block = "life",
        level = "A1.1",
        title = "Досуг, хобби, приглашения",
        de = "Freizeit und Verabredungen",
        keywords = listOf(
            "хобби", "hobby", "freizeit", "досуг", "свободное время", "kino", "спорт", "sport", "einladung",
            "приглаш", "hast du lust", "hast du zeit", "wollen wir", "urlaub", "отпуск", "праздник"
        ),
        sheet = """
**Хобби:** lesen, kochen, schwimmen, tanzen, singen, malen, wandern, reisen, fotografieren, joggen, Sport machen, Fußball spielen, Gitarre / Klavier spielen, Musik hören, Filme sehen, Rad fahren, spazieren gehen, ins Kino gehen, Freunde treffen, im Internet surfen.
Was machst du in der Freizeit? — Ich lese **gern**. · Ich spiele gern Fußball. · Am Wochenende gehe ich oft ins Kino. · Mein Hobby ist Kochen. · Ich mache zweimal pro Woche Sport.
spielen + без артикля: Fußball / Tennis / Karten / Gitarre spielen.

| Пригласить | Согласиться | Отказаться |
|---|---|---|
| Hast du heute Abend Zeit? | Ja, gern! | Tut mir leid, ich kann leider nicht. |
| Hast du Lust auf Kino? | Gute Idee! | Ich habe leider keine Zeit. |
| Wollen wir ins Kino gehen? | Ja, klar! / Ja, warum nicht? | Nein, ich habe keine Lust. |
| Kommst du mit? | Ich komme gern mit. | Vielleicht morgen? |
| Gehen wir zusammen essen? | Super! | Heute geht es leider nicht. |

**Договориться:** Wann treffen wir uns? — Um 19 Uhr. · Wo treffen wir uns? — Vor dem Kino. / Am Bahnhof. · Passt dir Samstag? — Ja, das passt. · Bis dann! Bis Samstag!
**Отпуск:** Ich mache Urlaub in Spanien. · Ich fahre ans Meer / in die Berge / nach Italien. · Ich war im Sommer in der Türkei.
**Поздравления:** Herzlichen Glückwunsch (zum Geburtstag)! · Alles Gute! · Frohe Weihnachten! · Frohe Ostern! · Guten Rutsch! · Frohes neues Jahr! · Viel Spaß! · Viel Glück! · Viel Erfolg! · Gute Reise!
""".trimIndent()
    ),

    Topic(
        id = "work",
        block = "life",
        level = "A1.2",
        title = "Работа и профессии",
        de = "Arbeit und Beruf",
        keywords = listOf(
            "работ", "професси", "arbeit", "beruf", "arbeite als", "firma", "chef", "kollege", "büro",
            "gehalt", "bewerbung", "arbeitslos", "ausbildung", "vollzeit", "teilzeit"
        ),
        sheet = """
| Он | Она | Перевод |
|---|---|---|
| der Arzt | die Ärztin | врач |
| der Lehrer | die Lehrerin | учитель |
| der Verkäufer | die Verkäuferin | продавец |
| der Koch | die Köchin | повар |
| der Fahrer | die Fahrerin | водитель |
| der Ingenieur | die Ingenieurin | инженер |
| der Krankenpfleger | die Krankenschwester | медбрат / медсестра |
| der Kellner | die Kellnerin | официант |
| der Mechaniker | die Mechanikerin | механик |
| der Programmierer | die Programmiererin | программист |
| der Friseur | die Friseurin | парикмахер |
| der Polizist | die Polizistin | полицейский |
| der Elektriker | die Elektrikerin | электрик |
| der Hausmann | die Hausfrau | домохозяин / домохозяйка |
| der Student | die Studentin | студент |
| der Rentner | die Rentnerin | пенсионер |

**О себе:** Was sind Sie von Beruf? — Ich bin Lehrer(in). (без артикля!) · Ich arbeite **als** Fahrer. · Ich arbeite **bei** Siemens / **in** einem Büro / **im** Krankenhaus / **in** einer Schule. · Ich bin arbeitslos. Ich suche Arbeit. · Ich mache eine Ausbildung. · Ich studiere Medizin. · Ich bin Rentner(in). · Ich bin in Elternzeit.
**Условия:** die Vollzeit / die Teilzeit, die Schicht, die Pause, der Feierabend (конец рабочего дня), der Urlaub, frei haben, das Gehalt / der Lohn, der Chef / die Chefin, der Kollege / die Kollegin, die Firma, das Büro, die Stelle, die Bewerbung, der Lebenslauf, das Vorstellungsgespräch, der Arbeitsvertrag.
Ich arbeite von Montag bis Freitag, 40 Stunden pro Woche. · Ich arbeite von 8 bis 16 Uhr. · Meine Arbeit is interessant / anstrengend / langweilig. · Am Wochenende habe ich frei. · Ich habe drei Wochen Urlaub.
**Сообщить о болезни:** Guten Morgen, hier ist … Ich bin krank und kann heute leider nicht kommen.
""".trimIndent()
    ),

    Topic(
        id = "health",
        block = "life",
        level = "A1.2",
        title = "Здоровье, врач, аптека",
        de = "Gesundheit, Arzt, Apotheke",
        keywords = listOf(
            "здоров", "врач", "arzt", "болит", "schmerzen", "krank", "apotheke", "аптек", "fieber", "husten",
            "tut weh", "medikament", "rezept", "тело", "körper", "боле", "gute besserung", "112"
        ),
        sheet = """
**Тело (der Körper):** der Kopf, das Auge (-n), das Ohr (-en), die Nase, der Mund, der Zahn (Zähne), der Hals, der Rücken, der Bauch, die Brust, die Schulter, der Arm (-e), die Hand (Hände), der Finger, das Bein (-e), das Knie, der Fuß (Füße), das Herz.

| Жалоба | По-немецки |
|---|---|
| я болен / больна | Ich bin krank. · Mir geht es nicht gut. · Mir ist schlecht. |
| болит голова, горло, живот, зуб, спина | Ich habe Kopfschmerzen / Halsschmerzen / Bauchschmerzen / Zahnschmerzen / Rückenschmerzen. |
| что-то болит | Mein Kopf tut weh. · Meine Augen tun weh. · Der Bauch tut **mir** weh. |
| температура, кашель, насморк | Ich habe Fieber / Husten / Schnupfen. |
| простуда, грипп, аллергия | Ich habe eine Erkältung / eine Grippe / eine Allergie. · Ich bin erkältet. |

**У врача:** Was fehlt Ihnen? / Was haben Sie? · Wo tut es weh? — Hier. · Seit wann haben Sie das? — Seit drei Tagen. · Ihre Versichertenkarte, bitte. · Nehmen Sie bitte im Wartezimmer Platz. · Ich schreibe Ihnen ein Rezept. · Nehmen Sie die Tabletten dreimal täglich nach dem Essen. · Sie müssen im Bett bleiben und viel trinken. · Ich brauche eine Krankschreibung. · **Gute Besserung!**
**В аптеке:** Ich brauche etwas gegen Kopfschmerzen / Husten. · Haben Sie etwas gegen Fieber? · das Medikament (-e), die Tablette (-n), die Salbe, die Tropfen (Pl), das Pflaster, das Rezept.
**Слова:** der Hausarzt / die Hausärztin, der Zahnarzt, die Praxis, die Sprechstunde, der Termin, das Krankenhaus, die Krankenkasse, die Krankenversicherung, gesund – krank.
**Экстренно:** **112** — скорая и пожарные, **110** — полиция. Hilfe! · Rufen Sie bitte einen Krankenwagen! · Ich brauche einen Arzt. · Es ist ein Notfall.
""".trimIndent()
    ),

    Topic(
        id = "appointments",
        block = "life",
        level = "A1.2",
        title = "Запись на приём, встречи, телефон",
        de = "Termine und Telefonieren",
        keywords = listOf(
            "termin", "запис", "приём", "прием", "встреч", "телефон", "telefon", "anrufen", "absagen",
            "verschieben", "zurückrufen", "falsch verbunden", "договор"
        ),
        sheet = """
| Задача | Фразы |
|---|---|
| записаться | Ich hätte gern einen Termin. · Ich möchte einen Termin machen / vereinbaren. |
| когда? | Wann haben Sie Zeit? · Haben Sie am Montag einen Termin frei? · Geht es am Dienstag um 10 Uhr? |
| согласиться | Ja, das passt (gut). · Ja, das geht. · Gut, dann bis Dienstag. |
| не подходит | Nein, da kann ich leider nicht. · Geht es auch später / am Nachmittag / am Freitag? |
| отменить, перенести | Ich muss den Termin leider absagen. · Kann ich den Termin verschieben? |
| опаздываю | Ich komme etwas später. · Entschuldigung, ich habe Verspätung. |
| на месте | Guten Tag, ich habe einen Termin bei Dr. Müller um 9 Uhr. |

**По телефону:** отвечают фамилией: «Schmidt, guten Tag.» · Hallo, hier ist Anna Petrova. / Hier spricht … · Kann ich bitte Herrn Müller / Frau Braun sprechen? · Einen Moment, bitte. · Er ist leider nicht da. · Kann ich eine Nachricht hinterlassen? · Können Sie mich bitte zurückrufen? Meine Nummer ist … · Können Sie das bitte wiederholen? · Entschuldigung, falsch verbunden. · **Auf Wiederhören!**
**С друзьями:** Hast du morgen Zeit? · Wann treffen wir uns? · Um wie viel Uhr? · Passt dir 18 Uhr? — Ja, passt! · Bis morgen!
**Предлоги:** **am** Montag, **um** 10 Uhr, **bei** Dr. Müller, **beim** Zahnarzt, **im** Bürgeramt.
""".trimIndent()
    ),

    Topic(
        id = "weather_clothes",
        block = "life",
        level = "A1.2",
        title = "Погода, времена года, одежда",
        de = "Wetter, Jahreszeiten, Kleidung",
        keywords = listOf(
            "погод", "wetter", "regnet", "дожд", "schnee", "снег", "sonne", "солнц", "одежд", "kleidung",
            "jacke", "hose", "schuhe", "tragen", "надеть", "grad", "градус", "холодно", "жарко"
        ),
        sheet = """
**Wie ist das Wetter (heute)?**

| По-немецки | Перевод |
|---|---|
| Es ist sonnig / warm / heiß / kalt / kühl / windig / bewölkt / neblig. | солнечно / тепло / жарко / холодно / прохладно / ветрено / облачно / туманно |
| Die Sonne scheint. | Светит солнце. |
| Es regnet. / Es schneit. | Идёт дождь. / Идёт снег. |
| Es gibt ein Gewitter. | Гроза. |
| Es sind 20 Grad. / Es sind minus 5 Grad. | 20 градусов. / Минус 5. |
| Das Wetter ist schön / schlecht. | Погода хорошая / плохая. |

der Regen, der Schnee, der Wind, die Sonne, die Wolke (-n), das Gewitter, der Nebel, die Temperatur.
**Времена года:** der Frühling, der Sommer, der Herbst, der Winter → **im** Sommer. **Стороны света:** der Norden, der Süden, der Osten, der Westen → **im** Norden.
Im Winter ist es kalt und es schneit. · Im Sommer ist es heiß. · Wie ist das Wetter in Russland?

**Одежда (die Kleidung):** die Hose, die Jeans, der Rock, das Kleid, das Hemd, die Bluse, das T-Shirt, der Pullover, der Jacke, der Mantel, der Anzug, der Schuh (-e), der Stiefel (-), die Socke (-n), die Mütze, der Schal, der Handschuh (-e), die Tasche, der Regenschirm, die Brille.
**tragen** (du trägst, er trägt) — носить; **anziehen** — надевать; **ausziehen** — снимать: Ich trage **eine** Jacke und **einen** Schal. · Was ziehst du heute an? · Zieh den Mantel an, es ist kalt!
**Цвета:** rot, blau, grün, gelb, schwarz, weiß, grau, braun, orange, rosa, lila. Die Jacke ist blau. · Welche Farbe hat dein Auto?
""".trimIndent()
    ),

    Topic(
        id = "forms_letters",
        block = "life",
        level = "A1.2",
        title = "Анкеты, письма, сообщения, таблички",
        de = "Formulare, Briefe, Nachrichten, Schilder",
        keywords = listOf(
            "анкет", "formular", "письм", "brief", "e-mail", "сообщени", "nachricht", "sehr geehrte",
            "mit freundlichen", "liebe grüße", "заполн", "ausfüllen", "unterschrift", "табличк", "schild",
            "написать письмо", "напиши письмо"
        ),
        sheet = """
**Анкета (das Formular):**

| Поле | Перевод |
|---|---|
| der Vorname / der Nachname (Familienname) | имя / фамилия |
| das Geburtsdatum / der Geburtsort | дата / место рождения |
| das Geschlecht: männlich / weiblich / divers | пол |
| die Staatsangehörigkeit | гражданство |
| der Familienstand: ledig / verheiratet / geschieden / verwitwet | семейное положение |
| die Adresse: Straße, Hausnummer, Postleitzahl (PLZ), Ort | адрес: улица, дом, индекс, город |
| die Telefonnummer / die E-Mail-Adresse | телефон / почта |
| der Beruf | профессия |
| das Datum / die Unterschrift | дата / подпись |

ausfüllen (заполнить), ankreuzen (отметить крестиком), unterschreiben (подписать), buchstabieren (по буквам). Дата: 10.11.2026.

**Официальное письмо:** Sehr geehrte Frau Müller, / Sehr geehrter Herr Schmidt, / Sehr geehrte Damen und Herren, → после запятой текст с **маленькой** буквы. В конце: **Mit freundlichen Grüßen** + имя (без запятой).
**Другу:** Liebe Anna, / Lieber Paul, / Hallo Tom, … В конце: Viele Grüße / Liebe Grüße / Bis bald + имя.
**Готовые фразы:** vielen Dank für Ihre E-Mail / deine Nachricht. · ich möchte einen Termin machen. · ich kann am Montag leider nicht kommen, denn ich bin krank. · Können Sie mir bitte Informationen schicken? · Bitte antworten Sie mir bald. · Ich freue mich auf Ihre Antwort. · Wann hast du Zeit?
**Пример:** «Liebe Anna, vielen Dank für die Einladung. Ich komme gern. Soll ich etwas mitbringen? Bis Samstag! Liebe Grüße, Tom»

**Таблички:** Eingang (вход) · Ausgang (выход) · Notausgang · Geöffnet / Geschlossen · Drücken (от себя) / Ziehen (на себя) · Rauchen verboten · Kein Zutritt · Besetzt / Frei · Achtung! · Vorsicht! · Außer Betrieb (не работает) · Kasse · WC: Damen / Herren · Auskunft / Information · Anmeldung · Ruhetag (выходной).
""".trimIndent()
    ),

    Topic(
        id = "exam_a1",
        block = "life",
        level = "A1.2",
        title = "Экзамен A1 (Goethe-Zertifikat A1: Start Deutsch 1)",
        de = "Prüfung A1",
        keywords = listOf(
            "экзамен", "prüfung", "goethe", "start deutsch", "сертификат", "zertifikat", "telc", "sprechen teil",
            "schreiben teil"
        ),
        sheet = """
Четыре части; для сдачи нужно набрать **60 из 100** баллов. Формат иногда обновляют — уточняй на сайте экзаменационного центра.

| Часть | Время | Что делать |
|---|---|---|
| **Hören** | ≈ 20 мин | Teil 1: короткие диалоги, выбор a / b / c. Teil 2: объявления, richtig / falsch. Teil 3: сообщения на автоответчике, a / b / c. |
| **Lesen** | ≈ 25 мин | Teil 1: записки и письма, richtig / falsch. Teil 2: объявления — выбрать a или b. Teil 3: таблички и вывески, richtig / falsch. |
| **Schreiben** | ≈ 20 мин | Teil 1: заполнить анкету (5 пропусков). Teil 2: короткое сообщение (≈ 30 слов) по трём пунктам. |
| **Sprechen** | ≈ 15 мин, в группе | Teil 1: представиться. Teil 2: спросить и ответить по карточке (тема + слово). Teil 3: попросить о чём-то и отреагировать на просьбу. |

**Sprechen 1 — представиться:** Name? Alter? Land? Wohnort? Sprachen? Beruf? Hobby? → «Ich heiße … Ich bin … Jahre alt. Ich komme aus … Ich wohne in … Ich spreche … Ich bin … von Beruf. Mein Hobby ist …» Потом просят продиктовать по буквам фамилию и назвать номер.
**Sprechen 2 — карточки:** тема «Essen und Trinken», слово «Frühstück» → «Was isst du zum Frühstück?» — «Ich esse Brot mit Käse.»
**Sprechen 3 — просьбы:** «Können Sie bitte das Fenster aufmachen?» / «Gib mir bitte den Stift.» — «Ja, gern.» / «Tut mir leid, das geht nicht.»
**Schreiben 2:** обращение (Liebe … / Sehr geehrte …) + по одному-два предложения на каждый из трёх пунктов + прощание (Viele Grüße / Mit freundlichen Grüßen) + имя.
**Советы:** отвечай полными, но простыми предложениями; если не понял — «Wie bitte? Können Sie das bitte wiederholen?»; не оставляй пустых ответов.
""".trimIndent()
    )
)

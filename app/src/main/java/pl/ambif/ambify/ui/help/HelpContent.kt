package pl.ambif.ambify.ui.help

/**
 * The help behind "?" (0.7.8, canvas board 6 — the standard): every screen has one. Title = the screen's title;
 * 1–3 sentences; topics (nouns of doing), each with steps, or with an intro and subtopics (one level deep at most);
 * steps start with a verb, ≤ 14 words, button names in „” as in the app. Change a screen → change its help in the
 * same release (Michał, 25.11).
 */
data class HelpScreen(val title: String, val intro: String, val topics: List<HelpTopic>)

data class HelpTopic(
    val name: String,
    val steps: List<HelpStep> = emptyList(),
    val intro: String? = null,
    val subs: List<HelpTopic> = emptyList(),
    /** "Kto może" — only where a role or Premium decides. */
    val who: String? = null,
    val premium: Boolean = false,
)

data class HelpStep(val text: String, val mock: Mock, val label: String = "")

/** The step's little picture: what the finger does. */
enum class Mock { TAP, HOLD, DRAG, DROP, SWIPE, MENU, CHECK, TYPE, TOGGLE }

private fun s(text: String, mock: Mock, label: String = "") = HelpStep(text, mock, label)

object Help {
    val LOGIN = HelpScreen(
        "Logowanie", "Wejście do AMBiFy kontem z e-mailem i hasłem. Jedno konto działa w każdym Twoim kręgu.",
        listOf(
            HelpTopic("Logowanie", listOf(s("Wpisz e-mail i hasło.", Mock.TYPE, "ola@…"), s("Stuknij „Zaloguj”.", Mock.TAP, "Zaloguj"))),
            HelpTopic("Zakładanie konta", listOf(s("Stuknij „Załóż konto”.", Mock.TAP, "Załóż konto"), s("Podaj imię, e-mail i hasło — krąg wybierzesz potem.", Mock.TYPE, "Ola"))),
            HelpTopic("Zapomniane hasło", listOf(
                s("Stuknij „Nie pamiętam hasła”.", Mock.TAP, "Nie pamiętam"),
                s("Wpisz kod od Ownera albo Admina kręgu i nowe hasło.", Mock.TYPE, "K7Q-2M"),
            ), who = "Kod daje Owner lub Admin kręgu, jeśli należysz tylko do niego. Ownerowi i osobie z kilku kręgów — zespół AMBiFy."),
        ),
    )

    val START = HelpScreen(
        "Bez kręgu", "Masz konto, ale nie należysz jeszcze do żadnego kręgu. Załóż własny albo dołącz kodem.",
        listOf(
            HelpTopic("Zakładanie kręgu", listOf(
                s("Stuknij kartę „Załóż krąg”.", Mock.TAP, "Załóż krąg"),
                s("Wpisz nazwę, np. „Pacakowie”.", Mock.TYPE, "Pacakowie"),
                s("Stuknij złoty „Załóż krąg” — zostaniesz jego Ownerem.", Mock.TAP, "Załóż krąg"),
            )),
            HelpTopic("Dołączanie z kodem", listOf(
                s("Stuknij kartę „Mam kod zaproszenia”.", Mock.TAP, "Mam kod"),
                s("Wpisz kod od Ownera albo Admina.", Mock.TYPE, "AB12CD"),
                s("Stuknij „Dołącz do kręgu”.", Mock.TAP, "Dołącz"),
            )),
        ),
    )

    val DASHBOARD = HelpScreen(
        "Pulpit", "Twoje widgety w jednym miejscu — ułóż je po swojemu. Każdy krąg ma osobny Pulpit.",
        listOf(
            HelpTopic("Dodawanie widgetu", listOf(
                s("Stuknij „Dodaj widget” na dole Pulpitu.", Mock.TAP, "Dodaj widget"),
                s("Wybierz moduł i widget z listy.", Mock.MENU),
                s("Ustaw rozmiar i stuknij „Dodaj”.", Mock.TAP, "Dodaj"),
            )),
            HelpTopic("Zmiana pozycji widgetu", listOf(
                s("Przytrzymaj widget, aż się uniesie.", Mock.HOLD),
                s("Przesuń go w górę albo w dół.", Mock.DRAG),
                s("Upuść w nowym miejscu.", Mock.DROP),
            )),
            HelpTopic("Rozmiar i ustawienia widgetu", listOf(
                s("Stuknij trybik w nagłówku widgetu.", Mock.TAP, "⚙"),
                s("Wybierz szerokość, wysokość i to, co ma pokazywać.", Mock.MENU),
                s("Stuknij „Zapisz”.", Mock.TAP, "Zapisz"),
            )),
            HelpTopic("Usuwanie widgetu", listOf(s("Stuknij trybik w nagłówku widgetu.", Mock.TAP, "⚙"), s("Stuknij „Usuń z pulpitu”.", Mock.TAP, "Usuń z pulpitu"))),
            HelpTopic("Pogoda", listOf(
                s("Przesuń widget w bok albo stuknij strzałki, żeby zmienić miejsce.", Mock.SWIPE),
                s("Stuknij widget, żeby zobaczyć prognozę na 16 dni.", Mock.TAP, "Pogoda"),
                s("W trybiku stuknij nazwę miejsca, żeby ją zmienić.", Mock.TAP, "Dom"),
                s("Przeciągnij miejsce za uchwyt, żeby zmienić kolejność.", Mock.DRAG),
            )),
            HelpTopic("Przełączanie kręgu", listOf(s("Stuknij nazwę kręgu na górze.", Mock.TAP, "Pacakowie ▾"), s("Wybierz inny krąg z listy.", Mock.MENU))),
        ),
    )

    val WIDGETS = HelpScreen(
        "Widgety", "Wszystkie widgety modułów włączonych w Twoim kręgu. Każdy moduł ma co najmniej jeden.",
        listOf(
            HelpTopic("Wybór widgetu", listOf(s("Stuknij moduł, żeby zobaczyć jego widgety.", Mock.TAP, "Listy"), s("Stuknij widget, żeby go ustawić i dodać.", Mock.TAP, "Dodaj"))),
            HelpTopic("Podgląd i rozmiar", listOf(s("Wybierz „Pół szerokości” albo całą i wysokość.", Mock.MENU), s("Sprawdź podgląd i stuknij „Dodaj”.", Mock.TAP, "Dodaj"))),
        ),
    )

    val FORECAST = HelpScreen(
        "Prognoza", "Pogoda teraz, dziś co godzinę i na 16 dni dla miejsc z widgetu Pogoda.",
        listOf(
            HelpTopic("Zmiana miejsca", listOf(s("Stuknij nazwę miejsca na górze.", Mock.TAP, "Dom"))),
            HelpTopic("Godziny wybranego dnia", listOf(s("Stuknij dzień na liście.", Mock.TAP, "Pt. 27.11"), s("Przewiń godziny w bok.", Mock.SWIPE))),
            HelpTopic("Kolory temperatur", listOf(s("Sprawdź kolor: granat lodowato, niebieski mróz, czerwony upał.", Mock.TAP)),
                intro = null),
        ),
    )

    val LISTS = HelpScreen(
        "Listy", "Zakupy, rzeczy do spakowania i zadania — wspólne dla całego kręgu. Każdą zmianę wszyscy widzą od razu.",
        listOf(
            HelpTopic("Nowa lista", listOf(s("Stuknij „+ Nowa lista” pod listami.", Mock.TAP, "+ Nowa lista"), s("Wybierz rodzaj i wpisz nazwę.", Mock.TYPE, "Biedronka"))),
            HelpTopic("Filtry", listOf(s("Stuknij „Wszystkie”, „Listy rzeczy”, „Zadania” albo „Terminy”.", Mock.TAP, "Zadania"))),
            HelpTopic("Ustawienia, dostęp, archiwum i usuwanie", listOf(
                s("Przytrzymaj listę.", Mock.HOLD),
                s("Wybierz „Ustawienia”, „Dostęp”, „Archiwizuj” albo „Usuń”.", Mock.MENU),
            ), who = "Dostęp i usuwanie: autor listy, Owner i role z uprawnieniem."),
        ),
    )

    val LIST = HelpScreen(
        "Lista", "Pozycje jednej listy. Odhaczone spadają na dół, a pamięć podpowiada to, co już kupowaliście.",
        listOf(
            HelpTopic("Dodawanie pozycji", listOf(
                s("Stuknij „Dodaj pozycję”.", Mock.TAP, "Dodaj pozycję"),
                s("Wpisz nazwę i stuknij „Dodaj”.", Mock.TYPE, "Mleko"),
                s("Stuknij „Z pamięci”, żeby wybrać kilka używanych wcześniej.", Mock.TAP, "Z pamięci"),
                s("W pamięci przytrzymaj produkt, żeby go z niej usunąć.", Mock.HOLD),
            )),
            HelpTopic("Odhaczanie", listOf(s("Stuknij kwadrat przy pozycji.", Mock.CHECK))),
            HelpTopic("Ilość i jednostki", listOf(
                s("Stuknij liczbę przy pozycji i wpisz dowolną ilość, np. 0,5.", Mock.TYPE, "0,5"),
                s("Przytrzymaj własną jednostkę, żeby poprawić jej odmianę.", Mock.HOLD),
            )),
            HelpTopic(
                "Grupowanie w liście",
                intro = "W listach możesz grupować pozycje pod jedną nazwą — np. zakupy w konkretnym sklepie, zadania na dany dzień albo dla jednej osoby. Ile grup, zależy od Ciebie.",
                subs = listOf(
                    HelpTopic("Tworzenie grupy", listOf(
                        s("Przytrzymaj pozycję, żeby zacząć zaznaczanie.", Mock.HOLD),
                        s("Stuknij kolejne pozycje, które mają być razem.", Mock.CHECK),
                        s("Stuknij „Do grupy” → „Nowa grupa” i wpisz nazwę.", Mock.TAP, "Do grupy"),
                    )),
                    HelpTopic("Dodawanie do grupy", listOf(s("Zaznacz pozycje przytrzymaniem.", Mock.HOLD), s("Stuknij „Do grupy” i wybierz grupę.", Mock.MENU))),
                    HelpTopic("Wyjmowanie z grupy", listOf(s("Zaznacz pozycję w grupie przytrzymaniem.", Mock.HOLD), s("Stuknij „Wyjmij z grupy”.", Mock.TAP, "Wyjmij z grupy"))),
                    HelpTopic("Zwijanie grupy", listOf(s("Stuknij nazwę grupy, żeby ją zwinąć albo rozwinąć.", Mock.TAP, "Biedronka ▾"))),
                ),
                premium = true,
            ),
            HelpTopic("Kilka pozycji naraz", listOf(
                s("Przytrzymaj pozycję, potem stuknij kolejne.", Mock.CHECK),
                s("Stuknij „Odhacz” albo „Usuń” na dole.", Mock.TAP, "Odhacz"),
            )),
            HelpTopic("Zdjęcia, pliki i szczegóły", listOf(s("Stuknij nazwę pozycji.", Mock.TAP), s("Dodaj zdjęcie, plik, osobę albo termin.", Mock.MENU)), premium = true),
        ),
    )

    val TIMETABLE = HelpScreen(
        "Plan lekcji", "Lekcje dzieci z dziennika eduVulcan, zastępstwa i zajęcia dodatkowe. Dziennik odświeża się codziennie o 4:00 i 18:00.",
        listOf(
            HelpTopic("Dzień i tydzień", listOf(s("Stuknij „Dzień” albo „Tydzień”.", Mock.TAP, "Tydzień"), s("Przesuń w bok, żeby przejść do innego dnia.", Mock.SWIPE))),
            HelpTopic("Inne dziecko", listOf(s("Stuknij imię dziecka na górze.", Mock.TAP, "Filip"))),
            HelpTopic("Szczegóły lekcji", listOf(s("Stuknij lekcję: sala, nauczyciel, zastępstwo.", Mock.TAP))),
            HelpTopic("Zajęcia dodatkowe", listOf(s("Stuknij „+ Zajęcia dodatkowe”.", Mock.TAP, "+ Zajęcia"), s("Wpisz nazwę, dni, godziny i miejsce.", Mock.TYPE, "Basen")),
                who = "Dodają: Owner i role z uprawnieniem „Zajęcia dodatkowe”."),
            HelpTopic("Połączenie z dziennikiem", listOf(s("Stuknij trybik → „Połącz z eduVulcan”.", Mock.TAP, "Połącz"), s("Zaloguj się w eduVulcan w trzech krokach.", Mock.TYPE)),
                who = "Łączy: Owner albo Admin."),
        ),
    )

    val EXTRAS = HelpScreen(
        "Zajęcia dodatkowe", "Basen, szachy, angielski — stałe zajęcia po szkole. Widać je w planie i na Pulpicie.",
        listOf(
            HelpTopic("Dodawanie zajęć", listOf(s("Wpisz nazwę i miejsce.", Mock.TYPE, "Basen"), s("Zaznacz dzieci, dni i godziny.", Mock.CHECK), s("Stuknij „Zapisz”.", Mock.TAP, "Zapisz"))),
            HelpTopic("Zmiana i usuwanie", listOf(s("Stuknij zajęcia na liście.", Mock.TAP), s("Zmień i zapisz albo stuknij „Usuń”.", Mock.TAP, "Usuń"))),
        ),
    )

    val DIARY = HelpScreen(
        "Ustawienia planu", "Połączenie kręgu z dziennikiem eduVulcan i to, co plan pokazuje.",
        listOf(
            HelpTopic("Łączenie z dziennikiem", listOf(
                s("Stuknij „Połącz z eduVulcan”.", Mock.TAP, "Połącz"),
                s("Zaloguj się na stronie eduVulcan.", Mock.TYPE, "rodzic@…"),
                s("Zaznacz dzieci, których plan ma się pojawić.", Mock.CHECK),
            ), who = "Owner albo Admin."),
        ),
    )

    val SETTINGS = HelpScreen(
        "Ustawienia", "Trzy części: Aplikacja (z wyglądem), Konto i Kręgi.",
        listOf(
            HelpTopic("Aplikacja", listOf(s("Stuknij strzałkę przy „Aplikacja”: motyw, animacje, aktualizacje.", Mock.TAP, "›"))),
            HelpTopic("Konto", listOf(s("Stuknij strzałkę przy „Konto”: dane, hasło, zdjęcie, telefony.", Mock.TAP, "›"))),
            HelpTopic("Kręgi", listOf(s("Stuknij strzałkę przy „Kręgi”: ludzie, moduły, uprawnienia.", Mock.TAP, "›"))),
        ),
    )

    val APP = HelpScreen(
        "Aplikacja", "Ustawienia tego telefonu: wygląd, animacje, połączenie z serwerem i wersja.",
        listOf(
            HelpTopic("Motyw", listOf(s("Stuknij „Mój motyw”.", Mock.TAP, "Mój motyw"), s("Wybierz motyw — cała aplikacja zmieni się od razu.", Mock.MENU))),
            HelpTopic("Animacje kart", listOf(s("Przełącz „Animacje kart”, żeby karty stały nieruchomo.", Mock.TOGGLE))),
            HelpTopic("Aktualizacja", listOf(s("Stuknij „Sprawdź”, żeby poszukać nowej wersji.", Mock.TAP, "Sprawdź"))),
            HelpTopic("Zgłaszanie błędu", listOf(
                s("Stuknij „Zgłoś błąd” w karcie „Błędy”.", Mock.TAP, "Zgłoś błąd"),
                s("Opisz, co się stało, i stuknij „Wyślij”.", Mock.TAP, "Wyślij"),
            ), intro = "Najprościej z „?” na ekranie, którego dotyczy błąd — wtedy dołączy się zrzut tego ekranu."),
            HelpTopic("Moje zgłoszenia", listOf(
                s("Stuknij „Moje zgłoszenia”, żeby zobaczyć stan i odpowiedzi.", Mock.TAP, "Moje zgłoszenia"),
            ), intro = "Niebieskie kółko z „!” znaczy, że coś naprawiliśmy albo odpowiedzieliśmy."),
        ),
    )

    val REPORT = HelpScreen(
        "Zgłoś błąd", "Napisz, co nie działa. Zgłoszenie trafi do zespołu AMBiFy razem ze zrzutem ekranu.",
        listOf(
            HelpTopic("Opis", listOf(
                s("Napisz, co robiłeś i co poszło nie tak.", Mock.TYPE, "Dodałem zdjęcie…"),
                s("Stuknij „Wyślij”.", Mock.TAP, "Wyślij"),
            )),
            HelpTopic("Zrzut ekranu", listOf(
                s("Stuknij krzyżyk na zrzucie, jeśli nie chcesz go wysyłać.", Mock.TAP, "×"),
                s("Stuknij „Z galerii”, żeby dodać zdjęcie — razem najwyżej trzy.", Mock.TAP, "Z galerii"),
            ), intro = "Zrzut robi się sam, gdy stukasz „?” — pokazuje ekran pod pomocą."),
            HelpTopic("Co dołączamy", listOf(
                s("Stuknij „Co dołączymy”, żeby zobaczyć wersję, telefon i ekran.", Mock.TAP, "Co dołączymy"),
            )),
            HelpTopic("Bez internetu", listOf(
                s("Stuknij „Wyślij” jak zwykle — zgłoszenie poczeka w telefonie.", Mock.TAP, "Wyślij"),
            ), intro = "Wyśle się samo, gdy wróci sieć. Do tego czasu ma stan „Czeka na wysłanie”."),
        ),
    )

    val MY_REPORTS = HelpScreen(
        "Moje zgłoszenia", "Twoje zgłoszone błędy i co się z nimi dzieje. Najnowsze są na górze.",
        listOf(
            HelpTopic("Stany", listOf(
                s("Sprawdź znaczek przy zgłoszeniu.", Mock.TAP, "Przyjęte"),
            ), intro = "Wysłane — czeka na przejrzenie. Przyjęte — wiemy, o co chodzi. W naprawie — będzie w nowej wersji. Naprawione — już działa. Wyjaśnione — to nie był błąd, przeczytaj odpowiedź."),
            HelpTopic("Szczegóły i odpowiedź", listOf(
                s("Stuknij zgłoszenie, żeby zobaczyć opis, zrzut i historię zgłoszenia.", Mock.TAP),
            ), intro = "Odpowiedź zespołu AMBiFy jest na górze. Nie da się na nią odpisać."),
            HelpTopic("Gdy błąd wraca", listOf(
                s("Otwórz ekran, na którym jest błąd, i stuknij „?”.", Mock.TAP, "?"),
                s("Stuknij „Zgłoś błąd” i opisz, co się dzieje.", Mock.TAP, "Zgłoś błąd"),
            ), intro = "Także gdy zgłoszenie jest „Wyjaśnione” albo „Naprawione”, a Ty nadal widzisz błąd — wyślij nowe zgłoszenie."),
            HelpTopic("Znak „!”", listOf(
                s("Zajrzyj tutaj, a znak zniknie.", Mock.TAP),
            ), intro = "Niebieskie kółko z „!” w Ustawieniach znaczy: błąd naprawiony albo czeka odpowiedź."),
            HelpTopic("Dołączone dane", listOf(
                s("W zgłoszeniu stuknij „Dołączone dane”.", Mock.TAP, "Dołączone dane"),
            ), intro = "Wersja aplikacji, telefon, Android i sieć — to, co wysłało się razem z Twoim opisem."),
        ),
    )

    val PLATFORM_REPORTS = HelpScreen(
        "Zgłoszenia", "Wszystkie błędy zgłoszone z aplikacji, z opisem, zrzutem i danymi telefonu.",
        listOf(
            HelpTopic("Przeglądanie", listOf(
                s("Stuknij stan u góry: Wszystkie, Nowe, Przyjęte, W naprawie…", Mock.TAP, "Wszystkie"),
                s("Stuknij zgłoszenie, żeby zobaczyć szczegóły.", Mock.TAP),
            )),
            HelpTopic("Przyjęcie do rejestru", listOf(
                s("Stuknij „Przyjmij do rejestru” i sprawdź numer B-.", Mock.TAP, "Przyjmij"),
            )),
            HelpTopic("Naprawa", listOf(
                s("Stuknij „W naprawie” i wpisz wersję z naprawą.", Mock.TYPE, "0.7.9"),
            ), intro = "Po wydaniu tej wersji stan zmieni się sam na „Naprawione”."),
            HelpTopic("To nie błąd", listOf(
                s("Stuknij „To nie błąd” i wybierz powód.", Mock.TAP, "Tak ma działać"),
                s("Dopisz wyjaśnienie i stuknij „Wyślij wyjaśnienie”.", Mock.TAP, "Wyślij"),
            ), intro = "Bez wyjaśnienia nie da się wysłać — zgłaszający zawsze wie dlaczego."),
        ),
    )

    val ACCOUNT = HelpScreen(
        "Konto", "Twoje dane, hasło i zdjęcie — wspólne dla wszystkich kręgów — oraz zalogowane telefony.",
        listOf(
            HelpTopic("Dane i hasło", listOf(s("Stuknij „Zmień dane” albo „Zmień hasło”.", Mock.TAP, "Zmień dane"))),
            HelpTopic("Zdjęcie profilowe", listOf(
                s("Stuknij „Zrób zdjęcie” albo „Wybierz z galerii”.", Mock.TAP, "Z galerii"),
                s("Przesuń i powiększ zdjęcie w kółku, stuknij „Zapisz”.", Mock.DRAG),
            )),
            HelpTopic("Zalogowane telefony", listOf(s("Stuknij „Wyloguj” przy telefonie, którego już nie używasz.", Mock.TAP, "Wyloguj"))),
        ),
    )

    val CIRCLES = HelpScreen(
        "Kręgi", "Kręgi, do których należysz: rodzina, klasa, sąsiedzi. W każdym masz swoją rolę.",
        listOf(
            HelpTopic("Ustawienia kręgu", listOf(s("Stuknij krąg na liście.", Mock.TAP, "Pacakowie"), s("Wybierz: członkowie, zaproszenia, moduły, uprawnienia, wygląd.", Mock.MENU))),
            HelpTopic("Nowy krąg i dołączanie", listOf(s("Stuknij „Nowy krąg” albo „Dołącz do kręgu”.", Mock.TAP, "Nowy krąg"), s("Wpisz nazwę albo kod zaproszenia.", Mock.TYPE, "AB12CD"))),
            HelpTopic("Awatar kręgu", listOf(s("Stuknij krąg → „Awatar kręgu”.", Mock.MENU), s("Wybierz zdjęcie z galerii albo kolor liter.", Mock.TAP, "Z galerii")), who = "Tylko Owner."),
            HelpTopic("Opuszczanie kręgu", listOf(s("Stuknij krąg → „Opuść krąg”.", Mock.MENU)), who = "Owner najpierw przekazuje rolę komuś innemu."),
        ),
    )

    val PEOPLE = HelpScreen(
        "Członkowie", "Osoby w kręgu i ich role. Tu też zapraszasz nowe osoby.",
        listOf(
            HelpTopic("Zapraszanie", listOf(s("Stuknij „Utwórz kod”.", Mock.TAP, "Utwórz kod"), s("Wyślij kod — działa raz, przez 7 dni.", Mock.TAP, "Wyślij")), who = "Owner i role z uprawnieniem „Zapraszanie osób”."),
            HelpTopic("Rola i usuwanie osoby", listOf(s("Stuknij ⋮ przy osobie.", Mock.TAP, "⋮"), s("Wybierz rolę, „Resetuj hasło” albo „Usuń z kręgu”.", Mock.MENU)), who = "Hasło zresetujesz tylko osobie, która nie należy do innych kręgów."),
            HelpTopic("Zdjęcie osoby", listOf(s("Stuknij awatar, żeby go powiększyć.", Mock.TAP), s("Stuknij „Ukryj zdjęcie w tym kręgu”, jeśli trzeba.", Mock.TAP, "Ukryj")), who = "Ukrywa: rola z uprawnieniem „Zdjęcia profilowe”."),
        ),
    )

    val MODULES = HelpScreen(
        "Moduły", "Co jest na pasku u wszystkich w kręgu. Moduły basic są w cenie, premium kupuje się osobno.",
        listOf(
            HelpTopic("Włączanie i wyłączanie", listOf(s("Przełącz moduł — zniknie albo pojawi się na pasku u wszystkich.", Mock.TOGGLE)), who = "Owner i role z uprawnieniem „Włączanie modułów”."),
            HelpTopic("Dodatki premium", listOf(s("Stuknij link pod modułem, żeby zobaczyć dodatki.", Mock.TAP, "Dodatki")), premium = true),
        ),
    )

    val PERMISSIONS = HelpScreen(
        "Role i uprawnienia", "Kto w kręgu co może. Owner może wszystko i ustala uprawnienia pozostałych ról.",
        listOf(
            HelpTopic("Zmiana uprawnienia", listOf(s("Wybierz zakładkę: „Krąg” albo moduł.", Mock.TAP, "Listy"), s("Stuknij ustawienie przy roli i wybierz: może, nie może, za zgodą.", Mock.MENU)), who = "Tylko Owner."),
            HelpTopic("Nowa rola", listOf(s("Stuknij „+ Dodaj rolę” i wpisz nazwę.", Mock.TAP, "+ Dodaj rolę"))),
            HelpTopic("Za zgodą", listOf(s("Ustaw „za zgodą”: prośba trafia do „Prośby do zatwierdzenia”.", Mock.MENU))),
        ),
    )

    val REQUESTS = HelpScreen(
        "Prośby do zatwierdzenia", "Rzeczy, które ktoś chce zrobić „za zgodą” — czekają na Twoją decyzję.",
        listOf(HelpTopic("Decyzja", listOf(s("Stuknij „Zgoda” albo „Odmowa” przy prośbie.", Mock.TAP, "Zgoda")))),
    )

    val LOOK = HelpScreen(
        "Wygląd", "Twój motyw w całej aplikacji. Motyw przynosi też swoje pismo i efekt dotknięcia.",
        listOf(
            HelpTopic("Mój motyw", listOf(s("Stuknij kafelek motywu — zmiana jest od razu.", Mock.TAP), s("Zaznacz „Używaj motywu kręgu”, żeby wrócić do motywu kręgu.", Mock.CHECK))),
            HelpTopic("Efekt dotknięcia", listOf(s("Stuknij kartę: Luxury fale, Perła iskry, Neon laser, Kod znaki, Gaming trafienie.", Mock.TAP))),
            HelpTopic("Własne motywy", listOf(s("Stuknij „+ Nowy motyw”.", Mock.TAP, "+ Nowy motyw"), s("Ułóż kolory, tło, styl i tapetę, stuknij „Zapisz motyw”.", Mock.MENU)), premium = true),
            HelpTopic("Inny motyw w jednym kręgu", listOf(s("Otwórz Kręgi → krąg → „Wygląd”.", Mock.MENU))),
        ),
    )

    val CIRCLE_LOOK = HelpScreen(
        "Wygląd kręgu", "Motyw tego kręgu i Twój wybór w nim. Owner może ustawić motyw dla wszystkich i go zablokować.",
        listOf(
            HelpTopic("Mój motyw w tym kręgu", listOf(s("Stuknij kafelek motywu.", Mock.TAP))),
            HelpTopic("Motyw kręgu i blokada", listOf(s("Wybierz motyw kręgu.", Mock.TAP), s("Przełącz „Zablokuj”, żeby wszyscy widzieli ten sam.", Mock.TOGGLE)), who = "Owner i role z uprawnieniem „Motyw kręgu”."),
            HelpTopic("Motywy kręgu z tapetą", listOf(s("Stuknij „+ Nowy motyw kręgu”.", Mock.TAP, "+ Nowy motyw")), premium = true),
        ),
    )

    val THEME_EDITOR = HelpScreen(
        "Edytor motywu", "Własny motyw: kolory, tło, styl, efekt i tapeta. Cała aplikacja od razu pokazuje zmiany.",
        listOf(
            HelpTopic("Kolory", listOf(s("Stuknij pole 1, 2 albo 3.", Mock.TAP, "1 · Akcent"), s("Wybierz kolor z palety pod spodem.", Mock.TAP))),
            HelpTopic("Tło", listOf(s("Wybierz „Ciemne” albo „Jasne”.", Mock.TAP, "Ciemne"), s("Przesuń suwaki odcienia i ilości koloru.", Mock.SWIPE))),
            HelpTopic("Styl i efekt", listOf(s("Wybierz styl obwódek i efekt dotknięcia.", Mock.TAP, "Pasy"))),
            HelpTopic("Tapeta", listOf(s("Wybierz „Wzór z kolorów” albo „Zdjęcie”.", Mock.TAP, "Zdjęcie"), s("Ustaw kadr i przyciemnienie suwakami.", Mock.SWIPE))),
            HelpTopic("Zapisywanie", listOf(s("Stuknij „Zapisz motyw” albo „Anuluj”, żeby wrócić do poprzedniego.", Mock.TAP, "Zapisz motyw"))),
        ),
    )

    val PLATFORM = HelpScreen(
        "Platform Admin", "Wszystkie kręgi na serwerze, ich moduły i przywileje kont.",
        listOf(
            HelpTopic("Szukanie kręgu", listOf(s("Wpisz nazwę kręgu albo Ownera — lista zawęża się po każdej literze.", Mock.TYPE, "Pac"))),
            HelpTopic("Moduły i Premium kręgu", listOf(s("Stuknij strzałkę przy kręgu.", Mock.TAP, "›"), s("Przełącz moduł albo „Premium kręgu: motywy”.", Mock.TOGGLE))),
            HelpTopic("Premium konta i darmowy krąg", listOf(
                s("Wpisz e-mail albo imię i stuknij podpowiedź.", Mock.TYPE, "ula"),
                s("Stuknij „Przyznaj”, „Nadaj” albo „Odbierz”.", Mock.TAP, "Przyznaj"),
            )),
            HelpTopic("Kod resetu dla Ownera", listOf(s("Wpisz e-mail Ownera i stuknij „Utwórz kod”.", Mock.TAP, "Utwórz kod"))),
            HelpTopic("Zgłoszenia błędów", listOf(s("Stuknij strzałkę w karcie „Zgłoszenia błędów”.", Mock.TAP, "›"))),
        ),
    )
}

# Rejestr błędów AMBiFy

Zgłoszenia Michała z dosłownym tekstem zgłoszenia i analizą przyczyny. Każdy otwarty błąd wchodzi do najbliższego wydania.
Po naprawie: status „naprawiony w X.Y.Z”, wpis zostaje (historia).

Statusy: **otwarty** → **w TEST** (naprawa na AMBiFy TEST) → **naprawiony w X.Y.Z** (na PROD).

---

## B-001 · Plan lekcji na ½ szerokości: dziecko wychodzi poza widget
- **Zgłoszenie (oryginał):**
  > widget plan lekcji (pól szerokości)- jeśli >1 dziecko to przełaczanie dzieci musi być możliwe a obecnie dziecko wychodzi poza widget.
- **Zgłoszony:** 25.11.2026 (po 0.7.7)
- **Status:** naprawiony w 0.7.8 (PROD 26.11.2026)
- **Co się dzieje:** przy >1 dziecku przełącznik dzieci (chipy z awatarem i imieniem) jest szerszy niż połówka widgetu; drugie dziecko wystaje poza kartę.
- **Przyczyna:** `PersonSwitch` w `app/.../features/home/LessonsWidget.kt` układa wszystkie dzieci w jednym rzędzie (przewijanym w bok), z imionami przy ≤2 dzieciach — nie sprawdza szerokości widgetu.
- **Naprawa (uzgodniona na kanwie, plansza 4):** na ½ szerokości strzałki „‹ dziecko ›” jak przy miejscach w Pogodzie (+ przesunięcie palcem), imię i nazwisko w dwóch liniach.

## B-002 · Pogoda: Średnia prawie nie różni się od Dużej
- **Zgłoszenie (oryginał):**
  > Widget pogoda - pomiędzy średnią a duża opcją praktycznie nie ma różnic poza 1 pozycją, która nie zmienia rozmiaru. Po co nam w takim razie średnia?
  > Moze dać na stałe jutro a w średniej usunąć tnapisy pod deszcz ?
  > Albo pozbywamy się średniej - ale wolałbym zobaczyć najpierw jak będzie wygladać bez tych napisów - kanwa najpierw.
- **Zgłoszony:** 25.11.2026
- **Status:** naprawiony w 0.7.8 (PROD 26.11.2026)
- **Co się dzieje:** Duża ma tylko jedną kolumnę więcej („Jutro”) przy tej samej wysokości.
- **Przyczyna:** `WeatherWidget.kt` — różnica rozmiarów to tylko `if (widget.height == LARGE)` dla kolumny „Jutro”.
- **Naprawa (kanwa, plansza 2):** Średnia: 4 godziny + Jutro, bez linii „odczuwalna · pochmurno” i bez % pod godzinami (do porównania układ z temperaturą na środku). Duża: + odczuwalna, wilgotność, ciśnienie, wschód/zachód, % godzinowo, 3 kolejne dni. Kolorowe ikony. Stuknięcie → prognoza 16 dni (plansza 5).

## B-003 · Platform Admin: po przyznaniu e-mail zostaje w polu, brak potwierdzenia
- **Zgłoszenie (oryginał):**
  > Po dodaniu wyglądu premium dla użytkownika jego email powinien zniknąć z pola adresu i powinno pojawiać się potwierdzenie.
- **Zgłoszony:** 25.11.2026
- **Status:** naprawiony w 0.7.8 (PROD 26.11.2026)
- **Co się dzieje:** po „Przyznaj” pole e-mail się nie czyści, a komunikat pojawia się tylko na górze ekranu (poza widokiem przy przewiniętej liście).
- **Przyczyna:** `EmailAction` w `app/.../features/platform/PlatformScreen.kt` nie czyści swojego stanu po sukcesie; `notice` jest wspólny dla całego ekranu i nie znika po wyjściu.
- **Naprawa (kanwa, plansza 4):** komunikat sukcesu w karcie, pole czyszczone; przy błędzie pole zostaje. Komunikat znika po kolejnym wpisie i po wyjściu z ekranu. Jedna karta Premium z Przyznaj/Odbierz, podpowiedź pokazuje „ma / bez Premium” (to samo dla darmowej społeczności).

## B-004 · Wygląd: kafelek „Jak w społeczności” myli się z motywem Luxury
- **Zgłoszenie (oryginał):**
  > Motyw "Jak w społecznmości" - to jest mylkące szczególnie skoro ma ikonę jak motyw luxury. Ja bym to dał chyba jako checkbox z tekstem: "Używaj motywu danej społeczności" (default) a jak ktoś  wybiuerze inny to tutaj pteszek z aautomatu znika.
- **Zgłoszony:** 26.11.2026
- **Status:** naprawiony w 0.7.8 (PROD 26.11.2026)
- **Co się dzieje:** w Ustawienia → Wygląd → Mój motyw pierwszy kafelek „Jak w społeczności” wygląda jak zwykły motyw (w Społeczności testowej/Pacakach — dokładnie jak Luxury), więc nie wiadomo, że to „brak własnego wyboru”.
- **Przyczyna:** `AccountThemeScreen` w `app/.../features/settings/ThemeScreens.kt` (ok. l. 168–173) wstawia „Jak w społeczności” jako `TileItem` na równi z motywami i maluje go paletą motywu bieżącej społeczności (`session.paletteOf(state, it.theme)`, domyślnie `Palettes.LUXURY`).
- **Naprawa (propozycja Michała):** zamiast kafelka — pole wyboru „Używaj motywu danej społeczności” nad kafelkami, domyślnie zaznaczone (`accountTheme == null`). Wybór dowolnego motywu z kafelków sam odznacza pole (ustawia motyw konta); zaznaczenie pola z powrotem = `setAccountTheme(null)`. Gdy pole zaznaczone, żaden kafelek nie jest wyróżniony. Słowo „społeczność” wg decyzji o nowej nazwie. Zmiana wyglądu → najpierw dorysować na kanwie. Pomoc ekranu Wygląd uwzględnia pole.

## B-005 · Edytor motywu: po „Anuluj” tapeta znika z wyraźnym opóźnieniem
- **Zgłoszenie (oryginał):**
  > przy tworzeniu nowego motywu, jeśli dodam tapetę ale finalnie stuknę anuluj to tapeta znika po widocznie dłuższym czasie. Chciałbym żeby od tegop zaczynało się przywracanie starych ustawien bo to najbardziej widoczne,
- **Zgłoszony:** 26.11.2026
- **Status:** naprawiony w 0.7.8 (PROD 26.11.2026)
- **Co się dzieje:** po „Anuluj” (i po strzałce wstecz) ekran wraca, ale tapeta z podglądu i kolory edytowanego motywu zostają jeszcze przez ok. 0,7 s.
- **Przyczyna:** podgląd na żywo jest wyłączany w `DisposableEffect { onDispose { session.previewTheme(null) } }` w `ThemeEditorScreen` (`app/.../features/settings/ThemeScreens.kt`). `onDispose` wykonuje się dopiero, gdy ekran edytora całkiem zniknie — po domyślnej animacji przejścia `NavHost` (zanikanie ok. 700 ms, `AppNavHost.kt` nie ustawia własnych przejść). Do tego czasu `Luxury.palette` (z tapetą, którą rysuje `WallpaperLayer`) to wciąż edytowany motyw.
- **Naprawa:** wywołać `session.previewTheme(null)` od razu w chwili wyjścia — w „Anuluj”, strzałce wstecz, `BackHandler` i przy stuknięciu paska modułów — zanim ruszy nawigacja; `onDispose` zostaje jako zabezpieczenie. Kolejność przywracania wg Michała: najpierw tapeta (najbardziej widoczna), potem kolory — tzn. `WallpaperLayer` ma od razu brać tapetę z prawdziwego motywu (bez przejścia), a reszta palety może zmienić się razem z przejściem ekranu. Po „Zapisz motyw” bez zmian (motyw zostaje).

## B-006 · Ekran nowej osoby bez kręgu nie jest w stylu Luxury
- **Zgłoszenie (oryginał):**
  > ekran dla noweo usera (bez kręgu jest beznadziejny) powinien być w stylu Luxury a jest jakiś taki nijaki,
- **Zgłoszony:** 26.11.2026 (po 0.7.8)
- **Status:** w TEST (0.7.91–0.7.92, 26.11.2026) · plan: 0.7.9 — nowa oprawa także logowania, rejestracji, dołączenia z kodem i nowego hasła (kanwa 0.7.9, plansze 5–6)
- **Co się dzieje:** ekran „Witaj, …! Nie należysz jeszcze do żadnego kręgu” (wybór „Załóż krąg” / „Dołącz do kręgu”) wygląda jak ekran logowania: czarne tło, zwykłe przyciski Material, pola bez złotych kart.
- **Przyczyna:** `StartScreen` w `app/.../features/auth/AuthScreens.kt` używa `AuthLayout` (stałe czarne tło `Color.Black`, bez `LuxuryCard`, bez fal/efektów) i `StartChoice` (Material `Button`/`OutlinedButton` z zaokrągleniem 20 dp i kolorami `AuthColors`, w tym niezwiązane z motywem `Tonal #3B4258`). Ekran powstał razem z logowaniem, przed złotymi kartami.
- **Proponowana naprawa:** przerobić na styl aplikacji — tło motywu, dwie `LuxuryCard` („Załóż krąg” z polem nazwy i złotym przyciskiem, „Dołącz do kręgu” z polem kodu), nagłówek pismem motywu, logo AMBiFy; kolory z palety zamiast `AuthColors.Tonal`. Przy okazji ujednolicić rejestrację i reset hasła. Zmiana wyglądu → najpierw makieta na kanwie do akceptacji.

## B-007 · Filipa (kids, Realme) wylogowało
- **Zgłoszenie (oryginał):**
  > Filip mówi, że go wylogowało - możesz to sprawdzic ?
- **Zgłoszony:** 26.11.2026 (PROD 0.7.8)
- **Status:** w TEST (0.7.91–0.7.92, 26.11.2026) · plan: 0.7.9 — zrobione (1) serwer: poprzedni token działa, dopóki telefon nie użyje nowego (najdłużej 7 dni), V017; (2) `commit()` zamiast `apply()`; (3) wpis „wylogowanie: …” w logu kids. Do zrobienia przez Michała: (4) bateria AMBiFy na Realme Filipa „Bez ograniczeń”.
- **Co się dzieje (logi kids, `/var/lib/ambify-monitor`, telefon RMX3493):** ostatnia udana wysyłka 26.11 07:55. Potem przez 7,5 h zero wysyłek i zero wpisów „wysyłka nieudana” (choć telefon był używany 13:44–15:11). 15:22:01 Filip otwiera AMBiFy (jeszcze 0.7.7) → 15:22:03 „wysyłka nieudana: ServerError: Zaloguj się ponownie.” → serwer odrzucił też odświeżenie sesji → aplikacja wylogowała. 15:22:46 samoaktualizacja do 0.7.8 (nie ona wylogowała — była 40 s później). Wdrożenie serwera 0.7.8 o 9:27 nie ruszało sesji (inni nie zostali wylogowani).
- **Przyczyna (najbardziej prawdopodobna):** jednorazowe tokeny odświeżania. Każde odświeżenie (co ≥1 h, token dostępu żyje 60 min) daje nowy token odświeżania, a stary od razu przestaje działać (`AccountService.refresh`). Jeśli serwer zmienił token, ale telefon nie zapisał nowego — odpowiedź zgubiona przy przełączaniu Wi-Fi/LTE albo słabym zasięgu (limit 6 s), albo Android (Realme agresywnie ubija procesy) zabił aplikację zanim `SharedPreferences.apply()` zapisało token na dysk — przy następnym odświeżeniu telefon wysyła stary token → 401 → wylogowanie. Inne możliwe (nie do sprawdzenia bez dostępu do bazy PROD): ktoś wylogował Filipa w „Członkowie”/„Urządzenia”, reset hasła Filipa, logowanie na jego konto z telefonu o tej samej nazwie.
- **Poboczne:** między 7:55 a 15:22 nie ruszyła ani jedna godzinna wysyłka w tle (brak nawet wpisu o błędzie) — WorkManager na Realme prawdopodobnie wstrzymany przez oszczędzanie baterii.
- **Proponowana naprawa:** (1) serwer: okres łaski — poprzedni token odświeżania działa jeszcze np. 2 min / do pierwszego użycia nowego (zapamiętać `prevTokenHash`); (2) aplikacja: zapis tokenów przez `commit()` (synchronicznie) zamiast `apply()`; (3) log w kids przy wylogowaniu z kodem błędu serwera (`session_expired` vs `unauthorized`), żeby następnym razem było wiadomo; (4) sprawdzić na Realme ustawienia baterii dla AMBiFy („Bez ograniczeń”).

## B-008 · Panel Platformy: plakietka „3 nowych” łamie się na litery
- **Zgłoszenie (oryginał):**
  > mam 3 nowych zgłoszęń błedów 3 nowych się rozjeżdża - zobacz screen
  >
  > na s24
- **Zgłoszony:** 26.11.2026 (TEST 0.7.92, S24, konto Platform Admin)
- **Status:** w TEST 0.7.94 — sprawdzone przez Michała 27.11 („jest ok”) · plan: 0.7.9
- **Co się dzieje:** na karcie „Zgłoszenia błędów” w panelu Platform Admin plakietka obok tytułu łamie się w trzy linie: „3 / nowyc / h”. Przez nią karta jest wysoka, a wiersz „Nowe: 3 · W naprawie: 0 · Wszystkie: 8” też zawija się w dwie linie. Przy okazji zła odmiana: powinno być „3 nowe” (2–4 → „nowe”, 5+ → „nowych”, 22–24 → „nowe”).
- **Przyczyna:** `PlatformReportsCard` w `app/.../features/platform/PlatformReports.kt:86-88`. Tytuł `LuxurySectionTitle("Zgłoszenia błędów")` (wersaliki z dużym odstępem liter) i plakietka `MessageChip` siedzą w jednym `Row` w kolumnie o `weight(1f)`, obok przycisku „>”. Tytuł zabiera prawie całą szerokość, a `MessageChip` nie ma `softWrap = false`/`maxLines = 1`, więc dostaje resztkę miejsca i zawija tekst po literach. Na S10+ (węższy font/skala) tego nie było widać. Odmiana: `if (fresh == 1) "nowe" else "nowych"` nie zna reguły 2–4.
- **Proponowana naprawa:** plakietkę przenieść pod tytuł (do wiersza ze statystykami albo osobno nad nim) i dać jej `maxLines = 1`. Statystyki skrócić do „3 nowe · 0 w naprawie · 8 razem”. Odmianę liczby wziąć z jednej funkcji (np. `plural(n, "nowe", "nowe", "nowych")`) — sprawdzić, czy taka już jest w projekcie. Drobna zmiana układu tej samej karty, zgodna z kanwą (plansza 4) — pokazać zrzut przed PROD.
- **Zrzut:** `docs/images/bledy/B-008.png`

## B-009 · „Moje zgłoszenia”: brak danych telefonu i Androida
- **Zgłoszenie (oryginał):**
  > Tam gdzie sa moje zgloszenia nie ma informacji o tel, androidzie ito
- **Zgłoszony:** 26.11.2026 (TEST 0.7.92, S24)
- **Status:** w TEST 0.7.96 — sprawdzone przez Michała 27.11 („Ok”) · plan: 0.7.9 — plansza 8; `MyReport` + appVersion/appCode/phone/android/network, zwinięta karta „Dołączone dane”, temat w pomocy MY_REPORTS
- **Co się dzieje:** szczegóły zgłoszenia w Ustawienia → Aplikacja → Błędy → Moje zgłoszenia pokazują tekst, ekran, zrzuty, stan i historię, ale nie pokazują tego, co aplikacja dołączyła: wersji aplikacji, telefonu, Androida i sieci. Okno zgłoszenia obiecuje „Co dołączymy (wersja, telefon, ekran)”, a potem użytkownik tego nigdzie nie widzi. Panel Platform Admin te dane pokazuje.
- **Przyczyna:** to luka w projekcie, nie w kodzie. Plansza 3 kanwy 0.7.9 (`Moje.dc.html`) nie miała tych danych, a kod zrobiono według kanwy. Serwer zapisuje je (`bug_reports`: app_version, phone, android, network), ale `MyReport` w `shared/.../Reports.kt:67` nie ma tych pól. `ReportService.mine` ich nie wysyła, a `MyReportScreen` (`app/.../features/reports/ReportScreens.kt:235`) nie ma ich czym pokazać.
- **Proponowana naprawa:**
  - dopisać do `MyReport` pola `appVersion`, `phone`, `android` i `network` z wartościami domyślnymi (starsze aplikacje je pominą); wypełnić je w `ReportService.mine`;
  - w `MyReportScreen` dodać zwiniętą sekcję „Dołączone dane” (wersja · telefon · Android · sieć), taką samą jak „Co dołączymy” w oknie zgłoszenia;
  - wersja kids: pole `kids` nadal NIE trafia do `MyReport`; test `aReportSentTwice…NeverSeesKids` musi nadal przechodzić;
  - zgłoszenie czekające na wysłanie pokazać z danych w telefonie (`NewReport` ma te pola);
  - zmiana wyglądu: najpierw dopisać ją do planszy 3 na kanwie do akceptacji i zaktualizować pomoc „?” (MY_REPORTS).

## B-010 · Pulpit: nazwa kręgu w nagłówku łamie się w środku słowa
- **Zgłoszenie (oryginał):** Claude zauważył to na zrzutach ze zgłoszeń TEST i zapytał, czy zapisać. Michał odpowiedział:
  > rodzaj moze ten sama ale inne miejsce wiec oddzielnie je rozwiazujemy
- **Zgłoszony:** 26.11.2026 (TEST 0.7.92, S24; zrzuty ze zgłoszeń Uli 23:02 i Pata 22:55)
- **Status:** w TEST 0.7.95 — sprawdzone przez Michała 27.11 („działa”) · plan: 0.7.9
- **0.7.94 — nie działa (Michał, 27.11, zgłoszenie z S10+ „b010” ze zrzutem `docs/images/bledy/B-010.jpg`):** „Druga społeczność test…” nadal ucięte przy 28 sp. Przyczyna: pomiar szerokości (`rememberTextMeasurer`) nie znał odstępów między literami z motywu (`LocalTextStyle`, 0,5 sp), więc liczył nazwę jako węższą, niż jest naprawdę, i uznawał, że się mieści.
- **0.7.95:** pomiar tym samym stylem co tekst (`LocalTextStyle.current.merge(…)`) + zabezpieczenie: gdy prawdziwy układ wciąż się nie mieści (`onTextLayout` → `hasVisualOverflow`), czcionka maleje o 1 sp, do 20 sp.
- **Co się dzieje:** nazwa kręgu na górze Pulpitu jest pisana dużą czcionką. Na S24 „Społeczność testowa” łamie się w środku słowa na „Społeczno / ść testowa”. „Druga społeczność testowa” skraca się do „Druga / społeczno…”, więc nie widać końca nazwy. Prawdziwe kręgi z długim pojedynczym słowem (np. „Pacakowie-Kowalscy”) będą wyglądać tak samo.
- **Przyczyna:** `HomeHeader` w `app/.../features/home/HomeHeader.kt:79-83`. Tytuł ma stałe 28 sp (`capped(28f)`) i `maxLines = 2`. Siedzi w wąskiej kolumnie między odznaką kręgu (60 dp), strzałką zmiany kręgu i przyciskiem „?”. Gdy jedno słowo jest szersze niż kolumna, Compose tnie je w dowolnym miejscu (bez dzielenia wyrazów), a trzecia linia zamienia się w „…”. Na S10+ czcionka systemowa jest mniejsza, więc tego nie było widać. To ten sam rodzaj problemu co B-008, ale w innym miejscu, więc naprawiamy go osobno.
- **Proponowana naprawa:**
  - tytuł zmniejsza się sam, aż najdłuższe słowo zmieści się w jednej linii (np. 28 → 20 sp, krokami), a dopiero potem łamie się między słowami; maks. 2 linie;
  - poniżej 20 sp zostaje „…” na końcu, a pełną nazwę widać po stuknięciu (przełącznik kręgów);
  - sprawdzić na S24 i S10+ z nazwami: „Społeczność testowa”, „Druga społeczność testowa”, „Pacakowie”.
  - Wygląd się nie zmienia (ta sama czcionka i układ), więc nie trzeba kanwy; przed PROD pokazać zrzut.
- **Zrzuty:** `~/.local/share/ambify-test/reports/3a818c40-512f-4e5a-935a-04ca88df6ec2/0.jpg`, `…/60febd9d-00cb-4129-8aaa-c5ae9c4163e6/0.jpg` (serwer TEST)

## B-011 · Złota kropka przy „Moje zgłoszenia” jest prawie niewidoczna
- **Zgłoszenie (oryginał):** z aplikacji TEST (Ula User, S24, ekran „Aplikacja”, 26.11 23:09, ze zrzutem). W panelu TEST ma numer B-104. Treść:
  > kropka przy moj3 zgloszenia jest niebwidoczna. może wykrzyknuk w kółku?

  W czacie dodał: „jest jeszcze bug dotyczaący kropki” / „B-104”.
- **Zgłoszony:** 26.11.2026 (TEST 0.7.92)
- **Status:** w TEST 0.7.97 (27.11.2026, po poprawce odświeżania) · plan: 0.7.9 — wariant A z planszy 9: niebieskie (kolor informacji) kółko 14 dp z „!”; STANDARD dla całej aplikacji: „masz tu coś do przeczytania albo zrobienia”
- **Co się dzieje:** przy „Moje zgłoszenia” (i wyżej, przy „Aplikacja” w Ustawieniach) jest kropka 8 dp w kolorze złota, tuż obok złotej liczby „2” i „›”. Na ciemnym tle ginie wśród innych złotych elementów i nie wygląda jak sygnał „masz odpowiedź”.
- **Przyczyna:** `NewsDot` w `app/.../features/reports/ReportScreens.kt:306` to 8 dp `Luxury.GoldBase`, czyli ten sam kolor co tekst obok. Jest użyta w `SettingsRow` (`SettingsScreens.kt:292`), w karcie hubu Ustawień (`:88`) i w wierszach listy (`ReportRow`). Kanwa (plansza 3) też miała małą kropkę.
- **Proponowana naprawa:** propozycja Michała: wykrzyknik w kółku. Np. kółko 18–20 dp z „!”, wypełnione kolorem innym niż złoto (kolor STAN/UWAGA z kanwy komunikatów), z opisem dla czytnika „Nowa odpowiedź”. Te same miejsca: hub Ustawień → Aplikacja → Moje zgłoszenia → wiersz zgłoszenia. Zmiana wyglądu: najpierw plansza na kanwie (2–3 warianty), zgodna z kanwą komunikatów; potem pomoc „?” (MY_REPORTS, APP), gdzie dziś jest mowa o „kropce”.

- **0.7.96 — nie widać „!” (Michał, 27.11):**
  > B-011 Nie ma żadnego wykrzyknika na koncie admina pomimo updatu jednego z błędów. Chcę testowac bez przełaczania
- **Przyczyna:** wygląd był dobry, ale lista moich zgłoszeń (`ReportsStore`) pobierała się tylko przy starcie aplikacji i na ekranie „Moje zgłoszenia”. Pat (Platform Admin) zmienił w panelu stan własnego zgłoszenia „b010” na „Wyjaśnione”, ale Ustawienia nadal miały starą listę, więc nie było czego oznaczyć.
- **0.7.97:** Ustawienia pobierają moje zgłoszenia przy każdym otwarciu.
## B-012 · Na dole wyjaśnionego zgłoszenia stoi instrukcja zamiast w pomocy
- **Zgłoszenie (oryginał):** z aplikacji TEST (Ula User, S24, ekran „Moje zgłoszenia”, 26.11 23:10, ze zrzutem). W panelu TEST oznaczone „W naprawie 0.7.92”. Treść:
  > Na dole tego ekranu sa jakies instrukcje o ponobum zgloszeniu zamiast w pomocy
- **Zgłoszony:** 26.11.2026 (TEST 0.7.92)
- **Status:** w TEST 0.7.94 — sprawdzone przez Michała 27.11 („jest OK”) · plan: 0.7.9
- **Co się dzieje:** pod „Historią zgłoszenia” w zgłoszeniu ze stanem „Wyjaśnione” jest szary dopisek „Jeśli to jednak błąd, zgłoś go jeszcze raz z „?” na ekranie, którego dotyczy.” To instrukcja obsługi, a według standardu pomocy instrukcje są w „?”, nie na ekranie.
- **Przyczyna:** `MyReportScreen`, `app/.../features/reports/ReportScreens.kt:267-269`.
- **Proponowana naprawa:** usunąć dopisek z ekranu. W pomocy MY_REPORTS (`HelpContent.kt`) dodać zwinięty temat „Gdy błąd wraca” (1–2 kroki: otwórz ekran z błędem → „?” → „Zgłoś błąd”). Bez kanwy (tylko usunięcie tekstu).
- **Przy okazji (TEST):** zgłoszenie oznaczone „W naprawie” z wersją 0.7.92 od razu zmieniło się na „Naprawione w 0.7.92”, bo 0.7.92 jest już wydana. Tak działa zasada, ale w panelu łatwo o pomyłkę. Do rozważenia: w arkuszu „W naprawie” podpowiadać następną wersję (np. 0.7.93 na TEST, 0.8.0 na PROD) i ostrzegać, gdy wpisana wersja jest już wydana.

## B-013 · Owner mógł przejąć konto osoby z innych kręgów przez reset hasła
- **Zgłoszenie (oryginał):** wyszło z rozmowy o resecie hasła online. Michał zapytał:
  > Poza tym jełśi ktoś będzie członkiem 10ciu kręgów to który owner ma mu dac kod ?

  Claude znalazł dziurę i zaproponował łatkę. Michał zdecydował:
  > tak blokujemy opcje resetu dla ownera z możliwościa przejęcia konta
- **Zgłoszony:** 26.11.2026
- **Status:** w TEST (serwer TEST, 26.11.2026) · plan: 0.7.9
- **Co się działo:** każdy Owner/Admin z wyższą rolą w DOWOLNYM kręgu osoby mógł utworzyć kod resetu jej hasła. Kod widzi tylko on, a e-mail osoby zna z listy członków. Mógł więc sam ustawić nowe hasło i zalogować się na jej konto, a tym samym wejść do wszystkich jej kręgów (np. Owner klubu do kręgu rodzinnego).
- **Przyczyna:** `AccountService.createResetCode` (`server/.../accounts/AccountService.kt`) sprawdzał tylko rolę w bieżącym kręgu (`requireManageable`). Hasło należy do konta, nie do kręgu.
- **Naprawa (zrobiona):**
  - **serwer:** kod dla innej osoby tylko wtedy, gdy należy WYŁĄCZNIE do tego kręgu i nie jest Platform Adminem. W przeciwnym razie 409 `other_circles` z tekstem: „Ta osoba należy też do innych kręgów. Nowe hasło może ustawić tylko ona albo zespół AMBiFy.” (aplikacja pokazuje go jak inne błędy na ekranie Członkowie). Kod dla siebie i kod od Platform Admina działają jak dotąd;
  - **test:** `CommunitiesTest.ownerResetsOnlySomeoneWhoBelongsToNoOtherCircle`;
  - **pomoc „?”:** Logowanie → „Zapomniane hasło” i Członkowie → „Rola i usuwanie osoby”.
- **Na później (plan „Odzyskiwanie konta”):** osoba z kilku kręgów, która zapomniała hasła, nie ma dziś sposobu, by z aplikacji poprosić zespół AMBiFy, bo zgłoszenia wymagają zalogowania. Opcje do planu: prośba z ekranu logowania zatwierdzana przez Ownera, kod e-mailem z własnej domeny (wolne: ambif.pl, ambify.com.pl, ambify.eu, ambify.net; ambify.pl zajęta do 16.01.2027), kod awaryjny przy rejestracji.

## B-014 · Widget Lekcje na Pulpicie pokazał wczorajszy plan (Bartek: 8:00 zamiast 8:50)
- **Zgłoszenie (oryginał):**
  > Jeszcze jedna rzecz własnie zauważyłem. AMbifY produkcyjne działało sobie w tle na telefonie. kiedy rano wywołałem aplikację, żeby sprawdzic na która godzinę namają dzieciaki to mieli na 8 rano. Dopiero jak ich obudziłem to Bartek powiedział, że ma na 8:50. Na widgecie jednak nadal pokazywało 8. Dopiero jak przełaczyłem gdziekolwiek (na plan lekcji) to sie zsynchronizowało i pokazało aktualne dane.
  > To spowodowało , że za wcześnie obudziłem Bartka do szkoły.
  > - Jakie są zasady odświeżania danych w planie lekcji ?
  > - Jak możemy zrobic żeby takich sytuuacji uniknąć ?
- **Zgłoszony:** 27.11.2026 (PROD 0.7.8, S24)
- **Status:** w TEST 0.7.93 (27.11.2026) · plan: 0.7.9 — WYSOKI priorytet (realna szkoda: dziecko obudzone za wcześnie).
- **Decyzja Michała (27.11):**
  > ja bym na pewno dodal kolejne pobranienie danych i ewentualne odświeżanie widgetów na 4 w nocy (raczej nikt nie doda zastępstw między 4 a 6 rano). Dodatkowo może to być przy każdym wywołaniu aplikacji.
- **Zrobione:**
  - serwer pobiera dziennik o 4:00 i 18:00 (`DiarySchedule`, test `nextUpdateAtFourAndEighteen`);
  - Pulpit pobiera plan przy każdym powrocie do aplikacji (`ON_RESUME`, nie częściej niż co minutę), przy zmianie dnia i co 15 min;
  - nowy `TimetableRefreshWorker` raz dziennie ok. 4:30 (z siecią) odświeża kopię planu w telefonie dla każdego kręgu z kopią, a wylogowanie go wyłącza;
  - teksty „codziennie o 18:00” → „o 4:00 i 18:00” (pomoc, ekran dziennika, „następne odświeżenie”).
  - Dopisek „stan z …” na widgecie (pkt 3) — jeszcze nie, wymaga kanwy.
- **Co się dzieje:** aplikacja była w tle z otwartym Pulpitem. Rano po powrocie widget „Lekcje” pokazał stary plan (8:00). Po przejściu na „Plan lekcji” i z powrotem dane się odświeżyły (8:50).
- **Przyczyna:** `HomeScreen` (`app/.../features/home/HomeScreen.kt:144-146`) pobiera plan tylko w `LaunchedEffect(communityId, needsTimetable)`, czyli tylko przy tworzeniu ekranu Pulpitu. Gdy Android trzyma aplikację w pamięci, powrót do niej nie tworzy ekranu od nowa, więc plan się nie odświeża. Odświeża się zegar (`now` co 30 s), więc etykiety „DZIŚ/JUTRO” się przesuwają, ale dane są sprzed wielu godzin. Przejście do innego modułu i powrót tworzy Pulpit od nowa, stąd „synchronizacja” po przełączeniu. Widget nie pokazuje też, z której godziny są dane.
- **Zasady dziś:**
  - serwer pobiera plan z dziennika raz dziennie o 18:00 (`DiarySchedule`, wybór Michała) albo po „Odśwież” w ustawieniach dziennika;
  - ekran „Plan lekcji” pobiera dane przy każdym wejściu;
  - Pulpit tylko przy utworzeniu ekranu;
  - bez sieci oba pokazują kopię z telefonu (`TimetableCache`).
- **Proponowana naprawa:**
  1. **Aplikacja (najważniejsze):** Pulpit pobiera plan przy każdym powrocie aplikacji na ekran (`ON_RESUME`), przy zmianie dnia i co ~15 min, gdy jest otwarty. To samo dla innych widgetów z danymi z serwera (pogoda, listy) — sprawdzić.
  2. **Serwer:** częstsze pobieranie z dziennika, np. dodatkowo o 6:00 (zastępstwa wpisywane wieczorem/rano) albo co 2 h w 6–20 — do decyzji Michała.
  3. **Widget:** gdy dane są starsze niż np. 3 h albo z kopii offline, mały dopisek „stan z 18:05” (zmiana wyglądu → kanwa).
  4. **Test:** aplikacja w tle przez noc → rano Pulpit pokazuje nowe dane bez przełączania.

## B-015 · Okno „Zgłoś błąd”: przyciski chowają się pod klawiaturą
- **Zgłoszenie (oryginał, w czacie):**
  > Dodatkowo zauważyłem , że zgłoszenie błędu nie dostosowuje się wielkością do otwartej klawiatury (lub nie ma przypietych przycisków) a miało to być standardem
- **Zgłoszony:** 27.11.2026 (TEST 0.7.94, S10+)
- **Status:** w TEST 0.7.95 — sprawdzone przez Michała 27.11 („działa — taki ma być standard”) · plan: 0.7.9
- **Co się dzieje:** podczas pisania opisu klawiatura zasłania „Anuluj” i „Wyślij”; trzeba ją schować albo przewinąć, żeby wysłać.
- **Przyczyna:** „Zgłoś błąd” to zwykły ekran (`SettingsLayout`), a przyciski były na dole karty, w przewijanej części, pod polem tekstowym, zrzutami i „Co dołączymy”. Standard „przyciski przypięte nad klawiaturą” (`PopupButtons`) był zrobiony tylko dla okien (`LuxuryPopup`), nie dla ekranów z formularzem.
- **Naprawa:** `SettingsLayout` dostał parametr `footer` — przyciski stoją pod przewijaną częścią, zawsze na ekranie i tuż nad klawiaturą (cała nawigacja ma `imePadding`). „Zgłoś błąd” używa go dla „Anuluj”/„Wyślij”.
- **Do sprawdzenia później:** inne ekrany z formularzem na `SettingsLayout` i przyciskiem na dole — przejrzeć i przenieść do `footer`.

## B-016 · „Moje zgłoszenia”: brak oddzielenia między zgłoszeniami
- **Zgłoszenie (oryginał):** z aplikacji TEST (Pat, S10+, ekran „Moje zgłoszenia”, 27.11 11:28, ze zrzutem `docs/images/bledy/B-016.jpg`):
  > brak rozdzielenia pomiędzy zgłoszeniami. może być subtelne ale musi byc
- **Zgłoszony:** 27.11.2026 (TEST 0.7.94)
- **Status:** w TEST 0.7.97 (27.11.2026) · plan: 0.7.9
- **Co się dzieje:** zgłoszenia na liście w jednej karcie stoją jedno pod drugim bez żadnej linii; trudno ocenić, gdzie kończy się jedno, a zaczyna drugie.
- **Przyczyna:** kanwa (plansza 3) miała cienką linię między wierszami (`border-bottom`), a `ReportRow` w kodzie jej nie dostał.
- **Naprawa:** `ReportRow(divider = …)` rysuje `LuxuryDivider` (1 dp, kolor `Luxury.Divider`) nad każdym wierszem poza pierwszym — w „Moje zgłoszenia” i w panelu Platformy → Zgłoszenia.
- **0.7.95 — za subtelne (Michał, 27.11):**
  > jest ale chyba zbyt subtelne jednak - zrób taką samą linię ale podwójnie. Linia nad zgłoszeniem powinna być lekko zaokrąglona w dół na końcach a pod zgłoszeniem zaokrąglona w górę (mały łuk) - bez bocznych linii.
- **0.7.96:** każde zgłoszenie ma linię nad sobą (końce łukiem w dół) i pod sobą (końce łukiem w górę), łuk 7 dp, bez boków; między zgłoszeniami 6 dp przerwy, więc widać podwójną linię. `ReportRow` (`drawBehind`), także w panelu Platformy.
- **0.7.96 — za mały kontrast (Michał, 27.11):**
  > Te linie dają dokładnie taki efekt io jaki mi chodziło ale widoczne są tylkop przy bardzo jasnym ekranie - niue ma prawie w ogóle kontrastu
- **0.7.97:** kształt bez zmian; kolor linii zamiast `Luxury.Divider` (złoto 14 %) → `Luxury.GoldText` 45 %.

## B-017 · Panel Platformy: domyślnie powinien być filtr „Wszystkie”
- **Zgłoszenie (oryginał, w czacie):**
  > Chcę testowac bez przełaczania ale to podało nowy błąd - na platformie admina powinna być opcja sortowania "Wszytkie" ustawiona jako default
- **Zgłoszony:** 27.11.2026 (TEST 0.7.96)
- **Status:** w TEST 0.7.97 (27.11.2026) · plan: 0.7.9
- **Co się dzieje:** lista Platforma → Zgłoszenia otwiera się na filtrze „Nowe”. Nie ma filtru pokazującego wszystkie zgłoszenia naraz, więc po zmianie stanu zgłoszenie znika z widoku i trzeba przełączać filtry.
- **Przyczyna:** `Filter` w `PlatformReports.kt` miał tylko stany (Nowe, Przyjęte, W naprawie, Naprawione, Wyjaśnione), domyślnie `Filter.NEW`. Kanwa (plansza 4) też nie miała „Wszystkie”.
- **Naprawa:** nowy filtr `ALL("Wszystkie")` jako pierwszy i domyślny; pomoc PLATFORM_REPORTS wymienia „Wszystkie”.


# 08 · Warianty aplikacji (prod / test / kids)

Jeden kod źródłowy, jeden `applicationId` bazowy `pl.ambif.ambify`. Wariant wybiera się **właściwościami Gradle**
(`-Pambify.*`), nie osobnymi gałęziami kodu. Są trzy równoległe wersje:

```
AMBiFy  (jeden kod źródłowy)
│
├─ PROD — prawdziwa aplikacja rodziny
│    build:         ./gradlew :app:assembleDebug
│    publikacja:    server/deploy/publish-app.sh
│    serwer:        http://192.168.1.122:8090   (prawdziwy, /opt/ambify-server)
│    applicationId: pl.ambif.ambify
│    ikona / nazwa: złota · „AMBiFy”
│    kanał wersji:  /app/latest      →  /pobierz
│    zbieranie zdarzeń: NIE
│
├─ TEST — próby przed wydaniem (NIGDY na telefonach rodziny)
│    build:         ./gradlew :app:assembleDebug -Pambify.serverUrl=http://192.168.1.122:18090
│    publikacja:    server/deploy/publish-app.sh --test
│    serwer:        http://192.168.1.122:18090  (testowy, osobna baza, konta demo test1234)
│    applicationId: pl.ambif.ambify.test        (instaluje się OBOK prawdziwej)
│    ikona / nazwa: czerwona · „AMBiFy TEST” · przyciski „Wejdź jako Owner/Admin/User”
│    kanał wersji:  /app/latest na serwerze TEST  →  /pobierz
│    zbieranie zdarzeń: NIE (chyba że dołożysz --kids, patrz niżej)
│
└─ KIDS — telefony dzieci (zbiera zdarzenia, wygląda jak PROD)
     build:         ./gradlew :app:assembleDebug -Pambify.kids=true
     publikacja:    server/deploy/publish-app.sh --kids
     serwer:        http://192.168.1.122:8090   (ten sam co PROD)
     applicationId: pl.ambif.ambify             (jak PROD — na jednym telefonie tylko jedna z nich)
     ikona / nazwa: złota · „AMBiFy”            (nieodróżnialna od PROD)
     różnica:       BuildConfig.KIDS=true → zbiera zdarzenia, dosyła na POST /monitor/events
     kanał wersji:  /app/kids/latest  →  /pobierz/kids
     zbieranie zdarzeń: TAK
```

## Porównanie

| | PROD | TEST | KIDS |
|---|---|---|---|
| Flaga Gradle | — | `-Pambify.serverUrl=…:18090` | `-Pambify.kids=true` |
| `applicationId` | `pl.ambif.ambify` | `pl.ambif.ambify.test` | `pl.ambif.ambify` |
| Serwer | 8090 (prawdziwy) | 18090 (testowy) | 8090 (prawdziwy) |
| Ikona / nazwa | złota „AMBiFy” | czerwona „AMBiFy TEST” | złota „AMBiFy” |
| Kanał aktualizacji | `/app/latest` | `/app/latest` (TEST) | `/app/kids/latest` |
| Strona pobierania | `/pobierz` | `/pobierz` (TEST) | `/pobierz/kids` |
| `BuildConfig.KIDS` | false | false | **true** |
| Zbieranie zdarzeń | nie | nie | **tak** → plik logu na serverku |

## Reguła kanałów (ważne)

Build **KIDS aktualizuje się TYLKO z kanału kids** (`/app/kids/…`). Gdyby czytał zwykły `/app/latest`, zassałby
PROD (ten sam `applicationId`) i **zgubiłby zbieranie zdarzeń**. Dlatego `BuildConfig.KIDS` przełącza zarówno
zbieranie, jak i kanał aktualizacji. Publikuj więc wersje dla dzieci wyłącznie przez `publish-app.sh --kids`.

## Katalogi na serwerze

```
/opt/ambify-server/releases/           ← PROD: latest.json + APK   (publish-app.sh)
/opt/ambify-server/releases/kids/      ← KIDS: latest.json + APK   (publish-app.sh --kids)
/opt/ambify-server/monitor/            ← logi zdarzeń KIDS, plik JSONL per dziecko
```
`deploy.sh` nie kasuje `releases/` ani `monitor/` (rsync `--exclude`), więc wdrożenie serwera nie usuwa
opublikowanych aplikacji ani logów.

## Testowanie builda KIDS bez dotykania prawdziwego serwera

Połącz flagi: `publish-app.sh --test --kids` → build KIDS (zbiera zdarzenia) z `applicationId .test`,
publikowany na kanał kids **serwera TEST**. Instalujesz go na zapasowym telefonie i sprawdzasz, że zdarzenia
docierają na serwer testowy i że aktualizacja przychodzi z kanału kids — nic z tego nie dotyka telefonów rodziny.

## Co wybrać kiedy

- Zmiana dla wszystkich (PROD) → testuj na TEST, po akceptacji Michała `publish-app.sh` + `deploy.sh`.
- Zmiana tylko dla telefonów dzieci → `publish-app.sh --kids`; telefony dzieci dociągną ją same.
- Próba czegokolwiek → zawsze najpierw TEST (zob. `.claude/skills/wdrozenie`).

---
name: wdrozenie
description: Jak wdrażać zmiany AMBiFy (aplikacja i serwer) — zawsze najpierw wersja TEST, prawdziwa dopiero po akceptacji Michała. Użyj przed każdą instalacją aplikacji na telefonie albo wdrożeniem serwera.
---

# Wdrażanie AMBiFy: najpierw TEST, potem prawdziwa

Zasada: **każda zmiana trafia najpierw do AMBiFy TEST**. Prawdziwą aplikację i prawdziwy serwer aktualizujesz
dopiero, gdy Michał przetestuje zmianę w wersji TEST i wprost ją zaakceptuje.

## 1. Wersja TEST (bez pytania o zgodę)

Serwer testowy (port 18090, osobna baza z kontami owner@/admin@/user@test.ambify, hasło `test1234`):

```
server/deploy/deploy-test.sh            # aktualizacja serwera testowego
server/deploy/deploy-test.sh --reset    # to samo + nowa baza z kontami testowymi
```

Aplikacja TEST (`pl.ambif.ambify.test`, czerwona ikona, przyciski „Wejdź jako Owner/Admin/User”):

```
./gradlew :app:assembleDebug -Pambify.serverUrl=http://192.168.1.122:18090
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Po instalacji powiedz Michałowi, co zmieniło się w wersji TEST i co ma sprawdzić.

## 2. Wersja prawdziwa (tylko po akceptacji Michała)

```
server/deploy/deploy.sh                  # prawdziwy serwer (konto ambify, /opt/ambify-server)
./gradlew :app:assembleDebug
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Nigdy

- `adb shell pm clear pl.ambif.ambify` ani odinstalowanie prawdziwej aplikacji: Michał jest w niej zalogowany.
- Testowanie na prawdziwym serwerze albo prawdziwej bazie (zakładanie kont, kody, wylogowania).
- Zakładanie danych w prawdziwej społeczności za Michała.

Telefon: adb po Wi-Fi, port z `adb mdns services` (zob. notatki o telefonie). Jeśli Michał akurat używa telefonu,
nie klikaj w niego przez adb.

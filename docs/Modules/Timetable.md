# 🗓️ Timetable (Plan lekcji)

> Version: **1.0**
>
> Status: **In progress** (Stage 4, v0.6)

---

← **[Back to Modules](README.md)**

---

## Purpose

The children's school timetable, straight from their school diary, together with the extra activities parents add
(swimming, English lessons…), per student, for a day or a week, with reminders.

---

## Where the lessons come from

The server reads the timetable from the school diary itself. No other program is needed.

**eduVulcan** (the first diary):

1. An Owner or Admin taps "Połącz z eduVulcan". They need the "Łączenie z dziennikiem szkolnym" permission, which
   Owner and Admin have by default.
2. The app opens eduvulcan.pl in a WebView, and the parent signs in there with their usual e-mail and password.
3. After each eduvulcan.pl page, the app reads `https://eduvulcan.pl/api/ap` in the background. Before the parent
   signs in, that page is the sign-in page. Afterwards it holds one JWT token per child, valid for about an hour.
4. The app sends only these tokens to the server. It then clears the WebView's cookies, so the eduVulcan session
   does not stay on the phone.
5. The server makes its own RSA key and registers it as a "device" with the tokens (`mobile/register/jwt`). From then
   on it reads the pupils (`mobile/register/hebe`) and the lessons with changes
   (`mobile/schedule/withchanges/byPupil`) with requests signed by that key.
6. The key is stored encrypted: AES-GCM, with the key file `diary.key` next to the database.
7. Every connection is updated:
   - every day at 18:00 Polish time;
   - right after connecting;
   - on "Odśwież teraz".

   An update covers a week back to four weeks ahead and replaces the lessons in that range.

**The password never reaches AMBiFy.** It is typed on Vulcan's own page.

The protocol is eduVulcan's unofficial mobile API, ported from the Python library
[iris](https://github.com/bbrjpl1310b/iris). A unit test checks that the Kotlin signing gives the same result as iris
for the same key. If Vulcan changes the API, the update fails. The error then shows in Ustawienia planu → Dziennik
szkolny, and the old lessons stay.

---

## Several parent accounts

- One community can connect several parent accounts, for example children in different schools, or both parents.
- A pupil seen by two accounts is one student.
- When a diary is connected for the first time, a student who already exists with the same first name keeps their
  member link and their activities.
- Connecting again after an error replaces the old connection that sees the same pupils.
- Disconnecting tells the diary, if it answers. The students and their lessons stay.

---

## Changes in the diary

| Diary | AMBiFy |
|---|---|
| Substitution with another teacher | "zastępstwo", the substitute teacher shown |
| Class absence / change type 1 / "odwołane" | "odwołana", subject struck through |
| Moved to another day | "odwołana" on the old day, with "przeniesiona na d.M" |
| Other changes (room, subject) | "zmiana" |

The note from the diary (reason, note for pupils) is shown in the lesson popup.

---

## Other diaries

The server talks to a diary through `SchoolDiary` (connect, pupils, lessons, disconnect). Librus Synergia and
Mobidziennik will be more implementations after v1.0 of the current scope (see the [Roadmap](../02_Roadmap.md)).

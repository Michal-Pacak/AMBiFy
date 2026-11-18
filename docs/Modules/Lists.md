# 📝 Lists (Listy)

> Version: **1.0**
>
> Status: **In progress** (Stage 5, v0.7 — on AMBiFy TEST since 2026-11-17)

---

← **[Back to Modules](README.md)**

---

## Purpose

One list engine for every kind of list a household keeps: **shopping** (quantity and unit, cart), **tasks** (for whom,
optional date, repeating) and **deadlines** (a date is required, sorted by date, overdue on top). A new kind of list
is configuration (`ListType`), not new code. Mock-ups: canvas "AMBiFy Listy (Stage 5)",
https://claude.ai/artifact/ErxisTDZFDxu5LXmmXr21a (boards 1–9); messages follow the Komunikaty canvas
([Luxury Theme → Messages](../06_Luxury_Theme.md#messages)).

Who sees and edits a list: the whole community or chosen people; planned (2026-11-18) — also chosen **roles**, so a
person who gets that role later sees the list automatically (the standard for every module, see the Roadmap's Key Decisions).

---

## Offline first

- The phone keeps a copy of the community's lists (`filesDir/lists/<community>.json`): what the server last said
  (`base`) plus the person's changes not confirmed yet (`pending`). The screens show both together, so a change is
  visible at once and marked "czeka na wysłanie" until the server has it.
- Every change has its own id made on the phone; `POST /lists/sync` sends the waiting ones and gets everything newer
  than the phone's `rev`. A change sent twice (lost answer) is done once.
- Sync: when the screen opens, after every change, every minute while open (every 15 s while something waits or the
  server is away), and in the background (`ListsSyncWorker`: every 15 min with a network, and once as soon as the
  network is back when changes wait — also with the app closed).
- Offline is a **status**, not an error: the bar "Bez połączenia z serwerem — działasz na kopii w telefonie".
  Without any copy (first start offline) the whole screen says so, with "Spróbuj ponownie".
- Members and premium features are kept with the copy (`<community>.people.json`), so avatars and the premium rows
  work offline too.

## Conflicts

Two people change the same field of an item (name, quantity, date, person, note, reminders) after the same version:
the server keeps both, shows the one made later, and marks the item "Konflikt". The **list's creator** decides
(keep one version, "Wprowadź inne" — may be empty — or remove the item); after 48 h the Owner/Admin may. Checking
off and changes of different fields are never conflicts. The creator gets a notification (background sync); everyone
gets "Rozstrzygnięto" afterwards. Deciding needs the server (the decision is final).

## Units

Built in: szt., kg, dag, l, opak., siatka, zgrzewka (pack units take a size: "2 siatki po 2,5 kg"); a unit typed by
someone becomes the community's own. "Zapamiętaj dla …" remembers a product's unit for the whole community. Quick add
understands "mleko 2", "ziemniaki 2 siatki", "szynka 20 dag" (Polish plural forms).

## Permissions

| Action | Owner | lowest role |
|--------|:-----:|:-----------:|
| `LISTS_CREATE` — creating lists | ✅ | ✅ |
| `LISTS_EDIT_ITEMS` — adding and checking items on lists one sees | ✅ | ✅ |
| `LISTS_TASKS_OTHERS` — tasks for other people | ✅ | after approval ("czeka na zatwierdzenie"; refused = removed) |
| `LISTS_DELETE_OTHERS` — renaming, sharing, archiving, deleting lists of others | ✅ | ❌ |

No approval for the first two: changes are made offline and must be done at once or not at all.

## Premium

- `LISTS_REMINDERS` — reminders of tasks and deadlines (a notification or an alarm, for chosen people, on the day /
  1, 3 or 7 days before, at a chosen hour), planned on each person's phone.
- `LISTS_MEAL_PLAN`, `LISTS_TO_MISSIONS` — shown locked ("wkrótce"), built together with Meals and Missions.

## Code

- `:shared` `Lists.kt` — models, `quickAdd`, units, plural forms.
- `:server` `lists/` — `ListService` (sync, conflicts, approvals), migration `V008__lists.sql`.
- `:app` `features/lists/` — `ListsScreen` (board 1), `ListDetail` (2–4), `ListDialogs` (5, 6, 8, 9, delete),
  `data/ListRepository`, `ListsState`, `ListsSyncWorker`, `ListReminders`.

## After the first test (0.7.0, TEST 2026-11-18)

Boards 10–18 of the Lists canvas (accepted 2026-11-18), migration V009:

- **Lists of things** (`ListType.SHOPPING`, shown as "Lista rzeczy") work by their `ListStyle`: ready-made Zakupy / Pakowanie
  or the person's own (section names, amounts yes/no, the button: to the history or "Odznacz wszystko", icon).
- **History**: "Zakończ" keeps the things (`cleared`, `clearedAt`, `clearedBy`) under the list until it is deleted; "Przywróć" unchecks.
- **Done at 100%**: the person whose check finished the list and who may manage it is asked: keep / archive / delete
  (answer remembered on the phone until something new is added); others get "Lista zrobiona".
- **Kind** (`ItemValues.variant`, field `VARIANT`): part of the name, lighter; kinds used before are offered (`list_variants`).
- **Duplicates**: adding the same thing (name + kind) asks: add to it (sum, kg/dag/g, l/ml) / replace / another kind / cancel;
  two copies after a sync show "Podwójnie?" → merge (edit + delete) or "różne rzeczy" (remembered on the phone).
- **PILNE** (`ItemValues.urgent`, part of `DATE`): tasks and deadlines without a date, at the top.
- **Sorting**: "Sortuj" on every screen sorts only its own level, personal, kept on the phone (`ListPrefs`).
- **Access** (`ListInfo.access`): rows per role and per person (person beats role): widzi → odhacza → dodaje → edytuje i usuwa cudze;
  the creator and the Owner always everything; never more than the community's table (`LISTS_EDIT_ITEMS`).
  Lists without rows (before 0.7) keep everyone / members.
- Locked rows: links, photos and files (0.7.1, `LISTS_ATTACHMENTS`), "Dodaj do kalendarza" (Stage 6).
- `list_items.parent_id` is there for sub-lists (0.7.1, `LISTS_SUBITEMS`, boards 16 and 18: unfold in place, optional own screen).

## Still to do

- Dashboard widgets of Lists (a module without a widget is not finished) — board 19, waiting for acceptance.
- 0.7.1: sub-lists with their own access, then links, photos and files.
- Check on phones in TEST (owner), then release.

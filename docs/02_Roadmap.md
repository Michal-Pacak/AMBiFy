# 🗺 Roadmap

> Version: **2.0**
>
> Status: **Approved** (2026-10-14)

---

← **[Back to README](../README.md)**

---

## Purpose

This document describes the development roadmap for the **AMBiFy** platform.

Features are delivered in small, vertical stages. Every stage is one release (`v0.x`, git tag) approved by the owner, ends with an app running on a real phone and leaves room for improvements found while testing.

---

## Key Decisions

| Topic | Decision |
|-------|----------|
| Backend | Own server on the home server (`serverek`): Ktor + SQLite, LAN-only until stage 9 |
| Repository | Monorepo: `:app` (Android), `:shared` (API models, permissions, module keys), `:server` (Ktor) |
| First users | Parents first; children start as profiles without accounts and get accounts in stage 7 |
| Calendar | AMBiFy's own calendar first, Google Calendar sync later |
| Module order | Accounts first, then Timetable, Shopping list and Calendar (changed 2026-10-24); the AI gateway (Stage 1A) is built when convenient, nothing depends on it |
| AI | Own gateway (`llm_gateway`) over the official Claude / GPT / Gemini / Copilot CLIs running on our subscriptions; the paid API only as a fallback |

---

## Stages

### Stage 0 — Skeleton complete (v0.2)

- [x] Brand logo at 83% height, launcher icon (Onyx), black launch screen
- [x] Navigation bar: 2 + logo + 2, pull-up panel for further modules
- [x] Module placeholders: Missions, Shopping, Communication, Timetable, School
- [x] Roadmap updated

### Stage 0.1 — Module bar carousel (v0.2.1)

- [x] Modules form one circular list; the collapsed bar shows four of them around the fixed logo
- [x] Swiping the collapsed bar left/right moves the modules any distance and snaps to the nearest one (resets on app restart); near the logo modules shrink and fade as if turning around a sphere
- [x] Choosing a module from the panel collapses the bar and shows that module first
- [x] Long press in the open panel drags a module to a new place (confirmed by vibration)
- [x] Module order stored on the phone (moves to the user account in stage 2)
- [x] Panel grip shown only when there are more than four modules
- [x] Bar labels: 11 sp, at most 2 lines x 10 characters ("Komunikacja" shown as "Czat")
- [x] Dashboard: larger logo without a label, transparent around the symbol (1 px outline), level with the labels; flatter cradle
- [x] Back on the Dashboard leaves the app without asking: the real app goes to the background, the test app closes (changed 2026-10-24)

### Stage 1 — Server foundation (v0.3)

- [x] `:shared` and `:server` modules, `GET /health`, migrations, tests
- [x] Deployment on the home server: system service under a dedicated `ambify` account, LAN-only nginx, daily database backup to the NAS
- [x] `GET /diagnostics`: checks registered by each module (database, migrations, disk space, NAS backup, app/server version compatibility)
- [x] App: network layer, fixed server address (changed only by an app update), connection status on the Account screen (connected / server works — other problem / no connection / checking) and a full diagnostics popup under "Check connection" (parents only from Stage 3)

### Stage 1A — AI Gateway (v0.3.1)

The gateway service does not depend on Stage 1 and can be built in parallel; the app part needs `:server` and the network layer.

- [ ] `llm_gateway` service on the home server: one HTTP API for the Claude / GPT (Codex) / Gemini / Copilot CLIs running on our subscriptions; `auto` routing by task type or a forced (`manual`) model; fallback when a limit is hit
- [ ] Text + file attachments, streamed answers, conversation history kept on the server (model-independent)
- [ ] `:server` route that forwards app requests to the gateway (LAN + token; per-child limits come with Stage 3)
- [ ] Assistant module in the app: chat with a model picker (Auto / Claude / GPT / Gemini) and attachments — layout approved first
- [ ] Optional for other modules: vulcan_digest switches from the paid API to the gateway (paid API stays as a fallback)
- Later, optional: voice conversation (speech-to-text → gateway → text-to-speech), then a true live mode (Gemini Live API)

### Stage 2 — Accounts, organization, child profiles (v0.4)

Decisions (2026-10-24): login with e-mail + password; the phone stays signed in until logout (session renewed while the app is used, expires after 90 days of inactivity, a parent can sign out a lost phone); new people join only with an invitation code.

- [x] Registration and login (Argon2 password hashes, JWT access + refresh tokens with rotation)
- [x] The first person creates the family and becomes its Owner; everyone else registers only with a one-time invitation code (valid 7 days)
- [x] Organization, members, invitation codes
- [x] Child profiles without accounts: name and color (the link to the student in Vulcan moves to Stage 4)
- [x] Signed-in devices per member; sign out a lost phone remotely
- [x] App flow: Splash → Login → Home; Account screen with profile, members, invitations and child profiles — layout approved first
- [x] Module order saved per user on the server

### Stage 3 — Roles & Permissions (v0.5)

Done early (2026-10-24, owner's decision): the group is called a **community** ("Społeczność") instead of a family; roles are shown in English — **Owner / Admin / User**; people without an account are **"Nieletni"** (minor) profiles.

Business model (2026-10-27, owner's decision): anyone can found a community. The first community where a person is the Owner is free and has the **basic** modules (the light, text-only ones: Calendar, Lists, Communication); every next one needs a one-time founding fee. **Premium** modules and features (Timetable, School, Missions, AI Assistant, attachments, extras inside modules) are always paid, per community, by its Owner. Payments come after Stage 9; until then only the Platform Admin grants them, free of charge. The Guest role is dropped: people outside the family are Users or found their own community.

- [x] Roles Owner, Admin, User: the Owner grants and takes back Admin; Owner and Admin manage only people with a lower role (invitations, minors, password reset codes, signing out, removing)
- [x] One account in many communities, a role per community; switcher on the Account screen; founding a new community and joining with a code while signed in
- [x] Sign-up without a code (account first, then found or join a community); e-mail addresses without Polish letters
- [x] Module catalog in `:shared` (basic / premium modules and premium features inside modules), enforced by the server
- [x] Owner and Admins switch the community's modules on and off; the module bar shows only the enabled ones
- [x] Platform Admin (the oldest Owner on the server): panel with all communities, grants premium modules and features, a free founding of one more community, password reset code for an Owner
- [x] Luxury theme on every screen (graphite and metallic gold, [06_Luxury_Theme.md](06_Luxury_Theme.md)); every module has its place on the module bar

### Stage 3.1 — Settings, avatars, floating cards (v0.5.1)

- [x] Ustawienia on the module bar instead of Konto: Konto (own data, e-mail change with the password, devices, connected accounts later), Społeczności (list; a popup with the role and the actions it allows, also for a community that is not the current one), Aplikacja (settings of this phone)
- [x] Owner renames the community; Admin and User can leave it (the Owner cannot until handing the role over exists)
- [x] Avatars: initials on a color; same initials in a community get a different shape; handed out once per community and never changed by others joining
- [x] Floating Luxury cards (sink towards the touch, gold rings), switchable in Ustawienia → Aplikacja

### Stage 4 — Timetable from vulcan_digest (v0.6)

- [ ] Child profile linked to the student in Vulcan
- [ ] vulcan_digest exports the timetable to the AMBiFy server after its daily run
- [ ] Timetable screen: tab per child, day/week view, current lesson highlighted, offline copy

### Stage 5 — Lists (v0.7)

One list engine for several list types: shopping, tasks, deadlines (a new type is configuration, not new code).

- [ ] Basic: shared lists of every type, items with quantity or date, who added it, check off
- [ ] Offline first: local database + synchronization (reused by later modules)
- [ ] Premium features: reminders (`LISTS_REMINDERS`), shopping list generated from planned meals (`LISTS_MEAL_PLAN`), moving a task to Missions (`LISTS_TO_MISSIONS`)

### Stage 6 — Calendar v1 (v0.8)

- [ ] Events: title, time, all-day, location, description, participants (members and child profiles)
- [ ] Month view, day/week agenda, create/edit/delete, filter by person
- [ ] Google Calendar: Google accounts connected in Ustawienia → Konto; for each community the person picks the Google calendar synchronized with it (see [Calendar](Modules/Calendar.md))

### Stage 7 — School & Dashboard (v0.9)

- [ ] Structured school summaries (tests, homework, reminders) from vulcan_digest
- [ ] School screen per child
- [ ] Dashboard: today's events, children's lessons today/tomorrow, upcoming tests

### Stage 8 — Children as users (v0.10)

- [ ] Child profile becomes an account (login set by a parent)
- [ ] Limited permissions managed by parents

### Later — Quick unlock

- [ ] The phone remembers the signed-in person; the app opens with a fingerprint, PIN or pattern instead of the password (a lock of the app on the phone, not a second password on the server) — layout approved first
- [ ] Password recovery by e-mail link (needs Stage 9)

### Stage 9 — Access outside home (v1.0)

- [ ] `ambify.pacak.waw.pl` over HTTPS, rate limiting, fail2ban
- [ ] Admin panel of the NAS restricted to LAN before router ports are opened

### Later

Missions → Communication → Google Calendar sync → push notifications → Finance, Documents.

---

## Module Recipe

Every new module is delivered the same way:

1. `:shared` — data models, module key, permissions.
2. `:server` — tables (migration), API routes, permission checks, tests.
3. `:app` — `features/<module>/{data, domain, ui}`, navigation entry, line icon.
4. `docs/Modules/<Module>.md` and status in the [Modules Index](Modules/README.md).
5. Release — tests, installation on a phone, screenshots, tag.

---

## Milestones

| Milestone | Status |
|-----------|:------:|
| Project Setup | ✅ |
| Documentation | ✅ |
| App Skeleton (v0.2) | ✅ |
| Server & Accounts (v0.3–v0.5) | ⏳ |
| Calendar & Timetable (v0.6–v0.7) | ⏳ |
| Family Release (v1.0) | 🔮 |

---

## Related Documents

| Document | Description |
|----------|-------------|
| [README](../README.md) | Project overview |
| [Vision](00_Vision.md) | Product vision |
| [Architecture](01_Architecture.md) | Platform architecture |
| [User Roles](04_User_Roles.md) | Roles and permissions |
| [Naming Convention](03_Naming_Convention.md) | Naming standards |

---

➡ **Next document:** [Naming Convention →](03_Naming_Convention.md)

### After v1.0 — Payments

- [ ] One-time founding fee for every next community, premium modules and features per community paid by its Owner (Google Play Billing for digital features in the Android app)

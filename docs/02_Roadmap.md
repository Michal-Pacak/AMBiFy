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
| Backend | Own server on the home server (`serverek`): Ktor + SQLite, LAN-only until stage 8 |
| Repository | Monorepo: `:app` (Android), `:shared` (API models, permissions, module keys), `:server` (Ktor) |
| First users | Parents first; children start as profiles without accounts and get accounts in stage 7 |
| Calendar | AMBiFy's own calendar first, Google Calendar sync later |
| Module priority | Calendar and Timetable |
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
- [x] Back on the Dashboard asks before closing the app

### Stage 1 — Server foundation (v0.3)

- [x] `:shared` and `:server` modules, `GET /health`, migrations, tests
- [ ] Deployment on the home server: systemd service, LAN-only nginx, daily database backup to the NAS
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

- [ ] Registration and login (Argon2, JWT access + refresh tokens)
- [ ] Organization, members, invitation codes
- [ ] Child profiles without accounts (linked to the student in Vulcan)
- [ ] App flow: Splash → Login → Home; Account screen with members, invitations and child profiles
- [ ] Module order saved per user on the server

### Stage 3 — Roles & Permissions (v0.5)

- [ ] Roles: Owner, Administrator, Member, Guest (family: parent = Owner/Admin, child = Member)
- [ ] Per-module permission catalog in `:shared`, enforced by the server
- [ ] Enabling/disabling modules per organization; navigation bar built from enabled and permitted modules

### Stage 4 — Calendar v1 (v0.6)

- [ ] Events: title, time, all-day, location, description, participants (members and child profiles)
- [ ] Month view, day/week agenda, create/edit/delete, filter by person
- [ ] Offline first: local database + synchronization (reused by later modules)

### Stage 5 — Timetable from vulcan_digest (v0.7)

- [ ] vulcan_digest exports the timetable to the AMBiFy server after its daily run
- [ ] Timetable screen: tab per child, day/week view, current lesson highlighted, offline copy

### Stage 6 — School & Dashboard (v0.8)

- [ ] Structured school summaries (tests, homework, reminders) from vulcan_digest
- [ ] School screen per child
- [ ] Dashboard: today's events, children's lessons today/tomorrow, upcoming tests

### Stage 7 — Children as users (v0.9)

- [ ] Child profile becomes an account (login set by a parent)
- [ ] Limited permissions managed by parents

### Stage 8 — Access outside home (v1.0)

- [ ] `ambify.pacak.waw.pl` over HTTPS, rate limiting, fail2ban
- [ ] Admin panel of the NAS restricted to LAN before router ports are opened

### Later

Missions → Shopping → Communication → Google Calendar sync → push notifications → Finance, Documents.

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

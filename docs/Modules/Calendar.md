# 📅 Calendar

> Status: **In development**

---

← **[Back to Modules](README.md)**

---

## Purpose

The Calendar module provides organization-wide scheduling while allowing each user to selectively synchronize events with a personal Google Calendar.

## Calendar connections

Decided 2026-10-27 (owner): Google accounts belong to the person, calendar choices to the person in each community.

- **Ustawienia → Konto → Połączone konta**: a person connects one or more Google accounts (e.g. private and work) to their AMBiFy account, once for all their phones.
- **Ustawienia → Społeczności → popup of a community → Kalendarz Google**: for every community separately the person picks which of their Google calendars is synchronized with that community's Calendar (e.g. "Rodzina" with Pacakowie, "Praca" with another one).
- The Owner or an Admin can connect a minor's personal calendar on the minor's behalf.
- A user chooses per event whether that event should also be synchronized with their personal calendar.
- Community events remain shared in AMBiFy regardless of personal synchronization choices.

## Implementation boundary

The current app contains the Calendar navigation destination and initial screen. Google authorization, calendar discovery, event CRUD, and synchronization are not implemented yet; they require a configured Google Cloud project and explicit OAuth scopes.


# 📅 Calendar

> Status: **In development**

---

← **[Back to Modules](README.md)**

---

## Purpose

The Calendar module provides organization-wide scheduling while allowing each user to selectively synchronize events with a personal Google Calendar.

## Calendar connections

- The organization owner configures the shared organization calendar.
- The owner can either select a calendar from a connected Google account or provide an existing calendar ID.
- Users can connect their own Google accounts for personal synchronization.
- A user chooses per event whether that event should also be synchronized with their personal calendar.
- For a family, a parent or guardian can connect a child's personal calendar on the child's behalf.
- Organization events remain shared through the organization calendar regardless of personal synchronization choices.

## Implementation boundary

The current app contains the Calendar navigation destination and initial screen. Google authorization, calendar discovery, event CRUD, and synchronization are not implemented yet; they require a configured Google Cloud project and explicit OAuth scopes.


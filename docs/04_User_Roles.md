# 👥 User Roles

> Version: **2.0** (v0.6: own roles and the permission table)
>
> Status: **Draft**

---

← **[Back to README](../README.md)**

---

## Purpose

This document describes the roles and permission model used throughout the **AMBiFy** platform.

---

## Roles

A person has one account and may belong to many communities, with one role in each of them.

| Role | Description |
|------|-------------|
| Owner | Founded the community; can do everything; the only fixed role |
| Own roles | Named and ordered by the Owner; a new community starts with **Admin** and **User**. Examples: family Rodzic / Dziecko, football club Zarząd / Trener / Zawodnik |
| Platform Admin | Above all communities (the oldest Owner on the server); grants premium modules and features and free foundings, never set through the API |

There is no Guest role and no minor profiles: a child is a User with their own e-mail (since v0.6). Temporary access
(an invitation whose membership expires) is in the backlog.

A role may assign only roles below its own and manage only people below it.

---

## Permission Table

Catalog of actions in `shared/.../Permissions.kt` (`Action`): module, action, scope and the default for the highest
and the lowest role. Each cell is one of:

| Mode | Meaning |
|------|---------|
| Dozwolone | Done at once |
| Po zatwierdzeniu | Saved as a request; an Admin or the Owner (a role allowed to do it at once) approves or rejects it |
| Niedozwolone | Refused |

- Defaults: the highest own role gets the "top" default, the lowest the "bottom" one; a role in between gets
  "between" (allowed + not allowed → after approval, allowed + after approval → allowed).
- The Owner changes any cell (Ustawienia → Społeczności → popup → Role i uprawnienia); a changed cell is marked
  "zmienione". Others see the table read-only.
- Some actions are for the Owner only (rename the community, roles, the table itself, buying premium) and some cannot
  wait for approval (switching modules, linking Vulcan).
- Each new module adds its actions to the catalog; the table grows with it. Later it may be shown as an
  "advertisement" of what a community can set.

---

## Related Documents

| Document | Description |
|----------|-------------|
| [README](../README.md) | Project overview |
| [Architecture](01_Architecture.md) | Platform architecture |
| [Naming Convention](03_Naming_Convention.md) | Naming standards |
| [Modules](05_Modules.md) | Planned modules |

---

➡ **Next document:** [Modules →](05_Modules.md)
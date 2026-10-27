# 🧩 Modules

> Version: **1.0**
>
> Status: **Draft**

---

← **[Back to README](../README.md)**

---

## Purpose

This document describes the concept of feature modules within the **AMBiFy** platform.

Feature modules provide business functionality while relying on the shared **Core Platform**.

---

## Module Architecture

Every module is:

- Independent
- Reusable
- Built on the shared Core
- Installed as part of the same application
- Developed without direct dependencies on other feature modules

---

## Core Responsibilities

Feature modules rely on the Core Platform for:

- Authentication
- Organization management
- Permissions
- Notifications
- Synchronization
- Database
- API communication
- Settings
- Localization
- Themes

---

## Module Principles

Every module should:

- Focus on a single business domain.
- Have a clearly defined responsibility.
- Avoid direct communication with other feature modules.
- Use only public Core services.
- Follow the project naming conventions.
- Maintain a consistent user experience.

---

## Basic and Premium

The catalog lives in `shared/.../Modules.kt` (`ModuleId`, `Feature`, `Entitlements`) and the server enforces it.

| Tier | Modules | Who pays |
|------|---------|----------|
| Basic | Calendar, Lists, Communication (text) | Included: free in the first own community, in the founding fee of every next one |
| Premium | Timetable, School, Missions, AI Assistant | The community's Owner, per community |
| Premium features | Lists: reminders, meal plan, to Missions; Communication: attachments | The community's Owner, per community |

Basic modules are the light, text-only ones (little space and server work). Until payments exist, premium is granted only by the Platform Admin. The Owner and Admins can switch off any module the community has; the module bar shows the enabled ones.

---

## Module Documentation

Detailed documentation for individual modules is maintained separately.

➡ **[Modules Index →](Modules/README.md)**

---

## Related Documents

| Document | Description |
|----------|-------------|
| [README](../README.md) | Project overview |
| [Architecture](01_Architecture.md) | Platform architecture |
| [User Roles](04_User_Roles.md) | Roles and permissions |

---

🏠 **Back to Project:** [README](../README.md)
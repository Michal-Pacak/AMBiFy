# ⚙️ Core Platform

> Version: **1.0**
>
> Status: **Draft**

---

← **[Back to Architecture](../01_Architecture.md)**

🏠 **[Back to README](../../README.md)**

---

## Purpose

This document is the entry point for the Core Platform documentation.

The Core Platform provides the shared infrastructure used by all AMBiFy feature modules.

Unlike feature modules, Core components are not business features. They provide common services used throughout the platform.

---

## Core Components

| Component | Description | Status |
|----------|-------------|:------:|
| [Authentication](Authentication.md) | User authentication and authorization | ⏳ |
| [Database](Database.md) | Local database layer | ⏳ |
| [API](API.md) | Remote API communication | ⏳ |
| [Synchronization](Synchronization.md) | Cloud synchronization | ⏳ |
| [Notifications](Notifications.md) | Notification system | ⏳ |
| [Settings](Settings.md) | Shared application settings | ⏳ |
| [Permissions](Permissions.md) | Roles and permission management | ⏳ |
| [Localization](Localization.md) | Languages and translations | ⏳ |
| [Themes](Themes.md) | UI themes | ⏳ |

---

## Design Principles

- Shared infrastructure
- Independent from business modules
- Reusable services
- Modular architecture
- Single source of truth

---

🏁 **Continue with any Core component above.**
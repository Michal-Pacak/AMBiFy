# 🏗 Architecture

> Version: **1.0**
>
> Status: **Draft**

---

← **[Back to README](../README.md)**

---

## Purpose

This document describes the architecture of the **AMBiFy** platform.

It defines how the platform is organized and how individual modules interact with each other.

---

## Platform Architecture

```text
AMBiFy
│
├── Core
│
├── Calendar
├── Missions
├── Shopping
├── Communication
├── Finance
├── Rewards
├── Inventory
├── Documents
└── Future modules...
```

Every module is independent but communicates through **Core** services.

---

## Core Responsibilities

Core is responsible for:

- Authentication
- Organizations
- Members
- Roles
- Permissions
- Settings
- Notifications
- Synchronization
- Local Database
- Cloud API
- Backup
- Themes
- Languages

> **Note**
>
> Core never contains business logic of individual feature modules.

---

## Feature Modules

Each feature module contains only:

- UI
- Domain
- Repository
- Database
- Navigation

> **Rule**
>
> Feature modules never communicate directly with each other.
>
> All communication goes through the **Core** layer.

---

## Architecture Principles

- One Core shared by all modules.
- Independent feature modules.
- Shared authentication.
- Shared permissions.
- Shared organization model.
- Modular development.
- Scalability by design.

---

## Related Documents

| Document | Description |
|----------|-------------|
| [README](../README.md) | Project overview |
| [Vision](00_Vision.md) | Product vision |
| [Roadmap](02_Roadmap.md) | Development roadmap |

---

➡ **Next document:** [Roadmap →](02_Roadmap.md)

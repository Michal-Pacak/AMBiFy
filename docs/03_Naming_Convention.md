# 📝 Naming Convention

> Version: **1.0**
>
> Status: **Draft**

---

← **[Back to README](../README.md)**

---

## Purpose

This document defines the naming conventions used throughout the **AMBiFy** project.

The goal is to maintain consistency across source code, modules, documentation, packages, classes, files, and repositories.

---

## Project Name

Official product name:

**AMBiFy**

Always use this exact capitalization in documentation and branding.

---

## General Rules

- Use **English** for source code and documentation.
- Use descriptive names.
- Avoid abbreviations unless they are commonly accepted.
- Keep naming consistent across all modules.

---

## Project Structure

```text
AMBiFy
├── app
├── core
├── feature-calendar
├── feature-missions
├── feature-shopping
└── docs
```

---

## Kotlin Naming

| Element | Convention | Example |
|----------|------------|---------|
| Package | lowercase | `com.ambify.calendar` |
| Class | PascalCase | `CalendarRepository` |
| Interface | PascalCase | `NotificationService` |
| Function | camelCase | `loadEvents()` |
| Variable | camelCase | `currentUser` |
| Constant | UPPER_SNAKE_CASE | `MAX_MEMBERS` |
| File | PascalCase | `CalendarScreen.kt` |

---

## Git Naming

| Element | Example |
|----------|---------|
| Feature | `feature/calendar` |
| Bug Fix | `fix/login` |
| Release | `release/1.0.0` |
| Hotfix | `hotfix/crash-startup` |

---

## Related Documents

| Document | Description |
|----------|-------------|
| [README](../README.md) | Project overview |
| [Vision](00_Vision.md) | Product vision |
| [Architecture](01_Architecture.md) | Platform architecture |
| [Roadmap](02_Roadmap.md) | Development roadmap |
| [User Roles](04_User_Roles.md) | Roles and permissions |

---

➡ **Next document:** [User Roles →](04_User_Roles.md)
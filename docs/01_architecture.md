# Platform Architecture

**AMBiFy:**

- Core
- Calendar
- Missions
- Shopping
- Communication
- Finance
- Rewards
- Inventory
- Documents
- Future modules...


*Every module is independent but communicates through Core services.*

---
# Core responsibilities

*Core is responsible for:*

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

*Core never contains business logic of individual modules.*

---

# Feature Module

**Each module contains only:**

- UI
- Domain
- Repository
- Database
- Navigation

*No module communicates directly with another module.
Everything goes through Core.*
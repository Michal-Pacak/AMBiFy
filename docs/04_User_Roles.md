# 👥 User Roles

> Version: **1.0**
>
> Status: **Draft**

---

← **[Back to README](../README.md)**

---

## Purpose

This document describes the roles and permission model used throughout the **AMBiFy** platform.

---

## Roles

A person has one account and may belong to many communities, with a role in each of them.

| Role | Description |
|------|-------------|
| Owner | Founded the community; full control, grants and takes back the Admin role |
| Admin | Invites people, manages minors and people with the User role, switches modules on and off |
| User | Uses the modules the community has switched on |
| Nieletni | A minor's profile without an account, managed by the Owner and Admins |
| Platform Admin | Above all communities (the oldest Owner on the server); grants premium modules and features and free foundings, never set through the API |

There is no Guest role: someone from outside the family is a User or founds their own community. Temporary access (an invitation whose membership expires) is in the backlog.

---

## Permission Principles

- Role-based access control (RBAC)
- Least privilege
- Module-specific permissions
- Organization-wide consistency

---

## Future Extensions

The permission system should support:

- Custom roles
- Custom permissions
- Module-level permissions
- Temporary access
- Invitation-based access

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
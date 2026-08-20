# Hostvero

Hostvero is a host-only guest management platform for short-term rental operators, serviced apartments, guesthouses, and small hotels.

## Stack

- Java 21
- Spring Boot
- Maven
- PostgreSQL / Supabase
- HTML
- CSS
- JavaScript

## Development documentation

See:

- `AGENTS.md`
- `docs/PRODUCT_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/DATABASE.md`
- `docs/API_SPEC.md`
- `docs/SECURITY.md`
- `docs/ROADMAP.md`

## Local database configuration

Copy `.env.example` to `.env`, then replace its placeholders using the connection
details from the Supabase **Connect** panel. The `.env` file is ignored by Git;
use environment variables instead when deploying.

Run the Phase 0 checks with:

```powershell
.\mvnw.cmd test
```

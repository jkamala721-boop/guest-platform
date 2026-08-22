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

## Stripe Checkout sandbox

Stripe remains in safe mock mode by default. To test Stripe Checkout manually, set these values only in an ignored
`.env` file or your deployment secret store:

```properties
STRIPE_PAYMENT_MODE=live
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
HOSTVERO_PUBLIC_BASE_URL=http://localhost:8080
```

Start the application, then forward Stripe test events to the public webhook endpoint with:

```powershell
stripe listen --forward-to http://localhost:8080/api/webhooks/stripe
```

Copy the `whsec_...` value printed by Stripe CLI into `STRIPE_WEBHOOK_SECRET`. A Checkout return URL never confirms a
booking; only Stripe's signature-verified webhook does.

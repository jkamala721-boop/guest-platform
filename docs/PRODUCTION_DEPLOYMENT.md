# Hostvero production deployment (Render + Supabase)

## Architecture

Render runs the Java 21 Hostvero container. Render terminates HTTPS and forwards requests to Spring Boot; Spring
honours forwarded headers. The service connects to Supabase PostgreSQL over SSL and Flyway applies pending migrations
before the application becomes healthy. The Render health check calls `GET /api/health`.

Render does not currently provide a native JVM runtime, so this repository uses the minimal Java 21 `Dockerfile`.
The image build command is `./mvnw -B -DskipTests package`; its start command is:

```sh
exec java ${JAVA_OPTS:-} -jar /app/app.jar --server.port=${PORT:-8080}
```

Do not set a separate Render build or start command for this Docker service. Render builds the repository `Dockerfile`.

## Render setup checklist

1. Create a Render **Web Service** from the Hostvero GitHub repository and select the `docker` runtime.
2. Use the production branch and either create from `render.yaml` or configure the matching variables below.
3. Set `SPRING_PROFILES_ACTIVE=prod`; the image also defaults to this profile.
4. Configure the Supabase JDBC URL, username, and password in Render's secret store.
5. Set `HOSTVERO_PUBLIC_BASE_URL` to the final `https://` custom or `onrender.com` URL. Do not use localhost.
6. Configure Resend variables before deployment. Production scheduled notifications use `EMAIL`.
7. For Kenya production payments, set `PAYSTACK_PAYMENT_MODE=live` and configure the Paystack secret key. Keep
   `STRIPE_PAYMENT_MODE=mock`, `MPESA_PAYMENT_MODE=mock`, and `WHATSAPP_ENABLED=false` unless those providers are
   explicitly enabled.
8. Deploy. Confirm Flyway completed, then confirm `GET /api/health` responds with HTTP 200.
9. Verify host registration/login, an authenticated host request, and a secure guest link.
10. Inspect Render logs for normal startup, without credentials, bearer tokens, guest tokens, or OTP values.

## Render environment variables

| Variable | Required in Phase 10A | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | `prod` |
| `SUPABASE_DB_JDBC_URL` | Yes | PostgreSQL JDBC URL with `sslmode=require`; use the direct endpoint when Render can reach it, otherwise the Supavisor **session** pooler endpoint. Do not use the transaction pooler for this persistent Hibernate service. |
| `SUPABASE_DB_USERNAME` | Yes | Supabase database role/user from the Connect panel. |
| `SUPABASE_DB_PASSWORD` | Yes | Secret. |
| `HOSTVERO_PUBLIC_BASE_URL` | Yes | Final non-localhost HTTPS origin; used for payment and secure guest links. |
| `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS` | Recommended | Start with `5`, `1`, and `10000`; stay within the Supabase connection limit. |
| `AUTH_SESSION_TTL_HOURS` | Optional | Defaults to 24. |
| `RESEND_ENABLED` | Yes | `true` in production. |
| `RESEND_API_KEY`, `RESEND_FROM_EMAIL` | Yes | Resend secret and verified sender. The property name is `RESEND_FROM_EMAIL`. |
| `NOTIFICATION_DEFAULT_CHANNEL` | Yes | `EMAIL`. |
| `STRIPE_PAYMENT_MODE` | Yes | Keep `mock` in Phase 10A. If changed to `live`, `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` become mandatory. |
| `PAYSTACK_PAYMENT_MODE` | Yes for Kenya production | Set `live` to use Paystack-hosted M-Pesa/card checkout; otherwise keep `mock`. |
| `PAYSTACK_SECRET_KEY` | Required when Paystack is live | Secret server-side key. Never expose it in JavaScript or logs. |
| `PAYSTACK_PUBLIC_KEY` | Optional | Reserved for a future Paystack client integration; the current hosted-checkout flow does not use it. |
| `MPESA_PAYMENT_MODE` | Yes | Keep `mock`; real Daraja is intentionally not part of this pass. |
| `MPESA_WEBHOOK_SECRET` | Optional while M-Pesa is mock | Reserve for the future provider implementation. |
| `WHATSAPP_ENABLED` | Yes | `false` in Phase 10A. When enabled later, all `WHATSAPP_*` credentials and approved template names are required. |

When WhatsApp is eventually enabled, configure `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`,
`WHATSAPP_API_VERSION`, `WHATSAPP_MANUAL_TEMPLATE_NAME`, `WHATSAPP_GUEST_LINK_TEMPLATE_NAME`,
`WHATSAPP_SCHEDULED_TEMPLATE_NAME`, and optionally `WHATSAPP_LANGUAGE_CODE`. Do not add those values while the
integration is disabled.

## Migrations and deploy safety

- Flyway runs during application startup, before Render observes a healthy application.
- V1--V18 are immutable. Every later schema change receives a new versioned migration; never edit an applied file.
- Flyway validates applied migrations and has `clean` disabled in production.
- A migration failure fails the new deploy; Render keeps the last successful deployment running. Investigate the
  Flyway error and `flyway_schema_history` before retrying.
- Application rollback restores a previous application image only. It does **not** undo a successful database migration.
  Do not edit old migrations or invent destructive down-migrations. Use a new corrective migration or restore into a
  safe environment after an approved recovery decision.

## Backups and recovery

Supabase owns database backups. Before a significant migration, confirm the project plan's backup/PITR capability and
verify a recent backup under **Supabase Dashboard → Database → Backups**. Free-tier projects should also maintain an
encrypted, access-controlled logical export on an approved schedule.

For recovery, first restore into a separate safe project/environment and validate schema, Flyway history, application
startup, and a representative set of records. Do not test a restore by overwriting production. Record the target time,
approval, and the required post-restore credential rotation before any production recovery.

## Operational checks

- Render: check deploy and runtime logs, `GET /api/health`, and configured health-check status.
- Supabase: check connection utilisation and `flyway_schema_history`; use the Connect panel rather than copying
  credentials into source files.
- Providers: verify only configured providers. A disabled provider must have no credentials and make no network calls.

## Paystack hosted checkout

Hostvero initializes Paystack transactions from the backend and redirects guests to Paystack's hosted checkout. The
guest is charged the booking amount plus a server-calculated 5% Hostvero service fee; cash payments never receive that
fee. Flyway V17 stores the booking amount and fee separately from the charged payment amount for auditability. Flyway
V18 adds host payout destinations and provider-fee/settlement accounting for Paystack subaccount payments.

Configure the Paystack Dashboard webhook URL as:

```text
https://guest-platform.onrender.com/api/webhooks/paystack
```

Paystack's `x-paystack-signature` is validated against `PAYSTACK_SECRET_KEY`. In live mode, Hostvero additionally
calls Paystack's server-side transaction verification endpoint before the shared payment-completion flow runs. A
browser callback only returns the guest to the secure link; it never confirms the booking.

# Stage 1 pre-PR verification

Verified November 14, 2026 in `/tmp/hostvero-property-offers-stage1`, branch
`feat/property-offers-stage1`, based on `852f1c4`.

## Full Maven suite

Command: `bash mvnw test` (no test selection, exclusions, or skipped tests).

Result: **BUILD SUCCESS — 213 tests, 0 failures, 0 errors, 0 skipped**.
Java 21.0.12.1; PostgreSQL checks use disposable PostgreSQL 14.22.
The full run completed in 2 minutes 35 seconds. Local log:
`/tmp/hostvero-full-suite.log`.

JavaScript syntax checks passed for `app.js`, `offers.js`, and
`service-worker.js`. `git diff --check` passed.

## Workflow evidence and browser limitation

No browser-control tool, browser executable on PATH, installed Playwright runtime,
or cached Playwright browser was available in this session. No browser server was
started. **The complete journey has not been verified in a browser. Desktop and
mobile rendering, touch interaction, clipboard sharing, and navigation remain
unverified.** API tests and syntax checks do not establish browser correctness.

The full suite provides the following automated server-side evidence:

| Workflow step | Evidence |
| --- | --- |
| Host creates an offer | Owner authorization, immutable price/currency, seven-day expiry, revocation tests pass. |
| Host shares an offer | Token issuance and public token-scoped resolution pass; actual copying/sharing was not exercised. |
| Guest selects dates | Valid/invalid dates, adjacent stays, pending extensions, and concurrent host/guest reservation tests pass. |
| Backend prices the stay | Stored offer rate, currency, total, separate Paystack fee, and price-tampering tests pass. |
| Guest registers | Registration and existing email-verification integration tests pass; registration alone does not extend the hold. |
| Guest confirms a future reservation | Explicit confirmation and idempotency tests pass; deadlines are enforced independently of cleanup. |
| Payment eligibility | T−24 opening, T−12 release, arrivals after T−12, check-in-capped holds, and late-payment handling pass. |
| Existing booking links | Existing registration, payment, receipt, stay activation, expiry, and access-policy regression suites pass. |

Payment/provider tests use mocks or local test fixtures, including signed webhook
fixtures. No live payment was charged and no real guest notification was sent.

## V32/V33 migration compatibility

Added `PropertyOfferMigrationTest` to test a populated **V31 → V33** upgrade using
real PostgreSQL and the actual Flyway migrations. It seeds existing hosts,
properties, bookings in every existing booking status, guest links, successful
payments, and receipts before upgrading.

Verified:

* Exactly V32 and V33 apply; Flyway validation succeeds afterward.
* Existing column values and records remain unchanged, including token hashes,
  amounts, statuses, expiry timestamps, and receipt/payment references.
* Legacy booking offer/deadline fields and payment resolution flags remain null.
* The new offers table has RLS enabled and no SELECT/INSERT/UPDATE/DELETE privileges
  for `anon` or `authenticated`.
* Re-running Flyway applies zero migrations.
* Initial empty-database migration and application integration tests also pass.

V32 is additive; it does not backfill, update, or delete legacy records. The new
booking constraint accepts existing rows because their new fields are all null.
V33 affects only the new offers table and follows the existing Supabase role
protection pattern. No earlier migration file was modified.

Compatibility is established for the tested schema/data on PostgreSQL 14.22.
No production database or production backup was inspected or migrated. Production
migration duration and lock contention were not measured: ordinary ALTER TABLE,
constraint validation, and index creation can block concurrent traffic. This is
not a claim of a zero-downtime migration. The `anon` and `authenticated` roles must
exist, as they do for the project's existing Supabase migration pattern.

## Changes in this verification pass

Only these files were added or updated during this pass:

* `src/test/java/com/guest_platform/PropertyOfferMigrationTest.java` — populated upgrade compatibility test.
* `docs/PROPERTY_OFFERS_STAGE1.md` — link to this full-suite verification record.
* `docs/STAGE1_VERIFICATION.md` — this report and exact Stage 1 file inventory.

No production code was changed during this verification pass. Automated checks
found no blocking workflow defect. Browser-only defects cannot be ruled out
without the missing browser verification. No features or unrelated refactoring
were added. No PR, push, merge, deployment, or production migration was performed.

## Exact Stage 1 changed files

The complete local change set relative to `852f1c4` contains these 38 files,
including untracked new files and this report:

```text
docs/API_SPEC.md
docs/PROPERTY_OFFERS_STAGE1.md
docs/ROADMAP.md
docs/STAGE1_VERIFICATION.md
pom.xml
src/main/java/com/guest_platform/config/SecurityConfig.java
src/main/java/com/guest_platform/controller/PropertyOfferController.java
src/main/java/com/guest_platform/controller/PublicGuestLinkController.java
src/main/java/com/guest_platform/controller/WebPageController.java
src/main/java/com/guest_platform/dto/BookingResponse.java
src/main/java/com/guest_platform/dto/PaymentResponse.java
src/main/java/com/guest_platform/dto/PublicGuestRegistrationOrPaymentResponse.java
src/main/java/com/guest_platform/entity/Booking.java
src/main/java/com/guest_platform/entity/Payment.java
src/main/java/com/guest_platform/entity/Property.java
src/main/java/com/guest_platform/entity/PropertyOffer.java
src/main/java/com/guest_platform/repository/BookingRepository.java
src/main/java/com/guest_platform/repository/PropertyOfferRepository.java
src/main/java/com/guest_platform/security/PublicRateLimitFilter.java
src/main/java/com/guest_platform/service/AvailabilityService.java
src/main/java/com/guest_platform/service/BookingService.java
src/main/java/com/guest_platform/service/GuestLinkService.java
src/main/java/com/guest_platform/service/NotificationService.java
src/main/java/com/guest_platform/service/OfferBookingPolicy.java
src/main/java/com/guest_platform/service/OfferReservationScheduler.java
src/main/java/com/guest_platform/service/OfferReservationService.java
src/main/java/com/guest_platform/service/PaymentService.java
src/main/java/com/guest_platform/service/PropertyOfferService.java
src/main/resources/application.properties
src/main/resources/db/core-migration/V32__property_offers_and_checkout_deadlines.sql
src/main/resources/db/postgresql-migration/V33__protect_property_offers.sql
src/main/resources/static/js/app.js
src/main/resources/static/js/offers.js
src/main/resources/static/service-worker.js
src/test/java/com/guest_platform/PropertyOfferMigrationTest.java
src/test/java/com/guest_platform/PropertyOfferPostgresIntegrationTest.java
src/test/java/com/guest_platform/security/PublicRateLimitFilterTest.java
src/test/java/com/guest_platform/service/OfferBookingPolicyTest.java
```

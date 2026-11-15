# Stage 2 implementation verification

Branch: `feat/property-photos-stage2`.
Worktree: `/tmp/hostvero-property-photos-stage2`.
Base: main `36dd9b0e3f042e2c39e1692663558d6904755e50`.

All application edits and additive migrations are local to this worktree. Nothing
was pushed, merged, deployed or applied to production. No bucket, Render setting,
production database, payment or guest message was modified. Credentials are supplied
through backend environment configuration; tests use placeholders and mocks.

This paragraph records implementation-time state. Subsequent PR preparation is
authorized to commit/push this isolated feature branch and open a PR to main;
production changes, feature enablement, deployment and merging remain prohibited.

## Automated verification

Java 21.0.12.1; real disposable PostgreSQL 14.22 for integration/upgrade tests;
existing H2 tests retained. Supabase Storage and HTTP transport are mocked. No real
Supabase database/Storage endpoint is contacted by the tests.

- Initial focused HTTP/PostgreSQL tests: 22 passed, zero failures/errors/skips.
- Expanded migration/image/integration checks: 26 passed, zero failures/errors/skips.
- First full Maven suite: **246 tests, zero failures/errors/skips; BUILD SUCCESS**.
- Final full suite after decoder-warning handling, missing-file error mapping and
  isolated cleanup scheduling: **247 tests, zero failures, zero errors, zero skips;
  BUILD SUCCESS**. Completed November 14, 2026 at 22:43:52 America/Los_Angeles,
  duration 3 minutes 48 seconds. Final JavaScript navigation/reorder safeguards
  were separately syntax-checked after their last edits.

Command: `bash mvnw test` without selection/exclusions or skipped tests.
Final run log: `/tmp/hostvero-stage2-full-suite-final.log` (local, not committed).

JavaScript syntax checks passed with `node --check` for `photos.js`, `api.js`,
`app.js`, `offers.js`, and `service-worker.js`. This uses the existing local syntax
checker; no Node application runtime or npm dependency was added. `git diff --check`
passed. No production infrastructure configuration file was edited.

### Coverage added

- PropertyPhotoIntegrationTest: owner/stranger/unauthenticated HTTP access, optimized
  image output, invalid/type-mismatched/oversized uploads, exact order validation,
  cover persistence/fallback, logical deletion, failed deletion retry, partial
  upload invisibility/recovery, stale-upload reclamation, active/in-progress count
  limit, competing application instances at the 15th slot, offer/guest link access,
  revoked/expired links, existing host-created booking links, signed lifetime caps,
  and isolation of gallery signing failures from existing booking pages.
- This class deliberately reuses the existing Stage 1 PostgreSQL fixture and also
  runs its 15 inherited offer/payment/availability regression tests.
- PropertyPhotoMigrationTest: populated V33 → V35, unchanged properties/offers,
  empty initial photo table, Flyway validation/repeat run, RLS and denied Data API roles.
- PhotoProcessorTest: all eight EXIF orientations, source metadata removal,
  dimension-bomb and animated PNG rejection, transparency flattening/no upscaling,
  truncated JPEG rejection.
- SupabasePhotoStorageTest: actual backend adapter with mocked JDK transport;
  immutable upload path/headers, bulk deletion payload, batched signing/lifetime,
  foreign URL rejection, safe failure messages and precise-origin CSP configuration.
- Historical Stage 1 migration test is pinned to V33, retaining its original V31 →
  V33 assertion rather than accidentally testing a different upgrade after V34/V35.

## Practical verification limits

No browser runtime is available here. Desktop/mobile rendering, touch/swipe,
clipboard and fullscreen keyboard/focus behavior require the documented manual
staging acceptance. Syntax and HTTP tests do not establish browser correctness.

Real development-bucket upload/sign/delete interoperability remains unverified;
all Storage tests use mocks until the actual Supabase project is confirmed.
Staging memory/latency checks on large real camera images remain necessary before
production enablement. No claim of production capacity is made from test success.

## Remaining deployment dependencies

1. Confirm the production Supabase project reference and read-only access. The
   visible inactive eu-west-1 project is not confirmed as Hostvero's eu-north-1
   pooler-backed production database; do not resume it.
2. Verify/provision private `hostvero-property-photos` and server-only access in
   the confirmed project, under separate authorization. The application never
   creates buckets/policies automatically.
3. Configure backend-only `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`; keep
   `PROPERTY_PHOTOS_ENABLED=false` until bucket, credentials and staging checks pass.
   CSP derives the exact configured origin. Render changes are not authorized yet.
4. Verify production Flyway is still V33 with no version collision, back up metadata
   and separately plan Storage object backups/restores; rehearse V34/V35 on a clone.
5. Complete desktop/mobile staging acceptance and a controlled development-bucket
   smoke test without real bookings/payments/notifications.
6. Explicit approval is required before production migrations, enablement, deploy,
   push or merge. Main currently auto-deploys, so a push/merge is consequential.

An application rollback cannot remove Storage objects or undo metadata changes.
Keep the additive migrations/data, disable photo entry points through an approved
release decision if necessary, and prefer a forward fix. Stopping the photo worker
only delays cleanup; it must not justify dropping lifecycle rows or deleting a bucket.

## Changed-file inventory

The inventory includes the approved planning document already present in this
worktree, new implementation/verification documentation, application code, migrations
and tests. No secret, dump, environment file or generated build output is included.

- `docs/API_SPEC.md`
- `docs/DATABASE.md`
- `docs/FRONTEND.md`
- `docs/PROPERTY_PHOTOS_STAGE2.md`
- `docs/PROPERTY_PHOTOS_STAGE2_PLAN.md`
- `docs/STAGE2_VERIFICATION.md`
- `pom.xml`
- `src/main/java/com/guest_platform/config/SecurityConfig.java`
- `src/main/java/com/guest_platform/controller/ApiExceptionHandler.java`
- `src/main/java/com/guest_platform/controller/PropertyPhotoController.java`
- `src/main/java/com/guest_platform/security/PublicRateLimitFilter.java`
- `src/main/java/com/guest_platform/service/GuestLinkService.java`
- `src/main/java/com/guest_platform/service/PropertyOfferService.java`
- `src/main/java/com/guest_platform/service/photo/PhotoProcessor.java`
- `src/main/java/com/guest_platform/service/photo/PhotoSettings.java`
- `src/main/java/com/guest_platform/service/photo/PhotoStorage.java`
- `src/main/java/com/guest_platform/service/photo/PhotoUnavailableException.java`
- `src/main/java/com/guest_platform/service/photo/PropertyPhotoService.java`
- `src/main/java/com/guest_platform/service/photo/SupabasePhotoStorage.java`
- `src/main/resources/application.properties`
- `src/main/resources/db/core-migration/V34__property_photographs.sql`
- `src/main/resources/db/postgresql-migration/V35__protect_property_photographs.sql`
- `src/main/resources/static/css/pages.css`
- `src/main/resources/static/js/api.js`
- `src/main/resources/static/js/app.js`
- `src/main/resources/static/js/offers.js`
- `src/main/resources/static/js/photos.js`
- `src/main/resources/static/service-worker.js`
- `src/test/java/com/guest_platform/PropertyOfferMigrationTest.java`
- `src/test/java/com/guest_platform/PropertyPhotoIntegrationTest.java`
- `src/test/java/com/guest_platform/PropertyPhotoMigrationTest.java`
- `src/test/java/com/guest_platform/service/photo/PhotoProcessorTest.java`
- `src/test/java/com/guest_platform/service/photo/SupabasePhotoStorageTest.java`

# Hostvero production database migration runbook

Use this runbook for every production schema change. It protects the Supabase PostgreSQL database, preserves Flyway history, and verifies the Render deployment rather than treating a successful build as a successful release.

## Current production baseline

- PostgreSQL on Supabase
- Flyway migrations currently through V20
- Render production deployment
- Spring Boot
- Hibernate `validate`
- Flyway `clean` disabled
- Better Stack external health monitoring

## 1. Before deployment

1. Confirm the working tree is clean before starting release work.
2. Run focused tests relevant to the change.
3. Run the full test suite:

   ```powershell
   .\mvnw.cmd test
   ```

   Require `Failures: 0`, `Errors: 0`, and `BUILD SUCCESS`.

4. Check the release diff:

   ```powershell
   git diff --check
   ```

5. If frontend JavaScript changed, also run:

   ```powershell
   node --check src/main/resources/static/js/app.js
   ```

6. Create a verified production backup:

   ```powershell
   .\scripts\backup-production.ps1
   ```

   Confirm that the script reports `Verification: succeeded`. Backup files are never committed.

7. Confirm the new Flyway migration has the next unused version number. Existing migrations are immutable: **V1–V20 must never be edited.**

## 2. Migration rules

- Every production schema change uses a new Flyway migration.
- Never edit an already-deployed migration.
- Never use Flyway `clean` in production.
- Hibernate production mode remains `validate`; Flyway is the schema authority.
- Make migration scripts backward-safe where practical so a rolling application deployment does not fail unexpectedly.
- Destructive migrations require an explicit, reviewed recovery plan before deployment.

## 3. Deployment

Use the current release flow:

```powershell
git add .
git commit -m "<message>"
git checkout main
git pull origin main
git merge phase-8-ux-redesign
git push origin main
```

Render deploys from GitHub `main`. Never force-push production `main`.

## 4. During Render deployment

- Watch the Render deployment and runtime logs.
- Confirm Flyway detects the expected new migration.
- Confirm the migration completes successfully.
- Confirm Spring Boot starts successfully after Flyway completes.
- Do not consider deployment complete merely because the Docker build succeeds.

## 5. Post-deploy verification

1. Verify [the Hostvero health endpoint](https://app.hostvero.net/api/health) returns HTTP 200.
2. Check the Better Stack uptime monitor.
3. Check Render logs for Flyway, Hibernate, database, payment, and startup errors.
4. Run a minimal smoke test of the feature affected by the migration.

## 6. If a migration fails

1. Stop further deployments.
2. Do **not** edit the failed already-deployed migration blindly.
3. Determine whether Flyway applied any part of it.
4. Inspect `flyway_schema_history` and the actual production schema.
5. Preserve the production backup.
6. If the migration was transactional and rolled back, create a corrected **new** migration version where appropriate.
7. If partial or non-transactional changes occurred, inspect actual state and write an explicit repair migration.
8. Do not use Flyway `clean`.
9. Do not delete production data merely to make a migration pass.
10. Do not manually mark a migration successful unless the actual schema is proven equivalent and the decision is documented.

## 7. Application failure after a successful migration

If a schema migration succeeded but application code fails, prefer fixing or rolling forward the application code. Do not automatically reverse a successful migration. If rollback is unavoidable, use a specifically designed compensating migration; never edit migration history.

## 8. Backup and restore

Supabase-managed automatic backups are unavailable on the current plan. Hostvero therefore uses local logical `pg_dump` backups before risky production migrations.

- Backup files live under `backups/` and are Git-ignored.
- Backup verification uses `pg_restore --list`.
- Test full restores only against a non-production database; never restore directly over production.
- Preserve a verified backup until the release is complete and the recovery window has passed.

## 9. Security

- Never place database credentials in documentation.
- Never commit `.env` files, dump files, keys, tokens, or passwords.
- Production secrets stay in environment variables.
- The backup script reads `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD` only from the environment and never prints them.

## 10. Release checklist

- [ ] Focused tests run where relevant.
- [ ] `./mvnw.cmd test` reports zero failures, zero errors, and `BUILD SUCCESS`.
- [ ] `git diff --check` passes.
- [ ] JavaScript syntax check passes when frontend code changed.
- [ ] Verified production backup created; no dump files staged for Git.
- [ ] New Flyway migration uses the next version; V1–V20 remain untouched.
- [ ] Release merged and pushed to `main` without force-push.
- [ ] Flyway reports the expected migration as successful on Render.
- [ ] Spring Boot starts successfully.
- [ ] Health endpoint returns HTTP 200.
- [ ] Render logs are clean and Better Stack is healthy.
- [ ] A smoke test of the migrated feature passes.

# Hostvero Production Database Migration Runbook

## 1. Purpose and scope

This runbook defines the procedure for preparing, applying, and verifying production database schema changes for Hostvero.

Its purpose is to protect production data, preserve Flyway migration history, and ensure that application releases remain compatible with the database.

This document describes the repository's configured production architecture. It does not independently verify the current state of the production database, Render dashboard, Supabase account, or external monitoring services.

Never assume that repository configuration proves the live production state.

## 2. Repository-verified production configuration

The following configuration has been verified in the repository:

| Component | Repository configuration |
|---|---|
| Application | Spring Boot |
| Hosting definition | Render web service named `hostvero` |
| Container runtime | Docker |
| Database | PostgreSQL |
| Database configuration | Supabase connection variables |
| Active production profile | `prod` |
| Schema migration tool | Flyway |
| Flyway migration locations | `db/core-migration` and `db/postgresql-migration` |
| Hibernate schema behavior | `validate` |
| Flyway production behavior | Enabled |
| Flyway migration validation | Enabled |
| Flyway clean protection | `clean-disabled=true` |
| Application health-check path | `/api/health` |
| Application port | Render-provided `PORT`, defaulting to 8080 |

Relevant configuration files:

- `render.yaml`
- `src/main/resources/application.properties`
- `src/main/resources/application-prod.properties`
- `pom.xml`
- `.env.example`

### 2.1 Migration version baseline

The repository currently contains versioned Flyway migrations through **V31**.

Migrations are distributed across:

- `src/main/resources/db/core-migration/`
- `src/main/resources/db/postgresql-migration/`

The highest migration file in Git is not proof of the highest migration successfully applied to production.

Before every production migration, inspect the production `flyway_schema_history` table and record the actual installed version.

Do not describe production as being at V31 unless the production database confirms it.

### 2.2 Production details requiring independent verification

Before relying on this runbook for a release, confirm:

- The deployment branch configured in the Render dashboard.
- The exact production database and its connection status.
- The highest successful Flyway migration in production.
- Whether Better Stack monitoring is configured and active.
- The current Supabase backup and recovery facilities available under the account's plan.
- The current production domain and public health endpoint.
- The validity of the production payment credentials and provider configuration.

Do not put secret values or credentials in this document.

## 3. Migration rules

The following rules are mandatory:

1. Every production schema change must use a new Flyway migration.
2. Never edit a migration that has already been applied to production.
3. Never use Flyway `clean` against production.
4. Hibernate production mode must remain `validate`.
5. Flyway is the authority for production schema changes.
6. New migrations must use the next available version in the existing migration sequence.
7. Migration versions must be unique across both configured Flyway locations.
8. Review migration compatibility with the currently deployed application.
9. Destructive changes require an explicit recovery plan and approval before deployment.
10. Never delete production data simply to make a migration pass.

Prefer backward-compatible migrations where practical. For changes requiring multiple releases, consider an expand-and-contract approach:

1. Add the new schema structure without removing the old structure.
2. Deploy application code compatible with both structures.
3. Migrate or backfill existing data.
4. Verify the new behavior.
5. Remove obsolete structures in a later, separately reviewed migration.

## 4. Before deployment

### 4.1 Confirm the release source

1. Confirm the intended release branch and commit.
2. Confirm the deployment branch configured in Render.
3. Confirm the release has been reviewed.
4. Confirm that unrelated documentation or development changes are not included.
5. Confirm the exact commit intended for production.

Do not assume that a particular historical feature branch remains the approved production release branch.

Do not merge or push a branch to `main` merely because an older runbook mentions it.

Never force-push production `main`.

### 4.2 Review and test the application

From the repository root, run:

```bash
git status
git diff --check
./mvnw test
```

Require:

- Zero test failures.
- Zero test errors.
- Successful Maven build.

Run relevant focused tests as well as the full suite.

If frontend JavaScript changes, run the applicable syntax checks. For example, if the file exists:

```bash
node --check src/main/resources/static/js/app.js
```

Do not treat successful compilation as proof that the affected user journey works.

### 4.3 Review the migration

Before deployment:

1. Inspect the complete migration SQL.
2. Confirm the next unused version.
3. Check both Flyway migration directories.
4. Confirm that no duplicate version exists.
5. Review foreign keys, indexes, constraints, and data transformations.
6. Identify destructive or potentially expensive operations.
7. Consider locking and execution time on production-sized tables.
8. Confirm application compatibility before and after migration.
9. Test the migration against a representative non-production database.

### 4.4 Inspect production migration history

Use an authorized, read-only database connection to inspect the production Flyway history.

Example SQL:

```sql
SELECT
    installed_rank,
    version,
    description,
    type,
    installed_on,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Confirm:

- The latest successfully installed migration.
- Whether any migration has failed.
- Whether the production history matches the expected release state.
- Whether a migration in the proposed release has already been applied.

Do not assume that the latest version in Git is the current production version.

Do not manually modify `flyway_schema_history` to bypass migration problems.

Record the observed production version in the release record.

## 5. Production backup

A suitable production backup must exist before a high-risk schema change.

### 5.1 Current repository backup scripts

The repository currently provides:

- `scripts/backup-production.ps1`
- `scripts/verify-backup.ps1`

These scripts use PostgreSQL 18 Windows command-line tools located at:

```text
C:\Program Files\PostgreSQL\18\bin\
```

The backup script requires these environment variables:

- `PGHOST`
- `PGPORT`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD`

Credentials must be supplied through the environment, never hardcoded in scripts or committed to Git.

### 5.2 Run the backup from Windows PowerShell

The current scripts are PowerShell scripts. From a Windows PowerShell terminal, run:

```powershell
.\scripts\backup-production.ps1
```

The script creates a custom-format database archive under:

```text
backups/
```

It checks that the archive exists, is non-empty, and can be listed using `pg_restore --list`.

### 5.3 Verify an existing archive

Run:

```powershell
.\scripts\verify-backup.ps1 -BackupPath "C:\path\to\backup.dump"
```

The verification script checks the archive's existence, non-zero size, and readability by `pg_restore --list`.

**Important:** This is archive validation, not a complete database restore test.

A successful archive listing does not prove that every database object and data record can be restored successfully.

### 5.4 Backup acceptance and retention

Before proceeding with a high-risk migration:

- Confirm the backup command completed successfully.
- Confirm the archive is non-empty.
- Confirm archive validation succeeded.
- Confirm the backup is stored securely.
- Confirm that the backup is not staged or committed to Git.
- Preserve the backup until the release is complete and the recovery window has passed.

The `.gitignore` currently excludes:

- `backups/`
- `*.dump`

### 5.5 Restore testing

A full restore test must be performed against a separate, non-production PostgreSQL database.

Never test a restore by overwriting the live production database.

After restoring, verify:

- Database objects and schema.
- Required tables and indexes.
- Migration history.
- Representative records and relationships.
- Application connectivity where appropriate.

Do not claim that a backup is fully restorable until an actual restore has been tested.

The availability of Supabase-managed backups and point-in-time recovery must be confirmed from the current account configuration. Do not assume a particular backup entitlement.

## 6. Deployment procedure

The exact production deployment procedure depends on the deployment branch and settings currently configured in Render.

Before deploying:

1. Confirm the intended commit.
2. Confirm the configured Render deployment branch.
3. Confirm the target production service.
4. Confirm the required environment variables exist.
5. Confirm production database connectivity.
6. Confirm the migration has been reviewed and tested.
7. Confirm the required backup exists.
8. Confirm the recovery plan.

Do not hardcode a historical feature branch into the release procedure.

Do not assume that a local branch is the production deployment branch.

Use the approved repository review and release process.

## 7. During deployment

Monitor the Render deployment and runtime logs.

Confirm:

1. The expected application commit is deployed.
2. The container build completes.
3. Flyway detects the expected migration.
4. The migration completes successfully.
5. Spring Boot starts successfully after migration.
6. Hibernate validates the resulting schema.
7. Database connectivity remains healthy.
8. No critical startup or runtime errors appear.

A successful Docker build alone does not mean the production release succeeded.

Do not begin another deployment while the current migration or release is in an uncertain state.

## 8. Post-deployment verification

### 8.1 Application health

The repository configures `/api/health` as the Render health-check path.

Verify that the production health endpoint returns HTTP 200.

The public hostname must be confirmed against the current Render and domain configuration before using a production URL.

### 8.2 Database

Verify:

- Database connectivity.
- Successful Flyway migration.
- Expected production migration version.
- No unexpected failed migration.
- No Hibernate schema validation errors.
- No unexpected database exceptions.

### 8.3 Application functionality

Perform a focused smoke test of the functionality affected by the release.

Where relevant, verify:

- Host authentication and session behavior.
- Property ownership and access restrictions.
- Booking creation and date validation.
- Availability and conflicting reservation prevention.
- Guest-link access and expiry behavior.
- Payment initialization and server-side payment verification.
- Receipt persistence and retrieval.
- Guest information protection.
- Email notification behavior.
- Relevant administrative operations.

Avoid unnecessary real payment transactions.

Use provider-approved test procedures where available.

### 8.4 Monitoring and logs

Inspect:

- Render deployment logs.
- Application runtime logs.
- Database errors.
- Payment processing errors.
- Notification delivery failures.
- Authentication and security errors.

Better Stack may be used if its production monitor is confirmed to be active.

Do not treat an assumed monitoring configuration as verified monitoring coverage.

A successful email API request is not proof that the recipient received the email.

## 9. Failed migration procedure

If a production migration fails:

1. Stop further deployments.
2. Record the failed release and migration version.
3. Inspect the Render deployment and application logs.
4. Inspect `flyway_schema_history`.
5. Inspect the actual production schema.
6. Determine whether the migration was transactional.
7. Determine whether any part of the migration was applied.
8. Preserve the production backup.
9. Assess the effect on existing application functionality.
10. Prepare an explicit recovery or repair plan.

Do not:

- Edit an already-applied migration blindly.
- Run Flyway `clean`.
- Delete production data to make a migration pass.
- Manually mark a migration successful without proving that the schema matches its intended result.
- Assume that a failed migration left the database unchanged.

If a transactional migration rolled back completely, a corrected new migration version may be appropriate.

If a migration partially applied changes, inspect the actual database state and design a repair migration based on that state.

Any manual intervention in migration history requires an explicit, reviewed recovery procedure and evidence that the resulting schema is correct.

## 10. Application failure after a successful migration

A successful migration and a failed application deployment are separate problems.

If the database migration succeeded but the application fails:

1. Identify the application failure.
2. Inspect startup and runtime logs.
3. Determine whether the previous application version is compatible with the new schema.
4. Prefer a forward application fix when appropriate.
5. Roll back application code only when compatibility has been established.

Do not assume that reverting application code reverses a database migration.

Do not automatically reverse a successful production migration.

If database recovery is necessary, use a specifically designed compensating migration or an approved database restoration procedure.

## 11. Security requirements

- Never place database credentials in documentation.
- Never commit `.env` files, database dumps, passwords, API keys, or tokens.
- Keep production secrets in the hosting provider's secure environment configuration.
- Do not expose database credentials in logs or terminal output.
- Do not share production connection strings in issue reports or screenshots.
- Do not log complete guest access tokens or sensitive payment information.
- Restrict production database access to authorized personnel.
- Use an appropriately scoped database account for operational tasks.

## 12. Release record

Record the following information for each production database release:

| Field | Required information |
|---|---|
| Release date | Date and time of deployment |
| Application commit | Exact deployed Git commit |
| Release source | Approved branch or release reference |
| Previous production migration | Version before deployment |
| New production migration | Version applied |
| Migration result | Success or failure |
| Backup | Backup location and archive validation result |
| Restore test | Whether a full restore was tested |
| Application health | Health-check result |
| Smoke test | Relevant functionality verified |
| Recovery notes | Any issues or corrective actions |

Do not record secret values or database credentials.

## 13. Production release checklist

### Before deployment

- [ ] Correct release source and commit confirmed.
- [ ] Render deployment branch confirmed.
- [ ] Relevant tests completed.
- [ ] Full Maven test suite passes.
- [ ] `git diff --check` passes.
- [ ] Migration SQL reviewed.
- [ ] Migration version checked across both migration locations.
- [ ] Production Flyway history inspected.
- [ ] Migration tested against a representative non-production database.
- [ ] Backup created and archive validation succeeds.
- [ ] Recovery plan reviewed.
- [ ] Production configuration checked.

### During deployment

- [ ] Expected application commit deployed.
- [ ] Container build succeeds.
- [ ] Expected Flyway migration detected.
- [ ] Migration completes successfully.
- [ ] Spring Boot starts successfully.
- [ ] Hibernate schema validation succeeds.
- [ ] No critical runtime errors.

### After deployment

- [ ] Production health endpoint returns HTTP 200.
- [ ] Production Flyway history reflects the expected migration.
- [ ] Database connectivity confirmed.
- [ ] Relevant smoke tests pass.
- [ ] Payment and notification behavior checked where relevant.
- [ ] Render logs reviewed.
- [ ] Monitoring checked where configured.
- [ ] Release record completed.
- [ ] Backup retained through the recovery window.

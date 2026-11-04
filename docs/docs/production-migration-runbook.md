# Hostvero Production Database Migration Runbook

## 1. Purpose

This runbook defines the procedure for safely preparing, reviewing, applying, and recovering PostgreSQL database migrations in Hostvero production.

Database migrations can affect bookings, guest information, payments, receipts, property records, and other critical business data.

A migration must never be treated as a routine code change without assessing its effect on production data.

**This document describes the required procedure. It does not confirm the current production database version, migration status, backup status, or infrastructure configuration. These must be verified before any production operation.**

## 2. Core Rules

The following rules are mandatory:

1. Never assume the production database is at the latest migration version in GitHub.
2. Never edit a Flyway migration that has already been applied to a shared or production database.
3. Always inspect the existing migration history before planning a new migration.
4. Always create a new sequential migration for a new schema change.
5. Never apply an unreviewed migration to production.
6. Confirm a recent backup before a high-risk migration.
7. Test migrations outside production first.
8. Never use production as a development or experimentation environment.
9. Do not perform destructive database operations without explicit approval.
10. Verify the database after every production migration.

## 3. Migration Technology

Hostvero uses:

- PostgreSQL.
- Flyway.
- Spring Boot.
- Spring Data JPA.
- Hibernate.
- Spring Data repositories.

Flyway is responsible for managing versioned database schema migrations.

Do not introduce another migration framework without explicit approval.

## 4. Sources of Truth

Before planning a production migration, inspect:

1. Migration files in the repository.
2. Current JPA entities.
3. Repository methods and database queries.
4. Application database configuration.
5. Production Flyway migration history.
6. Relevant database constraints and indexes.
7. Existing production data affected by the migration.

The production database's actual migration history must be checked directly.

The existence of a migration file in the repository does not prove that it has been applied to production.

## 5. Step 1 — Inspect Repository Migrations

Before creating a migration:

1. Open the repository's Flyway migration directory.
2. List the existing migration files.
3. Review their version numbers.
4. Inspect the most recent migrations.
5. Identify migrations related to the affected tables or columns.
6. Check whether the proposed change already exists.

Follow the naming convention already established in the repository.

Do not invent a new migration naming convention without checking existing files.

Do not assume that the highest migration number in GitHub is the production version.

## 6. Step 2 — Verify Production Migration History

Before preparing a production migration, inspect the production database's `flyway_schema_history` table.

Use an authorized database connection and a read-only inspection wherever possible.

The following query can be used to inspect migration history if the standard Flyway history table exists:

```sql
SELECT
    installed_rank,
    version,
    description,
    type,
    script,
    checksum,
    installed_on,
    execution_time,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

If the history table uses a different configured name, inspect the actual Flyway configuration and use the correct table.

Review:

- Latest successful migration.
- Failed migrations.
- Migration descriptions.
- Applied migration scripts.
- Installation timestamps.
- Execution times.
- Checksum information.

Do not modify the Flyway history table manually to make migration status appear correct.

If a failed migration or checksum mismatch is discovered, stop and investigate before proceeding.

## 7. Step 3 — Understand the Production Schema

Migration history alone is not sufficient.

Inspect the actual production schema for the affected objects.

Confirm:

- Existing tables.
- Existing columns.
- Column types.
- Nullability.
- Default values.
- Primary keys.
- Foreign keys.
- Unique constraints.
- Indexes.
- Existing records.
- Existing data relationships.

Confirm whether the proposed change is compatible with the current production data.

Never assume that production has exactly the same schema as the local development database.

## 8. Step 4 — Review the Proposed Change

Before creating the migration, document:

- Business reason for the change.
- Affected tables and columns.
- Expected data impact.
- Expected application impact.
- Whether existing records require backfilling.
- Whether the migration is destructive.
- Whether the application can run safely during the migration.
- Whether the previous application version remains compatible.

A migration should make the smallest necessary database change.

Avoid combining unrelated schema changes in one migration.

## 9. Step 5 — Migration Safety Standards

### 9.1 Adding Columns

Before adding a column:

- Confirm the correct data type.
- Determine whether existing rows need a value.
- Avoid adding a non-null column without a safe default or backfill strategy.
- Assess the performance impact on large tables.

### 9.2 Changing Columns

Before modifying a column:

- Inspect existing data.
- Identify values that may not fit the new type.
- Check dependent application code.
- Review indexes and constraints.
- Plan a safe transition if required.

### 9.3 Renaming Columns or Tables

Renames can break application versions that still reference the old name.

Consider a staged transition:

1. Add the new structure.
2. Update application code to support the transition.
3. Migrate or synchronize data safely.
4. Verify usage.
5. Remove the old structure in a separately approved migration.

Do not assume a rename is safe simply because the new application version compiles.

### 9.4 Dropping Columns or Tables

Dropping data is destructive.

Before removal:

- Confirm the data is no longer required.
- Check application references.
- Check integrations and reporting.
- Confirm backup availability.
- Obtain explicit approval.
- Prepare a recovery plan.

Never drop production data merely to resolve an application error.

### 9.5 Data Backfills

Backfills must be:

- Based on verified existing data.
- Deterministic where possible.
- Safe to rerun where practical.
- Tested on representative data.
- Monitored for failures.
- Designed to avoid unnecessary locking.

For large tables, consider batching and operational impact.

## 10. Step 6 — Create the Migration

Create a new versioned migration using the repository's established Flyway naming convention.

Requirements:

- Use the next appropriate migration version.
- Use a descriptive migration name.
- Keep the migration focused.
- Use safe SQL.
- Preserve existing data unless removal is explicitly approved.
- Add appropriate constraints and indexes.
- Consider rollback and recovery implications.

Never edit an already-applied migration to change its behavior.

If an earlier migration needs correction, create a new migration that safely corrects the schema.

## 11. Step 7 — Test the Migration

Before production:

1. Apply the migration to an isolated test database.
2. Verify the expected schema changes.
3. Test existing records.
4. Test new application behavior.
5. Test relevant repository queries.
6. Test constraints and relationships.
7. Test failure behavior where appropriate.
8. Run regression tests.
9. Verify application startup.

Where possible, test against a representative copy of the production schema using appropriately anonymized data.

Never copy production personal data into a development environment without an approved security and privacy process.

## 12. Step 8 — Backup Verification

Before a high-risk production migration:

- Confirm that a recent database backup exists.
- Confirm the backup belongs to the correct production database.
- Confirm backup completion.
- Confirm access to the restoration procedure.
- Verify that restoration is technically possible.
- Identify the person responsible for recovery.
- Record the recovery plan.

A backup is not considered reliable merely because a backup job was scheduled.

The backup and restoration process must be tested periodically.

For a destructive or high-risk migration, do not proceed without an adequate recovery strategy.

## 13. Step 9 — Production Approval

Before applying a migration, obtain explicit approval for production execution.

The approval should identify:

- Migration filename.
- Migration version.
- Purpose.
- Affected database objects.
- Expected data impact.
- Test results.
- Backup status.
- Deployment window.
- Recovery procedure.

If the migration contains destructive operations, explicitly identify them in the approval.

Do not infer production approval from approval to implement a feature in development.

## 14. Step 10 — Apply the Migration

Use the application's existing Flyway deployment mechanism.

Do not manually execute production SQL as a substitute for the established migration process unless an explicitly approved recovery procedure requires it.

During migration:

1. Confirm the correct production environment.
2. Confirm the correct database connection.
3. Confirm the intended application version.
4. Monitor migration logs.
5. Check execution results.
6. Stop further deployment if an unexpected error occurs.

Do not rerun a failed migration blindly.

First determine whether the migration partially changed the database or whether Flyway recorded a failed execution.

## 15. Step 11 — Post-Migration Verification

After applying the migration, verify the result.

Check:

- Flyway history.
- Migration success status.
- Expected schema objects.
- Column definitions.
- Constraints.
- Indexes.
- Existing data integrity.
- Foreign-key relationships.
- Application startup.
- Relevant application functionality.

Run relevant tests against the migrated database.

Confirm that the application can read and write the affected data correctly.

For migrations affecting bookings, payments, receipts, or guest access, verify the associated business workflows.

Do not declare success solely because Flyway reports a successful migration.

## 16. Failed Migration Procedure

If a migration fails:

1. Stop further migration or deployment activity.
2. Preserve the migration logs.
3. Inspect `flyway_schema_history`.
4. Determine whether any schema changes were applied.
5. Inspect the actual database schema.
6. Identify whether data was changed or lost.
7. Determine whether the failure is caused by SQL, existing data, permissions, or configuration.
8. Assess the application impact.
9. Prepare a recovery plan.
10. Obtain approval before making corrective production changes.

Do not:

- Delete migration history records.
- Manually change checksums.
- Edit an already-applied migration.
- Run repair commands without understanding their effect.
- Retry destructive SQL blindly.
- Assume the database automatically rolled back every operation.

Some database operations and migration execution circumstances can leave partial effects. Inspect the actual state before taking action.

## 17. Recovery and Rollback

Database recovery must be planned separately from application rollback.

### 17.1 Application Rollback

An application rollback may be appropriate when the previous application version is compatible with the current schema.

Confirm compatibility before restoring the previous version.

### 17.2 Migration Recovery

Flyway versioned migrations are not automatically reversed simply by rolling back application code.

Recovery may require:

- A new corrective migration.
- A carefully designed compensating operation.
- A database restoration.
- An approved manual recovery procedure.

The appropriate approach depends on the actual database state and the amount of data affected.

### 17.3 Database Restoration

If restoration is required:

1. Identify the correct backup.
2. Confirm the target database.
3. Assess the data-loss window.
4. Obtain explicit approval.
5. Follow the verified restoration procedure.
6. Verify schema and data integrity.
7. Verify application compatibility.
8. Confirm critical workflows.
9. Document the incident.

Never restore a backup over production without understanding the impact on transactions recorded after the backup.

## 18. Special Protection for Critical Business Data

The following data must receive additional protection:

### Bookings

Preserve booking history and agreed booking terms.

Do not modify existing booking prices unintentionally.

### Payments

Preserve payment references, amounts, currencies, and transaction history.

Do not fabricate payment states during a migration.

### Receipts

Ensure that receipt records remain available to authorized hosts.

Do not delete receipts because guest links have expired.

### Guest Information

Protect guest personal information and maintain appropriate ownership relationships.

### Guest Access Links

Do not accidentally reactivate expired or revoked links.

Preserve secure token handling and expiration behavior.

## 19. Production Migration Record

Every production migration should have a record containing:

- Date and time.
- Migration filename.
- Migration version.
- Deployed application commit.
- Production database identifier, without exposing credentials.
- Previous migration version.
- Resulting migration version.
- Backup reference.
- Test results.
- Approval reference.
- Execution result.
- Post-migration verification.
- Known issues.
- Recovery notes.

Do not include passwords, credentials, or complete secret values in the record.

## 20. Migration Execution Checklist

### Before Migration

- [ ] Inspect existing migration files.
- [ ] Inspect application entities and repositories.
- [ ] Verify production Flyway history.
- [ ] Inspect the actual production schema.
- [ ] Understand affected data.
- [ ] Review the migration SQL.
- [ ] Test against an isolated database.
- [ ] Run relevant regression tests.
- [ ] Confirm backup availability.
- [ ] Confirm recovery procedure.
- [ ] Obtain production approval.

### During Migration

- [ ] Confirm the correct database.
- [ ] Confirm the correct migration.
- [ ] Monitor execution.
- [ ] Preserve logs.
- [ ] Stop if unexpected behavior occurs.

### After Migration

- [ ] Verify Flyway history.
- [ ] Verify schema changes.
- [ ] Verify data integrity.
- [ ] Verify application startup.
- [ ] Test affected business workflows.
- [ ] Review logs.
- [ ] Record the migration result.

## 21. Current Production Migration Status

The actual production migration version must be recorded only after direct verification.

| Item | Verified value |
|---|---|
| Production database provider | To be verified |
| Flyway history table name | To be verified |
| Latest successful production migration | To be verified |
| Failed migrations | To be verified |
| Latest repository migration | To be verified from source |
| Backup status | To be verified |
| Restoration procedure | To be verified |
| Last production migration date | To be verified |

Do not replace these entries with assumptions.

Update this section after an authorized production inspection.

## 22. Final Principle

Production database migrations must be deliberate, verified, and recoverable.

Inspect the actual database, understand the data, test the migration, confirm backups, obtain approval, and verify the result.

Never sacrifice production data integrity to make a deployment appear successful.

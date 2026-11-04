# Production Deployment Guide

## 1. Purpose

This document defines the standards and procedures for deploying Hostvero to production.

It is intended to reduce deployment risk, protect production data, and ensure that releases are tested and verifiable.

**Important:** This document describes the required deployment process. It does not confirm the current production infrastructure, configuration, or deployment settings.

Before executing a deployment, inspect the actual repository, hosting configuration, environment variables, database configuration, and deployment pipeline.

Never guess production settings.

## 2. Approved Technology

Hostvero's approved application stack includes:

- Java 21.
- Spring Boot.
- Maven.
- Spring Data JPA.
- Hibernate.
- Spring Security.
- PostgreSQL.
- Flyway.
- HTML, CSS, and JavaScript.

The current infrastructure and hosting providers must be verified from the actual deployment configuration.

Do not replace existing infrastructure or introduce new deployment platforms without explicit approval.

## 3. Environments

Development, testing, staging, and production environments should be clearly separated where available.

### Development

Used for:

- Local development.
- Feature implementation.
- Debugging.
- Initial testing.

Development must not depend on production data or production secrets unnecessarily.

### Testing

Used for:

- Automated tests.
- Integration tests.
- Regression testing.
- Database migration testing.
- Security testing.

Test environments must use appropriate test data and isolated credentials.

### Staging

Where available, staging should closely resemble production.

Use it to verify:

- Application startup.
- Database connectivity.
- Migration behavior.
- Authentication.
- Booking workflows.
- Payment integration.
- Guest links.
- Email integration.
- Frontend behavior.

### Production

Production serves real hosts and guests.

Changes to production must follow the release and approval process defined in this document.

Production data must never be used casually for development or testing.

## 4. Configuration and Environment Variables

Application configuration must be environment-specific.

Requirements:

- Keep production credentials outside the source repository.
- Use the hosting provider's secure environment-variable or secret-management facilities.
- Never commit passwords, API keys, private tokens, or database credentials.
- Avoid hardcoding production URLs and credentials.
- Document required configuration variables without including their secret values.
- Validate required configuration during application startup where appropriate.

Before deployment, verify:

- Database connection configuration.
- Application profile.
- Authentication configuration.
- Email provider configuration.
- Payment provider configuration.
- File or image storage configuration.
- External service credentials.
- Allowed frontend origins.
- Logging configuration.

Do not assume a variable exists simply because the application references it. Inspect the actual application configuration and deployment environment.

## 5. Source Control and Release Management

Production deployments must originate from reviewed and approved source code.

Requirements:

1. Work on the appropriate feature or maintenance branch.
2. Avoid committing unrelated changes.
3. Review the complete change set.
4. Run the relevant tests.
5. Merge through the approved process.
6. Identify the exact commit being deployed.
7. Record the release information.
8. Verify the deployment result.

Do not deploy unreviewed experimental changes.

Do not deploy directly from an unfinished working branch.

The deployed commit must be identifiable so that the application version can be traced to its source code.

## 6. Pre-Deployment Checklist

Before starting a production deployment:

### Code

- [ ] Confirm the correct branch and commit.
- [ ] Review all changes.
- [ ] Confirm no unrelated files are included.
- [ ] Check for accidental secrets.
- [ ] Confirm documentation is updated where necessary.

### Build and Tests

- [ ] Run the Maven build.
- [ ] Run relevant automated tests.
- [ ] Verify application packaging.
- [ ] Check for dependency or compilation errors.
- [ ] Review security-sensitive changes.
- [ ] Verify important user journeys.

### Database

- [ ] Inspect the relevant Flyway migrations.
- [ ] Confirm the actual production schema version.
- [ ] Check migration compatibility with existing data.
- [ ] Confirm backup availability.
- [ ] Review destructive operations.
- [ ] Prepare a recovery plan.

### Infrastructure

- [ ] Verify production environment variables.
- [ ] Verify database connectivity.
- [ ] Verify required external services.
- [ ] Verify hosting configuration.
- [ ] Verify domain and HTTPS configuration.
- [ ] Confirm monitoring and logging availability.

Do not proceed if a critical item remains unresolved.

## 7. Build and Packaging

Build the application using the repository's approved Maven configuration.

The build must be performed from the correct project directory.

Verify:

- Java version compatibility.
- Maven dependency resolution.
- Compilation success.
- Test results.
- Packaging success.
- Runtime configuration.

Use the repository's existing build and deployment commands.

Do not invent a new build command or modify the build configuration without inspecting the project first.

Do not treat a successful compilation as proof that the application works correctly.

## 8. Database Deployment

Database changes require particular care.

Flyway must manage schema changes through versioned migrations.

Before applying a production migration:

1. Inspect the migration file.
2. Confirm the migration sequence.
3. Verify the current production `flyway_schema_history`.
4. Confirm whether the migration has already been applied.
5. Review data compatibility.
6. Check for destructive operations.
7. Confirm a recent backup.
8. Test the migration in a representative environment.
9. Prepare a recovery procedure.

Never assume that production is at the latest migration version in GitHub.

Never edit an already-applied migration.

Create a new sequential migration for additional schema changes.

Do not manually modify the production schema to bypass Flyway without an explicitly approved recovery procedure.

## 9. Application Deployment

The exact deployment procedure must follow the hosting provider and configuration currently used by Hostvero.

Before deployment:

- Confirm the target service.
- Confirm the deployment branch or commit.
- Confirm the correct environment.
- Confirm required configuration.
- Confirm database availability.
- Confirm migration behavior.

During deployment:

- Monitor build logs.
- Monitor startup logs.
- Confirm the application starts successfully.
- Check database connectivity.
- Check for migration errors.
- Check external service initialization.

After deployment, verify that the deployed version corresponds to the intended commit.

Do not assume that a deployment succeeded merely because the hosting dashboard reports a build as completed.

## 10. Post-Deployment Verification

Perform verification after every production deployment.

### Application Health

Check:

- Application startup.
- Health endpoint, if configured.
- Database connectivity.
- Application logs.
- Error rates.
- External service connectivity.

### Authentication

Verify:

- Host login.
- Host session behavior.
- Protected page access.
- Unauthorized access handling.

### Property Management

Verify:

- Property retrieval.
- Property creation or editing where relevant.
- Property photograph handling.
- Host ownership restrictions.

### Booking

Verify:

- Booking retrieval.
- Date validation.
- Availability checks.
- Booking creation where safe.
- Prevention of conflicting reservations.

### Payments

Where payment changes are involved, verify using the provider's appropriate test or production verification procedures.

Do not create unnecessary real transactions simply to test a deployment.

### Guest Experience

Verify:

- Guest link access.
- Guest link expiration behavior.
- Guest information protection.
- Guest Stay Page access.
- Relevant booking information.

### Receipts and Notifications

Verify:

- Receipt retrieval.
- Receipt persistence.
- Email notification behavior.
- Delivery failure handling.

A successful email API request must not automatically be treated as proof that the recipient received the email.

## 11. Monitoring and Logging

Production must provide enough operational visibility to identify failures.

Monitor:

- Application availability.
- Application startup failures.
- HTTP error rates.
- Database connectivity.
- Database performance.
- Slow requests.
- Payment processing failures.
- Email delivery failures.
- Unexpected exceptions.
- Security-related failures.

Logs must not expose:

- Passwords.
- Secret keys.
- Complete guest access tokens.
- Sensitive payment information.
- Unnecessary guest personal information.

Use structured logs where appropriate.

Logs should support diagnosis without compromising user privacy.

## 12. Backup and Recovery

Production data is critical.

Before high-risk changes:

- Confirm the backup schedule.
- Confirm a recent successful backup.
- Understand the restoration process.
- Identify the responsible database.
- Review the expected recovery time.
- Identify the expected recovery point.
- Confirm how restored data will be validated.

Backups must be protected against unauthorized access.

A backup is not considered reliable until restoration has been tested.

Do not assume that reverting application code reverses a database migration.

## 13. Rollback and Recovery

Every production release should have a recovery plan appropriate to the change.

### Application Rollback

Where safe, a failed application release may be rolled back to the previous known-good application version.

Before doing so, confirm that the previous application version remains compatible with the current database schema.

### Database Recovery

Database recovery is separate from application rollback.

Never automatically reverse an applied production migration.

A database rollback or restoration must be planned according to the actual migration and data changes.

For destructive or incompatible migrations, obtain explicit approval and use the documented recovery procedure.

### Incident Handling

If a deployment causes a serious production issue:

1. Identify the affected functionality.
2. Assess the impact on hosts and guests.
3. Preserve relevant application, database, and deployment logs.
4. Determine whether the issue affects application code, database migrations, configuration, or external services.
5. Restrict affected functionality where necessary to prevent further damage.
6. Decide whether application rollback or another recovery procedure is appropriate.
7. Confirm database compatibility before rolling back application code.
8. Apply the approved recovery procedure.
9. Verify application health and critical user journeys.
10. Confirm that booking, payment, guest access, and receipt records remain consistent.
11. Communicate relevant service disruptions to affected users where necessary.
12. Document the incident, recovery actions, and preventive improvements.

## 14. Hostvero Deployment Configuration

The repository currently contains a Render deployment configuration.

The configuration defines:

- Hosting service name: `hostvero`.
- Runtime: Docker.
- Spring profile: `prod`.
- Health-check path: `/api/health`.
- Application port: supplied through the `PORT` environment variable, with a default of `8080`.

The application obtains its production database connection through:

- `SUPABASE_DB_JDBC_URL`
- `SUPABASE_DB_USERNAME`
- `SUPABASE_DB_PASSWORD`

Before deployment, confirm that these variables are correctly configured in the hosting environment.

The repository configuration describes the intended deployment settings. It does not independently confirm the current hosting dashboard configuration or successful production connectivity.

Do not change the deployment platform, service identity, or production database configuration without approval.

## 15. Payment Provider Release Controls

Payment providers may operate in different modes. Their configuration must be reviewed independently.

The current repository Render configuration specifies:

- M-Pesa: `mock`.
- Stripe: `mock`.
- Paystack: `live`.

These are repository configuration values, not proof that live payment processing is operational.

Before releasing payment-related changes:

1. Confirm the intended payment mode for each provider.
2. Verify the required production credentials.
3. Confirm webhook configuration and signature verification.
4. Verify transaction amount, currency, and booking association.
5. Confirm duplicate webhook handling.
6. Use provider-supported testing procedures where available.
7. Confirm that failed or pending transactions cannot incorrectly mark bookings as paid.
8. Review payment logs without exposing sensitive credentials or payment information.

Never switch a provider from mock to live mode without explicit approval and appropriate production credentials.

Never assume that changing one provider's configuration changes the mode of other providers.

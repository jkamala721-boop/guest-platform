# Database Documentation

## 1. Purpose

This document defines the database principles, data ownership rules, migration standards, and planned data requirements for Hostvero.

It is intended to help developers understand the database safely before modifying it.

**Important:** This document is not a substitute for inspecting the actual database schema. The existing entities, migrations, constraints, relationships, and production schema must be verified against the repository and database before any database change.

## 2. Database Technology

Hostvero's approved database technology is:

- PostgreSQL
- Spring Data JPA
- Hibernate
- Flyway for database migrations

The current application uses a Java and Spring Boot backend.

Do not introduce another database technology without explicit approval.

## 3. Source of Truth

The following sources must be inspected before making database changes:

1. Current JPA entities.
2. Spring Data repositories.
3. Flyway migration files.
4. Application database configuration.
5. Existing database constraints and indexes.
6. Production `flyway_schema_history`, when production changes are involved.

Do not assume that the latest migration file in the repository has already been applied to production.

The production database migration version must be verified directly before planning a production migration.

Do not invent or document an exact table inventory without inspecting the actual schema.

## 4. Data Ownership and Isolation

Hostvero is a multi-host platform.

Every host-owned resource must have a clear ownership relationship.

Examples include:

- Properties.
- Bookings.
- Guest information.
- Guest access links.
- Payment records.
- Receipts.
- Host notifications.
- Property reviews.
- Host-specific configuration.

The backend must enforce ownership and authorization.

A host must never be able to access or modify another host's private information by changing an identifier in a request.

Ownership must be checked server-side. Hiding information in the frontend is not sufficient.

## 5. Core Data Domains

The following are logical data domains. They do not necessarily represent the exact names of existing database tables.

### 5.1 Identity and Accounts

Responsible for:

- Host accounts.
- Authentication-related information.
- User profiles.
- Account status.
- Roles and permissions.

Authentication credentials and sensitive security information must never be exposed through public APIs.

### 5.2 Properties

Responsible for:

- Property ownership.
- Property descriptions.
- Property location and details.
- Property facilities.
- Property photographs and gallery information.
- Property availability configuration.
- Base pricing information.

Property images should use secure and controlled storage references. Do not store large image binaries directly in ordinary relational records unless there is a documented reason.

### 5.3 Guests

Responsible for:

- Guest registration information.
- Guest contact information.
- Guest details associated with bookings.
- Guest-related records required for the Digital Guest Register.

Collect only the information required for the platform's legitimate purpose.

### 5.4 Bookings

Responsible for:

- Property reservation information.
- Check-in and check-out dates.
- Booking status.
- Guest association.
- Host and property association.
- Pricing breakdown.
- Payment status references.

A booking must retain a clear record of the agreed booking terms.

Changes to a property's current price must not silently rewrite the financial terms of an existing booking.

### 5.5 Availability

Availability must have one authoritative source of truth.

All booking creation, date changes, extensions, and availability checks must use consistent backend availability rules.

The database and backend must prevent conflicting reservations.

Availability checks must not rely exclusively on frontend validation.

Where concurrent booking attempts are possible, use appropriate database transactions, constraints, locking, or other concurrency controls.

### 5.6 Payments and Receipts

Payment records must preserve the relationship between:

- Guest.
- Booking.
- Host.
- Payment provider reference.
- Amount and currency.
- Payment status.
- Relevant timestamps.

Payment success must be verified server-side.

A browser redirect or frontend success message is not sufficient evidence of payment.

Receipts must remain available to the host even after a guest access link expires or is revoked.

Do not delete financial records merely because a temporary guest link has expired.

### 5.7 Guest Links and Stay Access

Guest access links must support appropriate security controls, including:

- Secure token generation.
- Token expiration.
- Revocation.
- Server-side validation.
- Restricted access to the intended booking or stay.

Avoid storing raw access tokens when a secure token-hashing approach is appropriate.

Guest access must not expose unrelated guest, host, or property information.

### 5.8 Notifications

Notification records should support the tracking of relevant communication events where needed.

Examples include:

- Guest registration notifications.
- Host notifications.
- Booking updates.
- Payment-related notifications.
- Receipt delivery.
- Guest review invitations.
- Host platform feedback invitations.

Notification delivery status must not be confused with the actual business event. For example, a failed email must not reverse a successfully verified payment.

## 6. Database Design Standards

All database changes must follow these principles:

- Use clear and consistent naming.
- Use appropriate PostgreSQL data types.
- Define foreign keys for genuine relational dependencies.
- Add indexes for frequently queried fields where justified.
- Apply appropriate uniqueness constraints.
- Use database constraints to protect important invariants.
- Avoid unnecessary duplication of data.
- Avoid storing values that can be reliably derived unless there is a documented performance or historical-record reason.
- Use appropriate timestamp handling.
- Define explicit deletion and retention behavior.
- Preserve financial and audit history where legally and operationally required.

Do not add indexes, constraints, or columns without understanding the existing query patterns and data model.

## 7. Pricing and Financial Data

Hostvero must distinguish between:

1. Base property pricing.
2. Host-specific pricing adjustments.
3. The final agreed booking price.
4. Payment amounts.
5. Refunds or adjustments, where supported.

The final agreed booking price must be preserved as part of the booking's historical record.

Changes to current property pricing must not automatically modify existing confirmed bookings.

Financial calculations must be performed and validated by the backend.

Use appropriate precision for monetary values. Avoid floating-point types for financial calculations.

## 8. Database Migrations

Flyway migrations must be treated as controlled, versioned changes.

Rules:

1. Never edit a migration that has already been applied to a shared or production database.
2. Create a new sequential migration for every new schema change.
3. Inspect existing migration filenames and numbering before creating a migration.
4. Verify the actual production migration history before production deployment.
5. Review generated SQL before applying it.
6. Test migrations against a representative database.
7. Consider existing production data before adding non-null columns or constraints.
8. Plan safe backfills when required.
9. Avoid destructive migrations unless explicitly approved.
10. Document recovery procedures for production changes.

A migration file existing in GitHub does not prove that it has been applied to production.

Never assume the production database is at the latest repository migration version.

## 9. Transactions and Data Integrity

Use database transactions for operations that must succeed or fail together.

Examples include:

- Creating a booking and its required associated records.
- Confirming a reservation after successful payment verification.
- Updating related booking and payment states.
- Processing booking extensions.
- Recording financial adjustments.

Do not create partial business states that can leave a booking

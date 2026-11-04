# Hostvero Testing Standards

## 1. Purpose

These standards define how Hostvero must be tested to protect application reliability, security, data integrity, and business workflows.

Testing must verify actual application behavior rather than merely confirm that code compiles.

All new features, bug fixes, and significant modifications must include appropriate tests.

Existing tests must be preserved unless there is a documented technical reason to change or remove them.

## 2. Testing Principles

- Test observable behavior, not implementation details unnecessarily.
- Prioritize business-critical workflows.
- Test both successful and unsuccessful scenarios.
- Test authorization and ownership boundaries.
- Test invalid, missing, and boundary inputs.
- Keep tests deterministic and repeatable.
- Avoid dependence on external services during ordinary automated tests.
- Never use real production credentials or customer data in automated tests.
- Never claim that a test passed unless it was executed successfully.
- Do not remove a failing test merely to obtain a successful build.
- Do not weaken assertions to make incorrect behavior pass.

## 3. Existing Testing Environment

The currently observed project configuration includes:

- Java 21.
- Spring Boot 4.0.7.
- Maven.
- Spring Boot MVC testing dependencies.
- Spring Security testing support.
- Spring Boot validation testing support.
- Spring Boot actuator testing support.
- H2 database with test scope.
- PostgreSQL runtime database driver.
- Existing integration and unit test classes under `src/test/`.

The existing test suite contains tests covering:

- Authentication and cookies.
- Guest access policies.
- Guest email verification.
- Host lifecycle and onboarding.
- Administration and payout operations.
- Security configuration and hardening.
- Property check-in locations.
- Public marketing pages.
- Payment integrations.
- Notification integrations.
- Encryption and fingerprint services.
- Health endpoints and exception handling.

This section describes the observed configuration and must be updated when the actual testing stack changes.

Do not assume that a testing dependency or build plugin exists without checking `pom.xml`.

## 4. Types of Tests

### 4.1 Unit Tests

Unit tests verify individual classes and business rules in isolation.

Use them for:

- Pricing calculations.
- Booking validation.
- Availability decisions.
- Guest-link expiration calculations.
- Payment state transitions.
- Notification decisions.
- Input validation.
- Fingerprinting and encryption utilities.
- Other isolated business logic.

Unit tests should be fast, deterministic, and independent of production infrastructure.

Mock external dependencies when appropriate.

### 4.2 Integration Tests

Integration tests verify interactions between multiple application components.

Use them for:

- Spring service interactions.
- Repository operations.
- Database persistence.
- Transaction boundaries.
- Controller and service integration.
- Authentication and authorization.
- Booking workflows.
- Payment workflows.
- Notification workflows.

Use the existing Spring Boot testing infrastructure and test profile.

### 4.3 API Tests

API tests must verify:

- HTTP methods and status codes.
- Request validation.
- Response structures.
- Authentication requirements.
- Authorization requirements.
- Resource ownership.
- Invalid and missing parameters.
- Error responses.
- Unexpected or malicious input.

Never test only the successful API response when the endpoint performs a sensitive operation.

### 4.4 Security Tests

Security tests are mandatory for security-sensitive changes.

Verify:

- Unauthenticated access is rejected where authentication is required.
- Authenticated users cannot access another host's resources.
- Guest links cannot expose another guest's information.
- Expired links cannot grant access.
- Revoked links cannot grant access.
- Sequential identifiers cannot be used to bypass authorization.
- Sensitive data is not exposed in responses.
- Public endpoints enforce their intended restrictions.
- Rate limits work where implemented.
- Cookies use appropriate security settings.

Security must be enforced by the backend.

### 4.5 End-to-End Tests

End-to-end tests verify complete user workflows across multiple application components.

Prioritize:

- Host registration and onboarding.
- Host login and session management.
- Property creation and management.
- Guest registration.
- Booking creation.
- Payment confirmation.
- Receipt generation.
- Guest access to the Stay Page.
- Booking extensions.
- Booking-again workflows.
- Host notification delivery.

Do not claim end-to-end coverage merely because individual services have unit tests.

## 5. Critical Hostvero Testing Requirements

### 5.1 Authentication and Host Ownership

Test:

- Successful authentication.
- Invalid credentials.
- Session expiration.
- Cookie behavior.
- Logout and session invalidation.
- Unauthorized access.
- Host resource ownership.
- Attempts to access another host's records.
- Account and onboarding restrictions.

Every protected operation must verify ownership on the backend.

### 5.2 Guest Links and Guest Privacy

Test:

- Valid guest tokens.
- Invalid guest tokens.
- Expired tokens.
- Revoked tokens.
- Token enumeration attempts.
- Unauthorized guest access.
- Guest information isolation.
- Access after the permitted stay period.

Verify that expiration of a guest link does not delete host-owned receipts or other permanent business records.

Guest tokens must not be exposed unnecessarily in application logs.

### 5.3 Booking and Availability

Availability must remain governed by one centralized source of truth.

Test:

- Valid check-in and check-out dates.
- Invalid date ranges.
- Check-out before check-in.
- Unavailable dates.
- Overlapping bookings.
- Concurrent booking attempts.
- Booking extensions.
- Book Again workflows.
- Changes in availability between selection and confirmation.
- Correct handling of booking status transitions.

Test both ordinary conflicts and concurrent operations.

Where concurrent bookings are possible, verify that the database and transaction strategy prevent conflicting confirmed reservations.

### 5.4 Payments

Payment tests are mandatory for changes involving payment processing.

Test:

- Successful payment verification.
- Failed payments.
- Pending payments.
- Invalid payment references.
- Invalid webhook signatures.
- Duplicate webhook delivery.
- Replayed webhook events.
- Payment amount mismatches.
- Currency mismatches.
- Unexpected payment status transitions.
- Database consistency after payment processing.
- Correct receipt generation.

Payment success must be established by trusted server-side verification.

A browser redirect or frontend success message must never independently establish that a payment succeeded.

Webhook processing must be idempotent where appropriate.

Automated tests must use test credentials and controlled fixtures, never production payment credentials.

### 5.5 Receipts and Financial Records

Test:

- Receipt creation after verified payment.
- Correct amounts and currency.
- Correct booking association.
- Correct guest and host association.
- Receipt persistence.
- Host receipt access after guest-link expiration.
- Receipt resend behavior.
- Duplicate payment notifications.
- Protection against unauthorized receipt access.

Financial records must not be silently deleted or altered by unrelated guest-link operations.

### 5.6 Email and Notifications

Test:

- Correct notification triggering.
- Correct recipient selection.
- Correct template selection.
- Invalid recipient handling.
- Provider failures.
- Timeout behavior.
- Retry behavior where implemented.
- Duplicate notification prevention where required.
- Correct handling of unsuccessful delivery.

Separate notification creation, provider acceptance, and confirmed delivery.

A successful API request to an email provider does not automatically prove that an email reached the recipient's inbox.

Use mocked provider responses for ordinary automated tests.

### 5.7 Database and Migrations

Test:

- Entity persistence.
- Relationship integrity.
- Unique constraints.
- Foreign key constraints.
- Transaction rollback.
- Concurrent updates where relevant.
- Null and boundary values.
- Migration compatibility.
- Data integrity after schema changes.

H2 may be used for fast isolated tests.

However, H2 is not a substitute for PostgreSQL.

Changes involving PostgreSQL-specific SQL, constraints, indexes, types, migrations, or transaction behavior must receive appropriate PostgreSQL integration testing before release.

Never run destructive database tests against production.

### 5.8 Encryption and Sensitive Data

Test:

- Successful encryption and decryption.
- Incorrect keys.
- Invalid encrypted values.
- Missing values.
- Tampered ciphertext.
- Protection against accidental plaintext exposure.
- Safe handling of sensitive information in logs.

Tests must not contain actual production secrets or private guest information.

## 6. Test Data and Environment

Use isolated test data.

Rules:

- Never use real production guest records.
- Never use production payment credentials.
- Never send real customer emails from ordinary automated tests.
- Never connect destructive test routines to production databases.
- Use dedicated test profiles and controlled fixtures.
- Clean up test data appropriately.
- Avoid tests that depend on execution order.
- Avoid shared mutable test state.

The existing `application-test.properties` must be inspected before changing test database configuration.

## 7. External Integrations

External services include payment providers, email providers, messaging services, and other approved integrations.

Ordinary automated tests should not require live external services.

Use mocks, stubs, or controlled test environments.

Integration tests against real external services must:

- Be explicitly identified.
- Use test or sandbox credentials.
- Be separated from ordinary test execution where appropriate.
- Avoid production customer data.
- Document external dependencies.
- Report network and provider failures clearly.

Never assume a test class performs a live integration simply because its name contains the word Integration.

## 8. Test Naming and Organization

Follow the existing project package structure.

Use descriptive test names that communicate the behavior being verified.

Examples:

- `BookingAvailabilityServiceTest`
- `GuestLinkExpirationIntegrationTest`
- `PaymentWebhookSecurityTest`
- `HostOwnershipAuthorizationTest`

Prefer test names that describe a specific condition and expected outcome.

Keep unit and integration tests distinguishable through their names and structure.

Do not rename or reorganize the existing suite unnecessarily.

## 9. Running Tests

Run the complete Maven test suite using:

```bash
./mvnw test
```

Run a specific test class using:

```bash
./mvnw -Dtest=ClassName test
```

Run a clean test build using:

```bash
./mvnw clean test
```

Inspect the actual Maven configuration before introducing additional test execution commands or build plugins.

Review the test reports under:

```text
target/surefire-reports/
```

Use the actual report location and build configuration if the project changes its test reporting setup.

## 10. Required Testing Before Completion

Before completing a change:

1. Run the relevant unit tests.
2. Run the relevant integration tests.
3. Test affected API endpoints.
4. Verify authorization and ownership.
5. Test invalid and boundary inputs.
6. Test relevant frontend behavior.
7. Test payment or financial integrity where applicable.
8. Test database behavior where applicable.
9. Run relevant regression tests.
10. Run the complete Maven test suite when appropriate.
11. Review failures and warnings.
12. Report actual results.

The scope of testing must reflect the risk of the change.

Security, payment, booking, and data-integrity changes require stronger verification than ordinary presentation changes.

## 11. Coverage and Quality

Do not introduce arbitrary coverage targets without measuring the existing baseline and reviewing the project's actual test coverage.

Prioritize coverage of:

- Payment verification.
- Booking availability.
- Authorization and ownership.
- Guest-link access.
- Sensitive data handling.
- Financial calculations.
- Database transactions.
- Critical host and guest workflows.

Coverage percentages alone do not prove correctness.

Tests must contain meaningful assertions and verify expected behavior.

## 12. Failure Handling

When a test fails:

- Identify the failing test.
- Inspect the error and stack trace.
- Determine whether the failure is caused by application behavior, test setup, environment, or an external dependency.
- Fix the underlying issue where appropriate.
- Rerun the affected test.
- Run relevant regression tests.

Never hide failures by disabling tests, weakening assertions, or silently ignoring exceptions.

If a test cannot run, report the reason.

## 13. Test Reporting

Every completed engineering task must report:

1. Tests executed.
2. Tests passed.
3. Tests failed.
4. Tests skipped.
5. Build result.
6. Any external services required.
7. Any untested critical behavior.
8. Remaining risks.

Distinguish between:

- Tests written.
- Tests executed.
- Tests passed.
- Functionality verified manually.

These are not interchangeable.

## 14. Final Principle

Hostvero handles guest information, accommodation bookings, payments, and operational records.

Testing must protect the correctness and security of these operations.

A successful compilation is not proof of correctness.

A successful unit test is not proof that an entire workflow works.

A feature is complete only when its intended behavior has been verified through appropriate tests and relevant regression checks.

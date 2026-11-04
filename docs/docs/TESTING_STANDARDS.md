# Hostvero Testing and Verification Standards

## 1. Purpose

Every modification must be tested before it is considered complete.

A successful compilation alone does not prove that a feature works.

Testing must verify:
- Expected functionality.
- Invalid input handling.
- Authorization.
- Data integrity.
- Security.
- Existing functionality.
- Failure scenarios.

## 2. Before Making Changes

1. Inspect existing tests.
2. Identify affected components.
3. Understand existing expected behavior.
4. Record current test failures.
5. Identify required new tests.
6. Avoid changing tests merely to make incorrect implementation pass.

## 3. Unit Tests

Test individual methods and business rules.

Examples:
- Booking amount calculation.
- Availability calculation.
- Guest-link expiration.
- Payment state transitions.
- Review eligibility.
- Rating validation.
- Notification scheduling.

Tests should cover normal, invalid, and boundary conditions.

## 4. Integration Tests

Test interactions between components.

Examples:
- Controller to service.
- Service to repository.
- Booking creation and availability.
- Payment verification and booking confirmation.
- Guest-link access and expiration.
- Review submission and booking validation.

Use the project's existing test infrastructure.

Do not assume that a mocked unit test proves a real external integration works.

## 5. API Tests

For affected endpoints, verify:
- Correct HTTP status.
- Valid request handling.
- Invalid request handling.
- Response structure.
- Authorization.
- Resource ownership.
- Error responses.
- Duplicate request handling.

## 6. Security Tests

Test:
- Unauthenticated access.
- Unauthorized host access.
- Cross-host resource access.
- Invalid guest tokens.
- Expired guest tokens.
- Revoked guest tokens.
- Invalid payment callbacks.
- Duplicate payment callbacks.
- Invalid review submissions.
- Rate limiting where configured.
- Sensitive information exposure.

Never rely only on frontend behavior to prove security.

## 7. Database Tests

For schema changes:
- Verify migration execution.
- Verify constraints.
- Verify relationships.
- Verify indexes where relevant.
- Verify existing records remain valid.
- Verify rollback and recovery considerations.
- Verify that no applied migration was modified.

Never test destructive migrations against production.

## 8. Frontend Tests

For affected pages and workflows:
- Verify rendering.
- Verify navigation.
- Verify form validation.
- Verify loading states.
- Verify success states.
- Verify error states.
- Verify empty states.
- Verify responsive layouts.
- Verify API error handling.
- Verify accessibility of important controls.

## 9. End-to-End User Journeys

Test complete workflows when affected.

Examples:

Host:
1. Sign in.
2. Create or select a property.
3. Create a booking.
4. Send a guest link.
5. View booking status.
6. View payment and receipt information.

Guest:
1. Open a valid guest link.
2. View property information.
3. Complete registration.
4. Select dates where supported.
5. Complete payment.
6. Access stay information.
7. Complete checkout.

Test invalid and expired links as well.

## 10. Regression Testing

A new feature must not silently break existing features.

After changes, test all affected dependencies and workflows.

Pay particular attention to:
- Authentication.
- Property management.
- Guest registration.
- Booking creation.
- Availability.
- Payments.
- Receipts.
- Guest links.
- Notifications.
- Dashboard.
- Host authorization.

## 11. Required Build Checks

Use the project's actual Maven wrapper and configured checks.

At minimum, run the relevant test suite.

When appropriate, run:

`./mvnw test`

On Windows PowerShell:

`.\mvnw.cmd test`

Also run configured:
- Formatting checks.
- Static analysis.
- JavaScript syntax checks.
- Frontend linting.
- Database migration validation.

Do not report a command as successful unless it was actually executed.

## 12. Test Evidence

Every completed task must report:

- Tests executed.
- Test commands.
- Number of tests passed.
- Number of tests failed.
- Number of errors.
- Tests skipped.
- Known limitations.
- Tests not executed and why.

Do not hide failed tests.

Do not remove meaningful tests to obtain a green build.

## 13. Production Verification

Before a production release:
- Review the code diff.
- Confirm the build.
- Confirm migrations.
- Confirm backups.
- Confirm production environment variables.
- Confirm payment and email configuration.
- Confirm health endpoint.
- Confirm critical user journeys.
- Inspect deployment logs.
- Verify monitoring.

A successful deployment build is not equivalent to a successful production release.

## 14. Final Standard

A feature is complete only when its expected behavior has been verified and relevant regression checks pass.

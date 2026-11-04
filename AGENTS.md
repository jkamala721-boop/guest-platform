# Hostvero — Repository Engineering Instructions

## 1. Project Identity

Project: Hostvero

Hostvero is a web-based hospitality management platform for:
- Airbnb hosts
- Short-term rental operators
- Serviced apartments
- Guesthouses
- Small hotels

Core purpose:
Help hosts manage properties, guests, bookings, payments, receipts, guest communication, and the complete guest stay journey.

Hostvero is host-oriented. Guests interact through secure web links and do not need to install an application or create platform accounts.

## 2. Mandatory Instructions Before Every Task

Before modifying anything:

1. Read this file completely.
2. Read the relevant documents in `docs/`.
3. Inspect the existing implementation.
4. Identify the affected files, classes, services, APIs, database entities, and frontend components.
5. Understand the current behavior.
6. Check existing tests and dependencies.
7. Identify security, privacy, payment, and data-integrity implications.
8. Compare the implementation with the relevant documentation.

Never assume documentation is accurate without checking the actual implementation.

If documentation and implementation disagree:
- Identify the discrepancy.
- Determine the actual current behavior.
- Explain the discrepancy.
- Propose the appropriate documentation correction.
- Do not silently introduce a breaking change.

For significant changes involving architecture, database structure, security, authentication, payments, or external integrations, present an implementation plan and wait for approval.

## 3. Technology Stack

The approved existing stack is:

Backend:
- Java 21
- Spring Boot
- Maven
- Spring Data JPA
- Hibernate
- Spring Security

Database:
- PostgreSQL
- Supabase
- Flyway migrations

Frontend:
- HTML
- CSS
- JavaScript

Infrastructure:
- Git
- GitHub
- Render
- Existing approved integrations

Do not introduce another framework, programming language, database, or infrastructure provider without explicit approval.

Do not introduce React, Node.js, Python, Firebase, MongoDB, or another replacement stack without approval.

Use existing dependencies and utilities whenever appropriate.

## 4. Product Rules

The following are core product decisions:

- Hosts manage properties and bookings.
- Guests do not create Hostvero accounts.
- Guests do not need to install an application.
- Guests access their stay through secure temporary web links.
- The Digital Guest Register is a core feature.
- Guest information must remain protected.
- The same guest link may transition from registration/payment to the active Stay Page.
- Guest links expire according to the approved stay-expiration rules.
- Receipts remain accessible to the host after guest-link expiration.
- Hosts can resend receipts.
- Future bookings follow the approved reminder and payment workflow.
- Availability must use one centralized source of truth.
- Extend Stay must verify availability before being offered.
- Book Again must display only valid available dates.
- Payment success must be verified server-side.
- Never trust browser-only payment confirmation.
- Do not build guest accounts or a consumer guest marketplace.

The revised booking experience is intended to let guests select their check-in and check-out dates through the guest-facing property page. Verify the existing implementation and approved product specification before changing the workflow.

## 5. Architecture Rules

Respect the existing architecture.

- Keep controllers focused on HTTP handling.
- Keep business logic in appropriate services.
- Keep repositories focused on persistence.
- Use DTOs for API request and response boundaries where appropriate.
- Keep frontend presentation separate from business logic.
- Reuse existing services instead of duplicating business rules.
- Keep availability logic centralized.
- Keep payment providers behind appropriate abstractions.
- Use dependency injection consistently.
- Avoid circular dependencies.
- Avoid unnecessarily large classes and methods.

Do not rewrite working components without a clear technical reason.

Do not perform unrelated refactoring during a feature task.

## 6. Mandatory Code Quality Rules

Follow `docs/CODING_STANDARDS.md`.

At minimum:

- Use consistent indentation.
- Respect `.editorconfig`.
- Keep code readable and properly formatted.
- Avoid unnecessarily long lines.
- Break complex expressions into readable blocks.
- Use descriptive names.
- Keep methods focused on one responsibility.
- Avoid deeply nested conditions.
- Avoid duplicate code.
- Avoid unexplained constants and magic numbers.
- Remove unused imports and dead code.
- Handle exceptions explicitly.
- Do not suppress errors to make tests pass.
- Do not use unreadable one-line implementations for complex logic.

Readable code is more important than minimizing line count.

## 7. Database Rules

Follow `docs/DATABASE.md`.

- Inspect existing entities and relationships before changing the schema.
- Use Flyway for schema changes.
- Never modify an already-applied production migration.
- Create a new versioned migration for an approved schema change.
- Never run destructive database operations without explicit approval.
- Preserve existing production records.
- Use appropriate constraints and indexes.
- Consider transaction boundaries.
- Check migration compatibility with the deployed application.
- Never expose database credentials.
- Never assume a database entity exists because it appears in a proposed specification.

## 8. Security Rules

Follow `docs/SECURITY.md`.

- Enforce authorization on the backend.
- Verify resource ownership on every protected operation.
- Validate all user input.
- Protect guest tokens and public links.
- Never use sequential IDs as authorization credentials.
- Never expose passwords, secrets, tokens, or sensitive guest information.
- Use secure password hashing.
- Protect payment callbacks and webhooks.
- Apply idempotency where appropriate.
- Avoid logging sensitive information.
- Protect against abuse, enumeration, and unauthorized access.
- Never rely exclusively on frontend restrictions for security.

Security-sensitive changes require explicit tests.

## 9. Frontend Rules

Follow `docs/FRONTEND.md` and `docs/CODING_STANDARDS.md`.

- Maintain mobile-first responsive behavior.
- Use existing design tokens and components where appropriate.
- Keep HTML semantic and accessible.
- Keep JavaScript modular and readable.
- Avoid placing large application logic inside HTML.
- Avoid duplicated event handlers.
- Provide loading, success, empty, and error states.
- Validate user input on the frontend and backend.
- Never place secrets or privileged credentials in browser code.
- Do not expose protected data in static frontend files.

## 10. Testing Requirements

Follow `docs/TESTING_STANDARDS.md`.

Before considering a change complete:

1. Run relevant unit tests.
2. Run relevant integration tests.
3. Test affected API endpoints.
4. Test authorization and ownership.
5. Test invalid and boundary inputs.
6. Test relevant frontend workflows.
7. Run regression tests for affected existing functionality.
8. Run the full Maven test suite when appropriate.
9. Run formatting and static checks.
10. Report actual results.

Never claim a test passed unless it was executed and passed.

If a test cannot run, explain why. Never conceal a failure.

## 11. Documentation Requirements

The following documents are authoritative references:

- `docs/PRODUCT_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/DATABASE.md`
- `docs/API_SPEC.md`
- `docs/FRONTEND.md`
- `docs/SECURITY.md`
- `docs/ROADMAP.md`
- `docs/PRODUCTION_DEPLOYMENT.md`
- `docs/production-migration-runbook.md`
- `docs/CODING_STANDARDS.md`
- `docs/TESTING_STANDARDS.md`

When a change modifies documented behavior:
- Update the relevant document.
- Keep implementation and documentation consistent.
- Distinguish implemented functionality from planned functionality.
- Record important changes in `docs/CHANGELOG.md` when appropriate.

Do not invent endpoints, database fields, integrations, or features.

## 12. Scope Control

Only implement the task that has been approved.

Do not:
- Add unrelated features.
- Introduce unapproved AI functionality.
- Change payment calculations without approval.
- Change database relationships unnecessarily.
- Replace existing integrations without approval.
- Perform broad refactoring without approval.
- Remove existing features because they appear unnecessary.

If a better approach is identified, explain it before expanding the scope.

## 13. Git and Deployment Safety

- Work on the assigned development branch.
- Do not directly modify production `main` unless explicitly instructed.
- Do not force-push.
- Do not commit secrets, credentials, backups, or environment files.
- Review the Git diff before committing.
- Do not deploy unverified changes.
- Never perform production database operations without explicit authorization.

## 14. Required Completion Report

After every task, report:

1. What was changed.
2. Why it was changed.
3. Files created.
4. Files modified.
5. Database changes, if any.
6. Tests actually executed.
7. Test results.
8. Security implications.
9. Known limitations.
10. Remaining work.

A task is not complete merely because code was generated or the application compiles.

It is complete only when the intended behavior is verified and relevant regression checks pass.

## 15. Final Principle

Hostvero must remain understandable, maintainable, secure, and reliable.

Prefer simple, well-structured, testable code over rushed implementations.

Never sacrifice maintainability or security for development speed.

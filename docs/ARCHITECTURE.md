# Hostvero System Architecture

## 1. Purpose

This document describes the architectural principles, system boundaries, and technical organization of Hostvero.

It is a reference for developers and AI coding assistants.

Important:
This document defines the intended architecture and constraints. It does not prove that every described component already exists.

Before implementing or modifying a component, inspect the actual repository and verify the current implementation.

Do not introduce a new architectural pattern or framework without approval.

---

## 2. Current Technology Stack

The existing project uses:

### Backend
- Java 21
- Spring Boot
- Maven
- Spring Data JPA
- Hibernate
- Spring Security

### Database
- PostgreSQL
- Supabase
- Flyway migrations

### Frontend
- HTML
- CSS
- JavaScript

### Infrastructure
- GitHub
- Render
- Existing configured external services

Do not replace these technologies without explicit approval.

---

## 3. High-Level System Architecture

Hostvero follows a web application architecture.

The intended request flow is:

Browser
    |
    v
Frontend
    |
    v
Backend API
    |
    v
Controllers
    |
    v
Services
    |
    v
Repositories
    |
    v
PostgreSQL Database

External services are accessed through appropriate backend integrations.

Examples include:
- Payment providers.
- Email delivery providers.
- File or image storage.
- AI providers in future approved development.

The browser must never directly access privileged backend credentials or perform trusted financial operations.

---

## 4. Architectural Responsibilities

### 4.1 Frontend

Responsible for:

- Displaying pages.
- Collecting user input.
- Form validation for usability.
- Communicating with backend APIs.
- Showing loading, success, and error states.
- Displaying host dashboards.
- Displaying guest-facing pages.
- Managing appropriate client-side navigation.

The frontend must not be the authority for:

- Payment verification.
- Booking availability.
- Booking price calculations.
- Authorization.
- Guest-link validity.
- Resource ownership.

These must be enforced by the backend.

### 4.2 Controllers

Controllers should:

- Receive HTTP requests.
- Validate request structures.
- Apply appropriate authentication requirements.
- Delegate business operations to services.
- Return appropriate response structures and status codes.

Controllers should not contain complex business rules or direct database orchestration.

### 4.3 Services

Services contain business logic.

They should:

- Enforce business rules.
- Coordinate repositories.
- Coordinate external integrations.
- Manage transaction boundaries.
- Validate state transitions.
- Protect business invariants.

Examples of potential service responsibilities:

- Property management.
- Booking management.
- Availability.
- Guest registration.
- Guest-link management.
- Payment processing.
- Receipt generation.
- Notifications.
- Review management.

Use existing services where possible. Do not create duplicate services for the same responsibility.

### 4.4 Repositories

Repositories handle persistence.

They should:

- Read and write database records.
- Use appropriate queries.
- Avoid unrelated business logic.
- Avoid unnecessary database access.
- Support transactional service operations.

### 4.5 DTOs

DTOs define API request and response boundaries.

They should:

- Expose only necessary information.
- Avoid exposing internal database entities.
- Exclude sensitive fields.
- Validate request data where appropriate.
- Remain consistent with API specifications.

---

## 5. Domain Boundaries

Hostvero has several important business domains.

These are logical responsibilities, not instructions to create new packages or services blindly.

### 5.1 Identity and Authentication

Responsible for:

- Host registration.
- Login.
- Password management.
- Authentication.
- Session management.
- Authorization.

### 5.2 Property Management

Responsible for:

- Property creation.
- Property editing.
- Property details.
- Property photographs.
- Property pricing.
- Property configuration.
- Property ownership.

### 5.3 Guest Management

Responsible for:

- Guest registration.
- Guest information.
- Guest records.
- Guest history.
- Guest-to-booking associations.

Guests do not require Hostvero accounts.

### 5.4 Booking Management

Responsible for:

- Booking creation.
- Booking status.
- Check-in and check-out dates.
- Booking price.
- Booking changes.
- Booking history.
- Checkout.
- Future bookings.

### 5.5 Availability Management

Availability must have one centralized source of truth.

All booking-related operations must use the same availability rules.

These include:

- Guest date selection.
- Booking creation.
- Booking confirmation.
- Extend Stay.
- Book Again.
- Future Host Network referrals.

Avoid implementing separate availability calculations in controllers, frontend code, or unrelated services.

### 5.6 Guest Link Management

Responsible for:

- Secure guest-link generation.
- Token validation.
- Token expiration.
- Token revocation.
- Guest-link state.
- Access restrictions.

Guest links must not rely on predictable identifiers as authorization credentials.

### 5.7 Payments and Financial Records

Responsible for:

- Payment initiation.
- Provider integration.
- Payment verification.
- Payment state transitions.
- Payment records.
- Financial calculations.
- Receipt generation.
- Host payout accounting where implemented.

All trusted payment operations must be verified by the backend.

### 5.8 Notifications

Responsible for:

- Host notifications.
- Guest notifications.
- Booking reminders.
- Payment reminders.
- Checkout reminders.
- Email delivery.
- Notification status where supported.

Notification delivery should be designed to avoid accidental duplicate messages.

### 5.9 Review and Feedback

This is an approved future product domain.

It will eventually contain two separate types of feedback:

1. Property reviews submitted by guests after completed stays.
2. Hostvero platform ratings submitted by eligible users.

These systems must remain logically separate because they evaluate different things.

Property reviews must be associated with eligible completed bookings.

Hostvero platform ratings must be associated with authenticated or otherwise verified platform users.

Do not assume these modules or database entities already exist.

### 5.10 AI Review Intelligence

Future approved domain.

Potential responsibilities:

- Review analysis.
- Sentiment and theme identification.
- Recurring issue detection.
- Improvement recommendations.
- Recommendation prioritization.
- Improvement tracking.
- Adaptive guest feedback questions.

AI must not directly modify property settings, prices, bookings, or financial records without an explicitly approved and securely controlled workflow.

AI recommendations must be traceable to the feedback evidence that produced them.

### 5.11 Hostvero Community

Future approved domain.

It has two distinct components:

A. Host Network:
- Host verification.
- Property discovery.
- Availability matching.
- Host-to-host referrals.
- Referral acceptance.
- Referral history.

B. Host Education Community:
- Hosting guides.
- Educational resources.
- Discussions.
- Knowledge sharing.
- Best practices.

The community must not expose private guest records, payment records, or another host's private booking information.

Do not build these modules until their roadmap dependencies are satisfied.

---

## 6. Core Booking Architecture

The intended booking flow is:

Host selects property
    |
    v
Host configures booking offer
    |
    v
Guest receives secure link
    |
    v
Guest views property
    |
    v
Guest selects dates
    |
    v
Backend validates availability
    |
    v
Backend calculates booking amount
    |
    v
Guest completes registration
    |
    v
Payment is initiated
    |
    v
Backend verifies payment
    |
    v
Booking is confirmed
    |
    v
Receipt is generated
    |
    v
Guest accesses Stay Page

The actual sequence must be reconciled with the current implementation before modifying it.

### Booking invariants

- Booking dates must be valid.
- Check-out must be after check-in.
- Property availability must be checked server-side.
- Conflicting bookings must be prevented.
- Financial amounts must be calculated or verified server-side.
- Payment success must be independently verified.
- Duplicate requests must not create duplicate financial transactions.
- Booking and payment states must remain consistent.

---

## 7. Centralized Availability Architecture

Availability is a critical shared business capability.

It should not be duplicated across:

- Booking controllers.
- Guest pages.
- Host dashboards.
- Extension workflows.
- Referral workflows.

A centralized availability service or equivalent existing implementation should determine whether a property can accommodate requested dates.

The implementation must account for:

- Confirmed bookings.
- Appropriate pending bookings or reservations.
- Property blocks.
- Booking cancellations.
- Extensions.
- Relevant concurrency conditions.

Do not assume a pending booking blocks dates unless the approved business rules define it.

Concurrency protection must be enforced at the backend and database level where appropriate.

---

## 8. Payment Architecture

The payment architecture must separate:

1. Guest payment initiation.
2. Payment provider interaction.
3. Provider confirmation.
4. Backend verification.
5. Booking state transition.
6. Receipt generation.
7. Financial record persistence.

A frontend success page must never be sufficient evidence of payment.

Payment webhooks must be authenticated and processed safely.

Duplicate provider callbacks must not create duplicate financial effects.

Provider-specific logic should remain isolated from general booking business logic.

---

## 9. Guest Link and Stay Page Architecture

Guests interact through secure links.

The backend is responsible for determining:

- Whether the link exists.
- Whether the token is valid.
- Whether it has expired.
- Whether it has been revoked.
- Which booking it represents.
- Which guest information may be displayed.
- Which Stay Page sections are available.

The frontend must not independently decide that a guest has permission to access protected information.

The same guest journey may transition from property/booking information to registration/payment and then to the active Stay Page, according to the approved product workflow.

---

## 10. Data and Transaction Boundaries

Use database transactions for operations that must succeed or fail together.

Examples:

- Booking creation and availability reservation.
- Payment state update and booking confirmation.
- Receipt record creation.
- Stay extension and associated financial updates.

Avoid partial updates that leave booking, payment, or receipt records inconsistent.

Use database constraints and appropriate locking or concurrency controls where necessary.

Follow DATABASE.md and never modify an already-applied production Flyway migration.

---

## 11. External Integrations

External services must be accessed through appropriate backend components.

Examples:

- Payment providers.
- Email providers.
- File storage.
- Maps or location services.
- AI services in future approved development.

Requirements:

- Keep secrets on the backend.
- Set appropriate timeouts.
- Handle service failures.
- Validate external responses.
- Avoid leaking sensitive data.
- Use retries only where safe.
- Apply idempotency for operations that can produce duplicate effects.
- Monitor external integration failures.

Do not add an external integration without approval.

---

## 12. AI Architecture Principles

AI features are future capabilities.

When approved, AI components should be isolated from core booking and payment logic.

AI should:

- Receive only necessary data.
- Respect host and property ownership.
- Avoid unnecessary personal information.
- Produce auditable recommendations.
- Identify insufficient evidence.
- Support safe failure handling.
- Be monitored for cost and performance.

AI must not be treated as the source of truth for:

- Booking availability.
- Payment status.
- Financial calculations.
- Authorization.
- Guest identity.
- Property ownership.

For review analysis, store or retain sufficient evidence to explain the generated recommendations.

AI output must not silently modify operational data.

---

## 13. Security Architecture

Security is enforced across all layers.

Requirements:

- Authentication.
- Backend authorization.
- Resource ownership checks.
- Input validation.
- Secure guest tokens.
- Protected guest information.
- Payment verification.
- Webhook verification.
- Appropriate rate limiting.
- Secure file access.
- Safe logging.
- Secret management.

Follow SECURITY.md.

Never rely exclusively on frontend controls.

---

## 14. Logging and Monitoring

Important operations should produce useful diagnostic information.

Examples:

- Authentication failures.
- Booking creation.
- Availability conflicts.
- Payment state changes.
- Webhook failures.
- Guest-link validation failures.
- Notification delivery failures.
- Database migration failures.

Do not log:

- Passwords.
- Payment credentials.
- Full guest-link tokens.
- Sensitive identity documents.
- Unnecessary private guest information.

Logs must help diagnose failures without exposing confidential information.

---

## 15. Testing and Quality Assurance

Architecture changes must include appropriate tests.

Test:

- Business rules.
- Authorization.
- Transactions.
- Availability.
- Payment verification.
- Guest-link security.
- Database behavior.
- External integration failures.
- Relevant user journeys.

Follow TESTING_STANDARDS.md.

---

## 16. Architectural Change Rules

Before making a significant architectural change:

1. Inspect the existing architecture.
2. Explain the problem.
3. Identify affected components.
4. Evaluate alternatives.
5. Identify migration and compatibility implications.
6. Present the proposed design.
7. Wait for approval when required.

Do not introduce unnecessary microservices.

Do not duplicate existing business capabilities.

Do not rewrite working functionality without a clear justification.

---

## 17. Final Principle

Hostvero should remain modular, secure, understandable, and maintainable.

The architecture must support future growth without introducing unnecessary complexity today.

Build the reliable core first. Add advanced AI and community capabilities only when their dependencies are ready.

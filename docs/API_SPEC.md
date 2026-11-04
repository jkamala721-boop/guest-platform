# API Specification

## 1. Purpose

This document defines the standards and documentation requirements for Hostvero's backend API.

It serves as a reference for:

- Frontend and backend integration.
- API development.
- Request and response structures.
- Authentication and authorization.
- Validation and error handling.
- Security requirements.
- Future API development.

**Important:** This document does not claim that every described capability or endpoint already exists.

Existing API endpoints must be verified against the actual source code. Proposed endpoints must remain clearly identified as future work until implemented and tested.

## 2. Technology

Hostvero's approved backend technology:

- Java 21.
- Spring Boot.
- Spring Web.
- Spring Security.
- Spring Data JPA.
- Hibernate.
- PostgreSQL.
- Flyway.

The frontend currently uses HTML, CSS, and JavaScript.

Do not introduce another backend framework or API technology without explicit approval.

## 3. API Documentation Status

Every API operation documented in this file must have one of the following statuses:

| Status | Meaning |
|---|---|
| Implemented | Verified in the current source code and supported by tests or other implementation evidence. |
| Partially implemented | Some functionality exists, but the complete behavior is not verified. |
| Planned | Approved for future development but not yet implemented. |
| Proposed | A possible future capability that still requires approval. |

Do not label an endpoint as implemented merely because a frontend page calls it or because it appears in an earlier development plan.

## 4. API Architecture

The intended request flow is:

1. Frontend sends an HTTP request.
2. Spring Security processes applicable security requirements.
3. Controller receives the request.
4. Request validation is performed.
5. Service layer executes business logic.
6. Repository layer accesses the database where necessary.
7. Backend returns an appropriate response.

Controllers should not contain substantial business logic.

Business rules must remain in the service layer.

Database entities should not be exposed directly as public API responses. Use appropriate request and response DTOs.

## 5. API Conventions

### 5.1 HTTP Methods

Use HTTP methods according to their intended purpose:

| Method | Intended use |
|---|---|
| GET | Retrieve information. |
| POST | Create a resource or initiate an operation. |
| PUT | Replace or fully update a resource. |
| PATCH | Partially update a resource. |
| DELETE | Delete or deactivate a resource when permitted. |

Do not use GET requests for operations that modify business data.

### 5.2 Resource Naming

Use consistent resource-oriented naming.

Prefer clear nouns and predictable URL structures.

Do not introduce inconsistent naming conventions between related endpoints.

The existing URL structure must be inspected before changing or introducing API routes.

### 5.3 Request and Response Formats

Use JSON for ordinary API request and response bodies unless a specific integration requires another format.

Responses should:

- Have predictable structures.
- Use appropriate HTTP status codes.
- Avoid exposing internal database entities.
- Avoid returning sensitive information unnecessarily.
- Use consistent date and time representations.
- Represent monetary values accurately.

### 5.4 Validation

Validate incoming requests on the backend.

Validation must cover relevant fields, including:

- Required values.
- Data types.
- Length limits.
- Valid dates.
- Date ordering.
- Monetary values.
- Resource ownership.
- Business-specific restrictions.

Frontend validation improves usability but does not replace backend validation.

### 5.5 Pagination, Filtering, and Sorting

Endpoints that return potentially large collections should support appropriate pagination.

Requirements:

- Define consistent pagination parameters.
- Apply reasonable default and maximum page sizes.
- Support filtering only through explicitly permitted fields.
- Support sorting only through explicitly permitted fields.
- Enforce authorization before returning collection results.
- Avoid exposing records belonging to another host.
- Ensure pagination does not unintentionally disclose sensitive information.

The exact response structure must follow the application's established API conventions.

## 6. Authentication and Authorization

Private API operations must require appropriate authentication.

The backend must distinguish between:

- Authenticated hosts.
- Guests using authorized guest access.
- Public requests.
- Administrative operations, where applicable.

Every private resource operation must enforce authorization.

A host must not access another host's properties, bookings, guests, receipts, or other private records by changing an identifier.

Guest access must be limited to the intended booking and information.

Do not assume that knowing a resource ID grants permission to access it.

## 7. Core API Domains

The following domains describe Hostvero's logical API responsibilities. They are not a verified list of existing routes.

### 7.1 Identity and Account Management

Responsibilities:

- Host registration.
- Login and authentication.
- Account profile management.
- Account-related security operations.
- Authentication state.

Existing routes and request formats must be extracted from the actual controllers and security configuration.

### 7.2 Property Management

Responsibilities:

- Creating properties.
- Retrieving properties.
- Updating property details.
- Managing property photographs.
- Managing property facilities and descriptions.
- Managing property-specific information.

All host-owned property operations must enforce ownership.

Image uploads must be validated and securely handled.

### 7.3 Guest Management

Responsibilities:

- Guest registration.
- Guest information retrieval where authorized.
- Guest information associated with bookings.
- Digital Guest Register functionality.

Guest personal information must not be exposed through public endpoints without a legitimate and authorized purpose.

### 7.4 Booking Management

Responsibilities:

- Booking creation.
- Booking retrieval.
- Booking updates.
- Booking status management.
- Check-in and check-out information.
- Booking extensions where supported.
- Booking-related pricing information.

The backend must validate the booking dates and property availability.

Changing a property price must not silently change the agreed price of an existing booking.

### 7.5 Availability

Availability is a critical business domain.

All booking-related operations must use the same authoritative backend availability rules.

This includes:

- Checking available dates.
- Creating reservations.
- Updating reservation dates.
- Extending stays.
- Preventing conflicting reservations.

The frontend must never be the final authority for availability.

Concurrent booking attempts must be handled safely.

### 7.6 Guest Access Links

Responsibilities:

- Generating guest access links.
- Validating guest access.
- Handling expiration.
- Revoking access.
- Restricting access to the correct booking.

Guest links must use secure tokens and server-side validation.

Expired or revoked links must not provide access to protected stay information.

A guest link's expiration must not delete the host's booking or receipt records.

### 7.7 Payments

Responsibilities:

- Initiating payment operations.
- Receiving payment provider notifications.
- Verifying payment results.
- Updating payment status.
- Associating payments with bookings.
- Handling supported payment adjustments or refunds.

Payment status must be verified server-side.

Never trust a browser redirect, frontend message, or client-submitted payment status as proof of successful payment.

Payment notifications and webhooks must be authenticated or verified using the payment provider's documented mechanism.

Payment processing must be idempotent where duplicate requests or webhook deliveries are possible.

### 7.7.1 Idempotency and Duplicate Processing

Operations that may be retried or delivered more than once must be designed to prevent unintended duplicate business effects.

This includes:

- Booking creation.
- Payment initiation.
- Payment webhook processing.
- Receipt generation.
- Refund processing, where supported.
- Other financially significant operations.

Where appropriate, use idempotency keys or equivalent server-side safeguards.

The backend must distinguish a legitimate retry from a new operation.

Repeated requests must not create duplicate payments, bookings, receipts, or financial records.

### 7.8 Receipts

Responsibilities:

- Creating receipts after the appropriate verified business event.
- Retrieving receipts for authorized users.
- Resending receipts where supported.

Receipts must remain available to the host even if a guest access link expires.

Receipt information must reflect the actual recorded financial transaction.

### 7.9 Notifications

Responsibilities may include:

- Guest registration notifications.
- Host notifications.
- Booking updates.
- Payment-related notifications.
- Receipt delivery.
- Feedback invitations.

Email delivery failure must not incorrectly reverse a completed booking or verified payment.

Notification delivery status must be tracked separately from the underlying business event.

## 8. API Error Handling

Use appropriate HTTP status codes.

Common examples:

| Status | Meaning |
|---|---|
| 200 | Successful request. |
| 201 | Resource created. |
| 204 | Successful operation with no response body. |
| 400 | Invalid request or validation failure. |
| 401 | Authentication required or invalid. |
| 403 | Authenticated user is not authorized. |
| 404 | Resource not found or intentionally unavailable. |
| 409 | Conflict with current resource or business state. |
| 422 | Semantically invalid request, where this convention is adopted. |
| 429 | Rate limit exceeded. |
| 500 | Unexpected server error. |

Use the conventions already established in the application where possible.

Error responses must not expose:

- Stack traces.
- Database credentials.
- Internal SQL statements.
- Secret tokens.
- Passwords.
- Sensitive configuration.
- Unnecessary personal information.

Errors should provide enough information for the frontend to respond appropriately without revealing internal implementation details.

### 8.1 Error Response Consistency

API errors should follow a consistent response structure.

Where appropriate, an error response should communicate:

- A machine-readable error code.
- A safe human-readable message.
- Field-level validation errors where applicable.
- A request or correlation identifier where available.

Error responses must not expose stack traces, internal implementation details, secrets, or unnecessary personal information.

The exact response structure must remain consistent with existing application conventions unless an approved API change establishes a new standard.

## 9. Security Requirements

All API development must consider:

- Authentication.
- Authorization.
- Host and property ownership.
- Guest token security.
- Input validation.
- SQL injection prevention.
- Cross-site request protections where applicable.
- Rate limiting.
- Secure file uploads.
- Payment webhook verification.
- Sensitive information exposure.
- Logging and audit requirements.

Public endpoints must be deliberately defined.

Do not make private endpoints public simply to simplify frontend integration.

## 10. API Documentation Format

Every verified endpoint should eventually be documented using this structure:

### Endpoint Documentation Template

**Name:** Clear operation name.

**Status:** Implemented / Partially implemented / Planned / Proposed.

**HTTP Method:** Actual HTTP method.

**Route:** Exact verified route.

**Authentication:** Required / Not required / Guest token.

**Authorization:** Required permissions and ownership rules.

**Purpose:** What the operation does.

**Path Parameters:** Parameters included in the route.

**Query Parameters:** Supported query parameters.

**Request Body:** Actual request structure, if applicable.

**Validation:** Required validation rules.

**Success Response:** Actual response structure and status code.

**Error Responses:** Relevant error conditions and status codes.

**Business Rules:** Important backend rules.

**Security Notes:** Relevant security considerations.

**Implementation Evidence:** Controller, service, DTO, and test references.

Do not fill this template with guessed routes or invented JSON structures.

## 11. Future API Requirements

The following capabilities are planned or proposed. They must not be treated as existing API functionality.

### 11.1 Property Reviews

Future functionality may include:

- Submitting a review after an eligible completed stay.
- Retrieving published property reviews.
- Managing review moderation.
- Preventing duplicate reviews for the same eligible booking.

The backend must verify review eligibility.

A guest must not be able to submit a review for an unrelated property or booking.

### 11.2 Hostvero Platform Ratings

Hostvero platform ratings are separate from property reviews.

Future functionality may include:

- A permanent platform feedback page.
- Submitting a star rating.
- Submitting written feedback.
- Managing feedback visibility.
- Obtaining consent before publishing testimonials.

Platform ratings must not be confused with ratings of individual properties.

### 11.3 Feedback Invitations

Future functionality may include:

- Sending platform rating invitations after meaningful product use.
- Sending property review invitations after eligible stays.
- Tracking invitation delivery and submission.
- Preventing accidental repeated invitations.

Invitation eligibility and timing must be enforced by backend rules.

### 11.4 AI Review Analysis

Future functionality may analyze property reviews to identify recurring issues.

Potential capabilities:

- Grouping recurring feedback themes.
- Identifying sentiment.
- Finding repeated operational problems.
- Summarizing supporting evidence.
- Providing host-facing insights.

AI analysis must respect host ownership and data access boundaries.

### 11.5 AI Recommendations

Future functionality may provide practical recommendations based on review evidence.

The API design should distinguish:

- Generated recommendations.
- Supporting evidence.
- Host acknowledgment.
- Host actions.
- Completion status.

AI must not autonomously modify bookings, payments, or financial records.

### 11.6 Adaptive Guest Questions

Future review forms may include optional, neutral questions based on previously identified property improvement areas.

Questions must not reveal another guest's private feedback.

### 11.7 Host Network

Future Host Network functionality may include:

- Finding participating hosts.
- Discovering suitable available properties.
- Sending referral requests.
- Accepting or declining referrals.
- Sharing guest referral information with appropriate consent.

The system must verify host and property ownership.

The initial product direction does not include referral commissions.

Guests must not need Hostvero accounts to use a referred guest booking link.

### 11.8 Host Education and Knowledge Community

A separate future API domain may support:

- Educational guides.
- Host discussions.
- Community content.
- Participation.
- Moderation.

This must remain separate from the Host Network referral workflow.

### 11.9 Host-Branded Email Templates

Future functionality may allow hosts to customize approved email branding, including:

- Business or property name.
- Logo.
- Brand colors.
- Contact information.
- Approved footer content.

Customization must not permit unsafe HTML or unrestricted script injection.

## 12. API Testing Requirements

API changes must include appropriate tests.

Tests should cover:

- Successful requests.
- Invalid requests.
- Missing authentication.
- Insufficient permissions.
- Cross-host access attempts.
- Missing resources.
- Duplicate requests.
- Booking date validation.
- Availability conflicts.
- Payment verification.
- Guest link expiration and revocation.
- Notification failures.
- Relevant integration failures.

Tests must verify both expected behavior and important security boundaries.

Do not claim an endpoint is fully implemented without checking its actual behavior.

## 13. API Change Management

Before changing an existing API:

1. Locate the controller and route.
2. Inspect its service implementation.
3. Inspect request and response DTOs.
4. Identify frontend consumers.
5. Identify external integrations.
6. Review authentication and authorization.
7. Review existing tests.
8. Assess compatibility with existing clients.
9. Update this document.
10. Run relevant tests.

Avoid breaking existing frontend or guest-link workflows without an approved migration plan.

When a breaking change is necessary, document the impact and transition approach.

### 13.1 API Compatibility and Versioning

Existing API behavior must be preserved unless a change is necessary and approved.

Requirements:

- Identify frontend and external consumers before changing an endpoint.
- Avoid removing or renaming fields without assessing compatibility.
- Avoid changing response types unexpectedly.
- Document breaking changes.
- Define a migration approach for affected clients.
- Introduce explicit API versioning when compatibility requirements justify it.

Do not introduce unnecessary versioning infrastructure for every API change.

## 14. API Documentation Checklist

Before marking an API operation as documented:

- [ ] Confirm the route in the actual source code.
- [ ] Confirm the HTTP method.
- [ ] Confirm authentication requirements.
- [ ] Confirm authorization and ownership checks.
- [ ] Inspect request DTOs.
- [ ] Inspect response DTOs.
- [ ] Verify validation rules.
- [ ] Verify error handling.
- [ ] Inspect the service-layer business logic.
- [ ] Review relevant tests.
- [ ] Record the implementation status.
- [ ] Update this document when behavior changes.

## 15. Final Principle

The API is the enforcement boundary for Hostvero's business rules and security.

The frontend may guide users, but the backend must validate every important operation.

Document what exists, distinguish it from what is planned, and never treat an unimplemented endpoint as a working feature.

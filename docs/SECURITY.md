# Security Documentation

## 1. Purpose

This document defines Hostvero's security requirements, security principles, and expectations for development, testing, and deployment.

Hostvero handles:

- Host accounts and authentication.
- Property information.
- Guest personal information.
- Booking records.
- Payment-related information.
- Receipts.
- Temporary guest access links.
- Email notifications.
- Future property reviews and platform feedback.
- Future host-to-host community features.
- Future AI-assisted analysis.

Security must be considered throughout the application lifecycle.

**Important:** This document defines security requirements. It does not certify that the current application satisfies them.

Existing security controls must be inspected and tested before being described as implemented or secure.

## 2. Security Principles

All development must follow these principles:

1. Secure by default.
2. Least-privilege access.
3. Server-side authorization.
4. Defense in depth.
5. Data minimization.
6. Secure handling of credentials and tokens.
7. Explicit trust boundaries.
8. Safe error handling.
9. Auditable critical operations.
10. Security testing before release.

Never assume that a feature is secure because it works under normal usage.

## 3. Application Security Architecture

Hostvero uses:

- Java 21.
- Spring Boot.
- Spring Security.
- Spring Data JPA.
- Hibernate.
- PostgreSQL.
- Flyway.
- HTML, CSS, and JavaScript.

Security enforcement must primarily occur on the backend.

The frontend may improve the user experience, but it must never be treated as the final authorization boundary.

Security-sensitive operations must be validated by the server before they are accepted.

## 4. Authentication

Authentication must reliably establish the identity of the user performing an operation.

Requirements:

- Protect private host operations.
- Use secure password handling where passwords are used.
- Never store plaintext passwords.
- Use appropriate session or token security.
- Handle authentication expiration.
- Protect account recovery flows.
- Avoid leaking whether sensitive account information exists where that creates an enumeration risk.
- Apply appropriate protection against brute-force login attempts.

Authentication configuration must be inspected before making changes.

Do not introduce a new authentication system without approval.

### 4.1 Session and Cookie Security

When cookie-based authentication is used:

- Authentication cookies must use `HttpOnly`.
- Production authentication cookies must use `Secure`.
- Apply an appropriate `SameSite` policy based on the authentication and cross-origin requirements.
- Session expiration must be enforced server-side.
- Logout must invalidate the relevant server-side session or authentication mechanism.
- Credential changes and account recovery must trigger appropriate session invalidation.
- Session identifiers must not be exposed unnecessarily to JavaScript or third-party resources.
- Session fixation risks must be addressed.

Cookie and CSRF configuration must be reviewed together. Do not weaken CSRF protection merely to simplify frontend requests.

### 4.2 Administrative Account Security

Administrative functionality requires stricter access controls.

- Apply least-privilege permissions to administrative accounts.
- Require multi-factor authentication where supported.
- Protect administrative endpoints with server-side authorization.
- Audit sensitive administrative operations.
- Restrict access to production credentials and infrastructure.
- Review administrative account access periodically.
- Remove or disable access when it is no longer required.

## 5. Authorization and Host Isolation

Hostvero is a multi-host platform.

Every private host-owned resource must be protected by server-side ownership checks.

This includes:

- Properties.
- Bookings.
- Guest information.
- Payment records.
- Receipts.
- Property photographs.
- Notifications.
- Host-specific settings.
- Future review and community records.

A host must not be able to access or modify another host's resources by changing an ID in a request.

The backend must verify both authentication and authorization.

Do not rely on:

- Hidden frontend buttons.
- Frontend route restrictions.
- Unpredictable database IDs.
- Client-submitted host IDs.
- Client-submitted ownership information.

The authenticated identity must be used to establish the user's permissions.

## 6. Guest Access Links

Guest access links are a critical security boundary.

They may provide access without a conventional host account, so they must be carefully restricted.

Requirements:

- Use cryptographically secure random tokens.
- Avoid predictable token values.
- Validate tokens server-side.
- Associate each token with the intended booking or stay.
- Enforce expiration.
- Support revocation.
- Prevent access after expiration or revocation.
- Avoid exposing unrelated booking or guest information.
- Avoid storing raw access tokens where secure token hashing is appropriate.
- Do not place sensitive information directly inside the URL.
- Avoid logging complete access tokens.

Guest links must not grant general access to the host's dashboard or unrelated resources.

A valid guest link must provide only the permissions required for its intended purpose.

### 6.1 Token Handling

Tokens must be generated securely.

Token validation must include the relevant status, expiration, and resource association checks.

Token leakage through logs, analytics, browser history, referrer headers, or third-party resources must be considered.

Where appropriate, use restrictive referrer policies and avoid loading unnecessary third-party resources on sensitive guest pages.

### 6.2 Expiration and Revocation

Expired and revoked links must be rejected server-side.

The frontend must display an appropriate message without revealing private information.

Expiration of a guest link must not automatically delete the underlying booking, payment, or receipt records.

## 7. Booking and Availability Security

Booking creation and updates must be validated by the backend.

The system must protect against:

- Unauthorized booking changes.
- Cross-host booking access.
- Invalid check-in and check-out dates.
- Conflicting reservations.
- Price manipulation.
- Duplicate submissions.
- Race conditions during booking creation.

The frontend must never be the authority for availability or booking confirmation.

The backend must validate the requested dates against the authoritative availability rules.

Concurrent booking attempts must be handled safely through appropriate transaction and database controls.

## 8. Payment Security

Payment processing is a high-priority security area.

Requirements:

- Use the payment provider's documented secure integration.
- Keep secret payment credentials on the server.
- Verify payment results server-side.
- Authenticate or verify payment webhooks using the provider's documented mechanism.
- Validate transaction references.
- Validate amounts and currencies.
- Associate payments with the correct booking.
- Prevent duplicate payment processing.
- Handle delayed and repeated webhook delivery.
- Keep payment state changes auditable.
- Do not trust frontend success messages.

A browser redirect to a success page does not prove that payment has been completed.

A booking must not be marked as paid based solely on information submitted by the browser.

Never store card security codes or unnecessary sensitive payment credentials.

Payment data storage must follow the payment provider's requirements and applicable payment security obligations.

## 9. Personal Data Protection

Hostvero processes personal information belonging to hosts and guests.

Collect and retain only the information necessary for legitimate platform operations.

Requirements:

- Protect guest contact information.
- Restrict access to personal data.
- Avoid unnecessary duplication.
- Define retention periods.
- Support appropriate deletion and correction processes.
- Prevent unauthorized disclosure.
- Protect data in transit.
- Use appropriate encryption and infrastructure protections at rest.
- Avoid including personal information in unnecessary logs.

Do not expose guest information through public property pages, reviews, community features, or AI-generated content.

Privacy and retention requirements must be reviewed against applicable laws and contractual obligations.

## 10. File Upload and Image Security

Property image uploads must be treated as untrusted input.

Requirements:

- Validate file type.
- Validate file size.
- Restrict accepted formats.
- Verify content rather than trusting file extensions alone.
- Use safe storage locations.
- Generate safe file names.
- Prevent executable file uploads.
- Prevent path traversal.
- Restrict unauthorized file access.
- Avoid exposing storage credentials.
- Handle image processing safely.

Do not assume that a file is safe because the browser accepts it.

## 10.1 Server-Side Request Forgery Protection

Any backend functionality that retrieves or processes externally supplied URLs must be protected against SSRF.

Requirements:

- Validate URL schemes and destinations.
- Restrict access to internal, private, and link-local network addresses.
- Prevent access to cloud metadata endpoints.
- Apply appropriate outbound network restrictions.
- Validate redirects rather than automatically trusting redirect destinations.
- Apply connection and response-size limits.
- Do not assume that a URL is safe because it was submitted through the frontend.

External URL processing must not provide an attacker with a way to access internal services or infrastructure.

## 11. API Security

All API operations must have clearly defined security requirements.

Security controls should include:

- Authentication where required.
- Authorization and ownership validation.
- Input validation.
- Rate limiting where appropriate.
- Safe error responses.
- Secure handling of tokens.
- Protection against injection.
- Appropriate cross-origin resource sharing configuration.
- CSRF protection where applicable to the authentication mechanism.
- Secure HTTP configuration.

Public endpoints must be deliberately identified.

Do not expose private endpoints merely to make frontend integration easier.

## 12. Browser and Frontend Security

Frontend security requirements include:

- Avoid unsafe HTML injection.
- Use safe output encoding.
- Avoid exposing secrets in JavaScript.
- Protect sensitive pages from unnecessary third-party scripts.
- Handle guest tokens carefully.
- Use secure communication over HTTPS.
- Avoid storing sensitive credentials in ordinary browser storage.
- Use appropriate security headers.
- Prevent accidental disclosure through error messages.

Do not treat frontend validation as a security control.

## 13. Email and Notification Security

Emails may contain booking or guest-related information.

Requirements:

- Do not include unnecessary sensitive information.
- Use secure links for protected information.
- Avoid exposing raw access tokens in logs.
- Validate email template variables.
- Prevent unsafe HTML injection into email templates.
- Handle delivery failures without corrupting business records.
- Avoid sending repeated or misleading notifications.
- Protect email provider credentials.

Email delivery success must not be confused with payment or booking success.

## 14. Logging and Auditability

Security-relevant operations should be auditable.

Examples include:

- Login attempts.
- Authentication failures.
- Authorization failures.
- Guest link creation and revocation.
- Booking changes.
- Payment verification.
- Receipt creation.
- Administrative actions.
- Sensitive account changes.

Logs must not contain:

- Plaintext passwords.
- Secret API keys.
- Complete access tokens.
- Payment card security codes.
- Unnecessary guest personal information.

Use structured logging where appropriate.

Audit records should be protected against unauthorized modification.

## 15. External Services and Secrets

Hostvero may integrate with external services, including:

- Payment providers.
- Email providers.
- Database infrastructure.
- Image storage services.
- Future AI providers.

Requirements:

- Store secrets in appropriate environment or secret-management systems.
- Never commit production secrets to GitHub.
- Never expose private credentials in frontend code.
- Use secure connections.
- Restrict API permissions where possible.
- Rotate compromised credentials.
- Review external service permissions.
- Understand what data is transmitted to each provider.

Do not send unnecessary guest personal information to third-party services.

## 15.1 Encryption Keys and Secret Management

Encryption keys and production secrets must be protected independently of the data they protect.

Requirements:

- Never hard-code encryption keys or production secrets.
- Restrict access to authorized application components and operators.
- Separate production secrets from development and testing environments.
- Define a process for key rotation.
- Define secure handling of compromised or exposed keys.
- Protect backups containing sensitive information.
- Ensure that key rotation does not make existing protected data permanently inaccessible.
- Avoid logging secrets or key material.

Key management must be considered part of the security architecture, not an implementation detail.

## 16. Future Review and Feedback Security

### 16.1 Property Reviews

Future review functionality must prevent:

- Reviews for unrelated bookings.
- Reviews from ineligible guests.
- Duplicate submissions where prohibited.
- Unauthorized review modification.
- Exposure of guest personal information.
- Review manipulation and abuse.

Moderation and publication rules must be enforced by the backend.

### 16.2 Hostvero Platform Ratings

Platform ratings must remain separate from property reviews.

The system must:

- Validate who is permitted to submit feedback.
- Protect user identity and private account information.
- Prevent automated or abusive submissions.
- Obtain appropriate consent before publishing testimonials.
- Provide suitable moderation controls.

Do not selectively request feedback only from users expected to provide positive ratings.

### 16.3 Feedback Invitations

Invitation links must be protected against unauthorized use.

Invitation handling must prevent accidental repeated requests and unauthorized submission.

## 17. Future AI Security and Data Governance

Future AI functionality may analyze guest reviews and recommend property improvements.

AI integration must follow strict data boundaries.

Requirements:

- Send only the information necessary for the requested analysis.
- Avoid unnecessary guest personal information.
- Enforce host and property ownership boundaries before analysis.
- Treat guest reviews as untrusted input.
- Defend against prompt injection and malicious content embedded in reviews.
- Treat AI output as untrusted until validated.
- Do not execute AI-generated instructions or code.
- Do not allow AI to autonomously modify bookings, payments, or financial records.
- Keep original reviews separate from AI-generated analysis.
- Record relevant model and analysis metadata where appropriate.

AI-generated recommendations must not be presented as verified facts without supporting evidence.

AI providers must be evaluated for their data handling, retention, privacy, and security terms before integration.

## 18. Future Host Network Security

The planned Host Network introduces new privacy and authorization risks.

Requirements:

- Verify participating hosts.
- Verify property ownership or authorization.
- Restrict access to host-specific information.
- Prevent unauthorized referral requests.
- Require appropriate consent before sharing guest information.
- Protect referral links.
- Validate referral status transitions.
- Prevent unauthorized changes to another host's referral records.
- Provide appropriate abuse reporting and moderation mechanisms.

A host must not be able to discover or access another host's private guest records.

Guests must not need Hostvero accounts to use referral booking links.

## 19. Future Community Security

The future Host Education and Knowledge Community may introduce user-generated content.

Requirements:

- Validate and safely render user-generated content.
- Prevent script injection.
- Provide moderation and reporting mechanisms.
- Restrict administrative operations.
- Prevent unauthorized content modification.
- Protect user privacy.
- Apply appropriate rate limits.
- Separate public educational content from private host and guest information.

The education community and Host Network must have separate authorization boundaries.

## 20. Security Testing

Security testing must cover both normal and adversarial behavior.

Required areas include:

### Authentication

- Invalid credentials.
- Missing credentials.
- Expired authentication.
- Account recovery.
- Brute-force protection.

### Authorization

- Accessing another host's property.
- Accessing another host's booking.
- Accessing another host's guest information.
- Modifying resources without permission.
- Accessing restricted administrative functions.

### Guest Links

- Invalid tokens.
- Expired tokens.
- Revoked tokens.
- Reused tokens where reuse is prohibited.
- Access to unrelated bookings.
- Token leakage through logs or responses.

### Booking and Availability

- Invalid date ranges.
- Conflicting bookings.
- Concurrent booking attempts.
- Price manipulation.
- Unauthorized booking updates.

### Payments

- Forged payment success.
- Invalid webhook signatures.
- Duplicate webhook delivery.
- Incorrect amounts.
- Incorrect booking associations.
- Delayed payment notifications.

### File Uploads

- Unsupported formats.
- Oversized files.
- Malicious file content.
- Unauthorized access.
- Unsafe file names.

### Data Exposure

- Sensitive information in API responses.
- Sensitive information in logs.
- Cross-host data leakage.
- Guest information exposed through public pages.

Security tests must be included in the normal development and release process.

## 21. Security Incident Response

If a security vulnerability or incident is discovered:

1. Record the issue.
2. Determine the affected functionality and data.
3. Assess whether unauthorized access or disclosure occurred.
4. Restrict the vulnerable functionality where necessary.
5. Revoke compromised credentials or tokens where appropriate.
6. Preserve relevant logs and evidence.
7. Apply and test the fix.
8. Assess whether affected users or authorities must be notified under applicable obligations.
9. Document the incident and corrective actions.

Do not conceal security failures or claim an incident is resolved without verification.

## 22. Security Review Before Production

Before production deployment, review:

- Authentication configuration.
- Authorization and host isolation.
- Guest link security.
- Payment verification.
- Database access.
- Secret management.
- HTTPS configuration.
- File upload security.
- CORS and CSRF configuration.
- Error handling.
- Logging.
- Dependency vulnerabilities.
- Backup and recovery.
- Security test results.

Critical security issues must be resolved before release.

Any accepted residual risk must be documented and explicitly approved.

## 23. Security Change Checklist

Before completing a security-sensitive change:

- [ ] Identify the security boundary.
- [ ] Identify the affected users and data.
- [ ] Inspect the existing implementation.
- [ ] Confirm authentication requirements.
- [ ] Confirm authorization requirements.
- [ ] Validate all untrusted inputs.
- [ ] Consider abuse and misuse cases.
- [ ] Protect sensitive information.
- [ ] Add relevant security tests.
- [ ] Run regression tests.
- [ ] Review logs and error responses.
- [ ] Update documentation.
- [ ] Record remaining risks.

## 24. Final Principle

Hostvero must protect hosts, guests, payments, property information, and business records at every layer.

Security must be enforced by the backend, tested against unauthorized behavior, and reviewed before production changes.

Never claim that Hostvero is secure simply because normal user journeys work.

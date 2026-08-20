# Hostvero Architecture

## 1. Core stack

Backend:
- Java 21
- Spring Boot
- Maven
- Spring Data JPA
- Hibernate

Database:
- PostgreSQL
- Supabase

Frontend:
- HTML
- CSS
- JavaScript

Deployment:
- Render

---

## 2. Backend layering

Use:

Controller
→ Service
→ Repository
→ Entity
→ PostgreSQL

Controllers:
- HTTP concerns
- request validation entry point
- response mapping

Services:
- business rules
- booking logic
- availability logic
- payment workflow
- receipt workflow
- guest-link state logic

Repositories:
- persistence only

Entities:
- database representation

Avoid business logic inside controllers.

---

## 3. Major services

Expected services:

- AuthenticationService
- UserService
- PropertyService
- GuestService
- BookingService
- AvailabilityService
- GuestLinkService
- PaymentService
- ReceiptService
- NotificationService

---

## 4. Payment abstraction

Payment implementation should support multiple providers.

Conceptual structure:

PaymentService
├── MpesaPaymentProvider
└── StripePaymentProvider

The booking system must not depend directly on Stripe-specific or M-Pesa-specific implementation details.

---

## 5. Availability architecture

There must be one AvailabilityService.

It is responsible for:

- checking date/time conflicts
- validating new bookings
- validating extensions
- returning available dates
- supporting future Host Network property matching

Do not duplicate availability queries in unrelated services.

---

## 6. Guest-link lifecycle

GuestLinkService controls:

- token creation
- token lookup
- token validation
- booking association
- expiry
- revocation
- guest page state

Guest link states:

- REGISTRATION_OR_PAYMENT
- STAY_ACTIVE
- EXPIRED
- REVOKED

Do not use sequential IDs as public link identifiers.

---

## 7. Booking lifecycle

Recommended states:

- DRAFT
- PENDING_CONFIRMATION
- PENDING_PAYMENT
- CONFIRMED
- CHECKED_IN
- COMPLETED
- CANCELLED

Exact implementation may be refined if necessary, but it must support:
- future booking without immediate payment
- successful payment
- active stay
- completion
- cancellation

---

## 8. Notification architecture

NotificationService should allow provider replacement.

Development can use:
- mock provider
- email provider

Later:
- WhatsApp provider

Business logic should request notifications through NotificationService instead of calling WhatsApp APIs directly.

---

## 9. Receipt architecture

ReceiptService should:

- generate receipt records
- create downloadable receipt output
- associate receipt with booking/payment
- allow host resend

Do not make receipt persistence dependent on guest-link lifetime.

---

## 10. Deployment architecture

Development:
Spring Boot
→ environment variables
→ Supabase PostgreSQL

Production:
Render
→ environment variables
→ Supabase PostgreSQL

GitHub must never contain production credentials.

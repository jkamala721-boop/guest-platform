Project working name: Hostvero

Purpose:
A host-only guest management platform for Airbnb hosts,
short-term rental hosts, serviced apartments, guesthouses,
and small hotels.

Core objective:
Digital guest registration, booking management, payments,
receipts, check-in information, checkout automation,
extensions, repeat bookings, and later a verified host network.

Technology stack:
- Java 21
- Spring Boot
- Maven
- Spring Data JPA
- Hibernate
- PostgreSQL
- Supabase
- HTML
- CSS
- JavaScript
- Git/GitHub
- Render

Architecture rules:
- Do not introduce React, Node.js, Python, Firebase, MongoDB,
  or another stack without explicit approval.
- Do not rewrite working code unnecessarily.
- Do not implement features outside the approved roadmap phase.
- Do not commit secrets or environment files.
- Run tests after meaningful changes.
- Keep the code production-oriented.

Product rules:
- Guests never create accounts.
- Guests never need an app.
- Guests interact through secure temporary web links.
- Digital Guest Register is a core feature.
- Payments will support M-Pesa and Stripe.
- After successful payment, the same guest link becomes
  the stay/check-in page.
- The guest link expires completely at the end of the booked stay.
- Receipts remain stored for the host after guest-link expiration.
- Hosts can resend receipts to guests.
- Future bookings are not paid immediately.
- Two days before arrival, send a reminder.
- 24 hours before arrival, request confirmation and payment.
- Successful payment unlocks receipt and check-in information.
- One hour before checkout, verify availability first.
- Offer Extend Stay only if the property is actually available.
- Book Again must show only available dates.
- Availability logic must use one centralized source of truth.
- Do not build a guest marketplace.
- Do not build guest accounts or consumer-style guest profiles.

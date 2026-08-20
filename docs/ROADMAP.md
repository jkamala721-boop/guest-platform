# Hostvero Development Roadmap

## Phase 0 — Foundation

Goals:

- inspect existing project
- fix Supabase PostgreSQL configuration
- protect secrets
- verify Spring Boot startup
- verify health endpoint
- verify database connectivity
- add or repair basic tests
- ensure Maven build passes

Do not build product features yet.

---

## Phase 1 — Host and Property

Build:

- authentication
- host account
- host profile
- property entity
- property repository
- property service
- property controller
- property CRUD
- authorization

---

## Phase 2 — Digital Guest Register

Build:

- Guest entity
- Guest repository
- Guest service
- Guest controller
- guest creation
- guest search
- guest filtering
- guest booking history

Do not create guest accounts.

---

## Phase 3 — Booking and Availability

Build:

- Booking entity
- booking states
- Booking repository
- Booking service
- AvailabilityService
- conflict detection
- booking creation
- booking cancellation
- GuestLink entity
- secure token generation

---

## Phase 4 — Payments and Receipts

Build:

- Payment entity
- payment abstraction
- Stripe provider
- M-Pesa provider
- payment callbacks/webhooks
- payment state transitions
- Receipt entity
- receipt generation
- receipt download
- host receipt resend

---

## Phase 5 — Guest Stay Page

Build:

- public guest registration page
- payment state page
- same-link state transition
- paid Stay Page
- receipt download
- check-in information
- guest-link expiration

---

## Phase 6 — Booking Automation

Build:

- scheduler/jobs
- two-day reminder
- 24-hour confirmation/payment request
- payment reminder state
- checkout reminder
- NotificationService

Use mock/email provider if WhatsApp is not connected yet.

---

## Phase 7 — Extend Stay and Book Again

Build:

- pre-check availability before extension offer
- extension calculation
- extension payment
- booking update
- updated receipt handling
- Book Again
- available-date query
- future-booking creation
- future-booking reminder flow

---

## Phase 8 — Dashboard and UI

Build final host UI.

Direction:

- mobile-first
- Airbnb-like familiarity
- original Hostvero branding
- clean cards
- white space
- simple navigation
- large touch targets
- minimal clicks
- fast loading

Screens:

- login
- dashboard
- properties
- new booking
- booking details
- guest register
- guest details
- payments
- receipts
- settings
- guest registration page
- guest payment page
- guest Stay Page
- Extend Stay
- Book Again

---

## Phase 9 — Testing and Hardening

Add:

- unit tests
- integration tests
- authorization tests
- availability conflict tests
- guest-token tests
- token-expiration tests
- Stripe webhook tests
- M-Pesa callback tests
- receipt tests
- extension tests
- Book Again tests
- validation tests

---

## Phase 10 — Production

Prepare:

- Render deployment
- Supabase production configuration
- environment variables
- production secrets
- logging
- monitoring
- backups
- domain
- HTTPS
- error handling
- production database migration process

---

## V1B — Verified Host Network

Build only after V1 is stable.

Features:

- host verification
- property verification
- verified host status
- referral-enabled properties
- host network search
- available-property matching
- referral booking
- temporary guest link
- referral history
- automatic receiving-host notification
- premium subscription gating

Rules:

- host-only
- no guest app
- no public marketplace
- platform takes no referral commission
- Host Network access is premium
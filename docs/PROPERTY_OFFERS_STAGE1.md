# Phase 2 — Guest Booking Experience, Stage 1

For the subsequent full-suite run, populated V31-to-V33 migration verification,
browser-testing limitation, and exact changed-file inventory, see
[Stage 1 pre-PR verification](STAGE1_VERIFICATION.md).

Stage 1 adds private, reusable property offers to the existing Java/Spring Boot and
HTML/JavaScript application. It does not introduce guest accounts or a marketplace.

## Host and guest journey

In Properties, select **Share offer**, approve a nightly price in the property's
currency, and copy the generated link. The link is returned only once; the database
stores its SHA-256 hash. Offers expire seven days after creation. Price and currency
are immutable snapshots; revoke and create a replacement to change terms.

Guests open `/offer/{token}`, select dates, and request an availability/price quote.
A quote creates no reservation. Continuing creates a booking and a new independent
`/guest/{token}` link for registration and the existing payment/stay journey.
Revoking or expiring the offer does not affect its existing bookings or their links.

## Reservation and payment policy

Only bookings with `offer_id` use this policy. Existing host-created bookings,
Book Again bookings, payment providers, and receipt/link behavior remain compatible.

* A checkout hold lasts 30 minutes by default. Configure
  `app.offers.checkout-hold-minutes` between 1 and 30. A duplicate conflicting request
  fails without changing the original deadline; retries do not renew the hold.
  Booking submissions require a UUID `checkoutAttemptId`. It becomes the booking ID;
  reusing it returns 409, including after expiry. It never updates an existing
  booking. The browser retains the attempt and returned guest link in session
  storage for retries. If a response is lost before the guest link is received,
  contact the host to recover access rather than creating another hold.
  A guest can explicitly start a new checkout attempt; it still must pass current
  availability and cannot renew an existing hold.
* For arrivals more than 24 hours away, registration alone is insufficient. Guests
  must explicitly confirm before the hold expires. Confirmation is idempotent and
  reserves the dates until 12 hours before scheduled check-in.
* Payment opens 24 hours before check-in. The existing notification scheduler sends
  the payment request then, using its configured delivery channel. Existing email
  verification requirements still apply to email delivery. Reminders do not extend
  payment deadlines. Guests retain their booking link for payment.
* For bookings created within 24 hours of check-in, including after T−12, payment is
  immediately available after registration. The deadline is the earlier of creation
  plus the configured hold duration and check-in. These bookings do not receive an
  already-passed T−12 deadline.
* At the deadline, unpaid reservations stop blocking availability immediately, and
  their links cannot register, confirm, or initiate payment. Cleanup runs every
  `app.offers.cleanup-delay-ms` (default 60,000), records cancellation, revokes links,
  and cancels pending notifications. Availability never depends on cleanup running.
* Payment initiation does not extend the deadline. A provider success must be
  verified by Hostvero before the deadline. Late verified successes are recorded
  idempotently with `reservation_resolution_required_at`, retain a host receipt, and
  require manual resolution/refund. They never reactivate an expired reservation,
  unlock its guest link, or queue a payout. No automatic refund execution is added.
* The Nairobi-first default is `Africa/Nairobi`; no property-specific time-zone field
  exists yet. Offer check-in, checkout/link expiry, reminders and payment deadlines
  use this zone. Existing bookings retain their UTC behavior. API deadlines are UTC
  instants; the interface formats timestamps in the guest's device time zone.

`reservation_confirmed_at` represents an unpaid reservation commitment. It does not
set the existing `CONFIRMED` booking status, which still follows verified payment.
Before payment, the existing pending statuses are used with the new deadline fields.
Hosts may cancel offer reservations, but cannot reschedule or reprice them through
the generic booking edit endpoint; create a new reservation for changed terms.

## API additions

| Method | Route | Body / result |
| --- | --- | --- |
| POST | `/api/properties/{id}/offers` | Authenticated owner; `{nightlyPrice}` → offer and one-time token |
| GET | `/api/properties/{id}/offers` | Owner's offer list |
| POST | `/api/offers/{id}/revoke` | Owner; idempotent revocation |
| GET | `/api/public/offers/{token}` | Safe property name, approved price/currency, expiry |
| POST | `/api/public/offers/{token}/quote` | `{checkInDate, checkOutDate}` → nights, amount, fees, availability |
| POST | `/api/public/offers/{token}/bookings` | Dates plus `checkoutAttemptId` UUID → booking ID, guest link, hold/payment deadlines |
| POST | `/api/public/guest/{token}/confirm-reservation` | Explicit future reservation confirmation after registration |

Public date requests never control price, currency, property, guest, host, or booking
status. Unknown fields cannot override server calculations. Quotes use the immutable
offer price × calendar nights with `BigDecimal`; no discount/tax/pricing rules are
added. Existing Paystack 5% fees are disclosed separately. Offers currently support
two-decimal currencies, consistent with booking/payment storage.

Guest responses add `offerReservation` timing and eligibility information. Host
booking responses add the hold, confirmation, and payment-deadline fields. Host
payment responses add `reservationResolutionRequiredAt`, shown in booking details.
Public offer and confirmation endpoints participate in existing rate limiting.

## Database and concurrency

Flyway V32 creates `property_offers`, adds nullable `offer_id`,
`checkout_hold_expires_at`, `reservation_confirmed_at`, `payment_deadline_at` to
bookings, and nullable `reservation_resolution_required_at` to payments. Foreign
keys, amount/lifecycle checks, and lookup/deadline indexes are included. Existing
rows keep null values; no data backfill or old migration edits are required.
PostgreSQL V33 enables RLS and removes Supabase Data API role access to offers,
following the existing server-only tables. These migrations have not been deployed.

`AvailabilityService` remains the single availability authority, including pending
extensions and half-open `[check-in, check-out)` stays. Queries and calendars exclude
expired offer reservations, while paid bookings continue to block dates normally.
Reservation creation locks the property row, revalidates the offer under its row
lock, recalculates the quote, rechecks availability, and creates the booking/link
atomically. Existing booking and extension writes use the same property lock.
Confirmation and expiry lock the booking and property; callbacks coordinate with
these locks before deciding between normal completion and manual resolution.

Property photographs and richer pricing can be added later without changing the
offer-to-booking relationship or existing stored booking totals.

## Verification

`PropertyOfferPostgresIntegrationTest` uses a disposable embedded PostgreSQL server
and runs the real core and PostgreSQL Flyway migrations. It requires a non-root
runtime capable of starting local PostgreSQL processes/listeners; no production
database or Docker is used. The test-only dependency is
`io.zonky.test:embedded-postgres:2.2.2`.

Run with Java 21:

```sh
bash mvnw -Dtest=PropertyOfferPostgresIntegrationTest test
```

Run relevant existing booking, availability, payment, guest-link, receipt and
notification integration tests as regressions. Never substitute H2 results for the
PostgreSQL concurrency checks.

Verified on November 14, 2026 with Java 21:

* 15 PostgreSQL integration tests passed, including V32/V33 migrations, concurrent
  guest reservations, host-versus-guest reservations, revocation while waiting on
  the property lock, expiry without cleanup, duplicate attempts after expiry,
  immutable pricing, pending extensions, and verified late M-Pesa/Paystack payments.
* Three policy tests passed for exact T−24/T−12 boundaries, Nairobi time, hold expiry,
  and preservation of paid/legacy bookings.
* 62 existing regression tests passed across PhaseThree–PhaseSeven integration
  suites, PaystackIntegrationTest, StripeLiveWebhookIntegrationTest,
  GuestAccessPolicyIntegrationTest, GuestEmailVerificationIntegrationTest,
  PropertyCheckinLocationIntegrationTest, WebInterfaceControllerTest, and the
  extended PublicRateLimitFilterTest.
* JavaScript syntax checks and `git diff --check` passed. Browser interaction was
  not exercised. No deployment, push, merge, or production migration was performed.

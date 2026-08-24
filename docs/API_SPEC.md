# Hostvero API Specification

This file defines the initial API direction.

Exact payloads can be refined during implementation, but major resource boundaries should remain stable.

---

## 1. Authentication

POST /api/auth/register

POST /api/auth/login

POST /api/auth/logout

POST /api/auth/password-reset

---

## 2. Host profile

GET /api/me

PUT /api/me

---

## 3. Properties

GET /api/properties

POST /api/properties

GET /api/properties/{id}

PUT /api/properties/{id}

DELETE or deactivate /api/properties/{id}

GET /api/properties/{id}/availability

---

## 4. Guests

GET /api/guests

GET /api/guests/{id}

Search/filter support should be added to GET /api/guests.

---

## 5. Bookings

GET /api/bookings

POST /api/bookings

GET /api/bookings/{id}

PUT /api/bookings/{id}

POST /api/bookings/{id}/cancel

POST /api/bookings/{id}/extend

POST /api/bookings/{id}/confirm

---

## 6. Public guest link

GET /guest/{token}

POST /guest/{token}/register

POST /guest/{token}/payment-intent-or-initiation

GET /guest/{token}/receipt

GET /guest/{token}/stay

Public guest endpoints must validate the secure token and booking state.

---

## 7. Payments

GET /api/me/payout-settings

Returns only the authenticated host's payout configuration. Bank account numbers are masked and Paystack
subaccount references are never returned.

PUT /api/me/payout-settings

Creates or updates the authenticated host's Paystack payout destination. The request accepts only a bank-account
method, Paystack settlement-bank code, full account number and account name. Hostvero uses the full number only for
the provider request and persists only its final four digits. A configured destination is required before a Paystack
booking payment can begin.

POST /api/payments/mpesa/initiate

POST /api/payments/stripe/create

Provider callbacks/webhooks must have separate verified endpoints.

Examples:

POST /api/webhooks/mpesa

POST /api/webhooks/stripe

POST /api/webhooks/paystack

Paystack checkout is initialized through the existing booking payment endpoint using
`{"provider":"PAYSTACK"}`. The browser receives only the Paystack authorization URL; the webhook is authoritative.
For a host with configured payout settings, Hostvero submits that host's Paystack subaccount plus a flat
`transaction_charge` equal to the server-calculated Hostvero service fee. Paystack's main account bears processor
fees; the host subaccount settlement target remains the booking amount.

Do not mark a payment successful from a browser-only response.

---

## 8. Receipts

GET /api/bookings/{id}/receipts

GET /api/receipts/{id}

POST /api/receipts/{id}/resend

---

## 9. Availability

GET /api/properties/{id}/availability?from=...&to=...

For Book Again, a later endpoint may expose available ranges/dates.

All availability APIs must call the centralized AvailabilityService.

---

## 10. Dashboard

GET /api/dashboard

The dashboard response may include:

- arrivals today
- departures today
- current guests
- upcoming bookings
- pending payments
- today's revenue
- recent activity

---

## 11. Authorization

All host API endpoints require host authentication.

Hosts may access only their own:

- properties
- guests
- bookings
- payments
- receipts

Public guest-link endpoints use secure guest tokens instead of host authentication.

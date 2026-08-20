# Hostvero Product Specification

## 1. Product purpose

Hostvero is a host-only guest management system designed for short-term accommodation operators.

Primary users:
- Airbnb hosts
- Short-term rental operators
- Serviced apartments
- Guesthouses
- Small hotels

The core objective is:

Create booking → send guest link → guest registers → guest pays → receipt is generated → check-in information becomes available → stay is managed → checkout is handled → guest may extend or book again.

The interface must be simple, mobile-first, fast, and familiar to hosts.

---

## 2. Host account

A host can:

- register
- log in
- log out
- reset password
- edit profile
- manage properties
- view guests
- create bookings
- view payments
- view receipts
- manage property settings

---

## 3. Property management

Each host can create and manage properties.

Initial property fields:

- id
- host/user id
- property name
- property type
- location/address
- Google Maps link
- maximum guests
- default nightly rate
- check-in time
- check-out time
- Wi-Fi name
- Wi-Fi password
- house rules
- check-in instructions
- host contact phone
- active status

The host should enter reusable property information once.

Bookings should automatically use the property's saved information.

---

## 4. Digital Guest Register

The Digital Guest Register is one of the main Hostvero features.

Guest information:

- full name
- phone number
- WhatsApp number
- email
- ID/passport number
- number of guests
- booking history
- check-in date
- check-out date
- payment status

Hosts must be able to:

- view guests
- search guests
- filter guests
- view current guests
- view upcoming guests
- view past guests
- view booking history attached to a guest record

Do not create consumer-style guest profiles.

---

## 5. Booking creation

The host creates a booking by selecting:

- property
- guest
- check-in date/time
- check-out date/time
- number of guests
- final amount

The host can manually change the final booking amount.

There is no promo-code system in V1.

The system must check property availability before confirming a booking.

---

## 6. Availability

The system must have one centralized availability engine.

It answers:

Is property X available between start datetime and end datetime?

Availability logic must be reused by:

- new booking creation
- Extend Stay
- Book Again
- future Host Network referrals

A booking must not overlap another active/confirmed booking for the same property.

---

## 7. Guest temporary link

Guests do not create accounts.

Each booking can generate a secure temporary guest link.

The public URL must not expose predictable booking IDs.

The guest link supports different states.

### State A — registration/payment

Before successful payment, the guest sees:

- property
- stay dates
- amount
- registration fields
- payment options

### State B — paid/stay page

After successful payment, the same link becomes the Stay Page.

The Stay Page shows:

- booking confirmation
- payment status
- receipt
- Download Receipt action
- property location
- check-in instructions
- Wi-Fi information
- house rules
- host contact
- access information if configured

### State C — expired

After the booked stay ends, the guest link expires completely.

The guest can no longer access the Stay Page.

The receipt remains stored in the host account.

The host can resend the receipt if the guest requests it.

---

## 8. Payments

V1 payment methods:

- M-Pesa
- Stripe/card

Payment success must be verified server-side.

Store:

- payment reference
- booking
- guest
- host
- amount
- currency
- payment provider
- payment method
- status
- created time
- confirmed time

Payment statuses should include at minimum:

- PENDING
- SUCCEEDED
- FAILED
- CANCELLED
- REFUNDED if refunds are later supported

---

## 9. Receipts

After successful payment:

- generate a receipt
- associate it with the booking and payment
- make it downloadable from the guest Stay Page
- store it permanently for the host

Receipt fields should include:

- receipt number
- booking reference
- guest name
- property
- amount
- currency
- payment method
- payment reference
- payment date
- check-in
- check-out
- host details

The host must be able to resend the receipt later.

---

## 10. Immediate check-in information

Do not delay check-in details until check-in time.

After payment succeeds, check-in information becomes available immediately on the Stay Page.

This includes:

- location
- check-in instructions
- Wi-Fi
- house rules
- host contact
- door/lockbox information if configured

---

## 11. Future bookings

When a guest uses Book Again for a future stay:

1. Show only available dates.
2. Guest selects dates.
3. Create the booking without charging immediately.
4. Two days before arrival, send a reminder.
5. Twenty-four hours before arrival, send final confirmation and payment request.
6. Guest confirms and pays.
7. After payment succeeds:
   - generate receipt
   - unlock Stay Page
   - show check-in information

---

## 12. Checkout reminder

One hour before checkout:

1. Check whether the property is available after the current booking.

If available:
- remind guest about checkout
- offer Extend Stay

If unavailable:
- remind guest about checkout
- do not offer extension
- offer Book Again for another available date

---

## 13. Extend Stay

Before showing Extend Stay:

- check availability

If unavailable:
- do not display extension option

If available:
- show possible extension
- calculate additional amount
- allow payment
- update booking
- update booking end time/date
- record additional payment
- update or generate the appropriate receipt

---

## 14. Book Again

Book Again must:

- query the centralized availability engine
- display only available dates
- allow guest to select valid dates
- create a future booking
- not require immediate payment
- enter the future-booking reminder and confirmation flow

---

## 15. Dashboard

Initial dashboard should show:

- arrivals today
- departures today
- current guests
- upcoming bookings
- unpaid/pending bookings
- today's revenue
- recent activity
- quick New Booking action

Main navigation:

- Dashboard
- Bookings
- Guests
- Payments
- Properties
- Settings

---

## 16. UI direction

The UI should feel familiar to Airbnb hosts but must not copy Airbnb's branding or protected design assets.

Design principles:

- mobile-first
- clean white space
- clear cards
- rounded controls
- large touch targets
- simple navigation
- obvious primary actions
- minimal clutter
- fast loading
- original Hostvero branding

---

## 17. Not in V1

Do not build:

- guest marketplace
- consumer guest profiles
- guest accounts
- guest mobile app
- loyalty system
- public property marketplace
- white-label
- advanced reports
- Host Network

Host Network comes after the individual-host workflow is stable.
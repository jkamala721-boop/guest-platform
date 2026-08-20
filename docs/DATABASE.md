# Hostvero Database Specification

## 1. Initial entities

Initial entities:

- User
- Property
- Guest
- Booking
- Payment
- Receipt
- GuestLink
- Notification

Add new entities only when required by an approved roadmap phase.

---

## 2. User

Purpose:
Host account.

Suggested fields:

- id
- email
- password_hash
- full_name
- phone
- created_at
- updated_at
- active

Relationships:

User 1 → many Properties

---

## 3. Property

Suggested fields:

- id
- user_id
- name
- property_type
- address
- maps_url
- max_guests
- default_nightly_rate
- currency
- check_in_time
- check_out_time
- wifi_name
- wifi_password
- house_rules
- check_in_instructions
- contact_phone
- active
- created_at
- updated_at

Relationships:

Property many → one User

Property 1 → many Bookings

---

## 4. Guest

Suggested fields:

- id
- full_name
- phone
- whatsapp
- email
- id_passport_number
- created_at
- updated_at

A guest is a host-operational record.

It is not a consumer account.

---

## 5. Booking

Suggested fields:

- id
- property_id
- guest_id
- booking_reference
- check_in_at
- check_out_at
- number_of_guests
- final_amount
- currency
- status
- created_at
- updated_at

Relationships:

Booking many → one Property

Booking many → one Guest

Booking 1 → many Payments

Booking 1 → one or more Receipts

Booking 1 → one active GuestLink

---

## 6. Payment

Suggested fields:

- id
- booking_id
- provider
- provider_reference
- amount
- currency
- status
- created_at
- confirmed_at
- updated_at

Provider examples:

- MPESA
- STRIPE

---

## 7. Receipt

Suggested fields:

- id
- booking_id
- payment_id
- receipt_number
- amount
- currency
- generated_at
- file_path_or_storage_reference
- created_at

Receipt remains stored after GuestLink expiry.

---

## 8. GuestLink

Suggested fields:

- id
- booking_id
- token_hash or secure token representation
- state
- created_at
- expires_at
- revoked_at
- updated_at

Never expose the internal GuestLink id publicly.

---

## 9. Notification

Suggested fields:

- id
- booking_id
- guest_id
- type
- channel
- status
- scheduled_at
- sent_at
- provider_reference
- created_at

Notification types may include:

- BOOKING_CONFIRMATION
- TWO_DAY_REMINDER
- FINAL_CONFIRMATION
- PAYMENT_REQUEST
- PAYMENT_SUCCESS
- CHECKOUT_REMINDER
- EXTENSION_OFFER
- RECEIPT_RESEND

---

## 10. Availability rules

A property is unavailable when an overlapping blocking booking exists.

Do not allow overlapping confirmed/active bookings for the same property.

Availability checks must be performed through AvailabilityService.

---

## 11. Future entities

Do not implement yet unless roadmap phase requires them:

- HostVerification
- PropertyVerification
- Referral
- Subscription
- Staff
- PropertyBlock

PropertyBlock may later be useful for:
- maintenance
- owner stays
- offline/unavailable periods

Do not create it until needed.
# Hostvero Security Specification

## 1. Secrets

Never commit:

- .env
- database passwords
- API keys
- M-Pesa credentials
- Stripe credentials
- Supabase secrets
- Render secrets

Use environment variables.

---

## 2. Passwords

Never store plaintext passwords.

Use Spring Security-approved password hashing.

---

## 3. Guest links

Guest links must use cryptographically secure random tokens.

Do not use:

/guest/1
/guest/2
/booking/123

as public authorization mechanisms.

Guest tokens must support:

- booking association
- expiration
- revocation
- state validation

---

## 4. Guest-link expiry

After the booked stay ends:

- the guest link expires
- active check-in information is inaccessible
- Wi-Fi/access instructions are inaccessible
- door/lockbox information is inaccessible

Receipt storage remains available to the host.

---

## 5. Authorization

A host may only access resources owned by that host.

Enforce authorization for:

- Property
- Guest
- Booking
- Payment
- Receipt

Never rely only on frontend hiding.

---

## 6. Payments

Never trust browser payment success alone.

Stripe:
verify authenticated webhook events.

M-Pesa:
verify provider callback/server-side transaction state.

Use idempotency where appropriate to avoid duplicate processing.

---

## 7. Sensitive guest data

Guest data may include:

- ID/passport information
- phone
- email
- stay details

Apply:
- minimal exposure
- authorization
- secure transport
- appropriate database protection
- auditability where needed

Do not log sensitive values unnecessarily.

---

## 8. Error handling

Do not expose:

- stack traces
- database credentials
- SQL details
- internal paths
- tokens

to production users.

---

## 9. Logging

Logs should include useful operational events but avoid:

- passwords
- payment secrets
- raw payment credentials
- full guest tokens
- unnecessarily exposed identity data
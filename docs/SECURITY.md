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

## Public rate limiting

The public rate limiter is an in-process sliding-window limiter. Host login,
admin login, and registration use normalized email identifiers hashed with
SHA-256 and a distinct purpose prefix. Guest-link endpoints use SHA-256 token
hashes for per-link isolation, including when socket peer information is
unavailable. The filter checks for a stored token hash before allocating a
guest-link bucket, so arbitrary nonexistent tokens do not consume limiter
capacity. Raw email addresses and guest tokens are not limiter keys or limiter
log values. Paystack events use the configured route-wide limit after signature
verification, so invalid signatures do not consume its bucket.

Network-level aggregate abuse protection remains a known limitation. Render
may present shared proxy peers, and this application does not currently
configure a verified client-IP trust path. The in-memory limits are per
application instance; horizontal scaling weakens enforcement and must be
reviewed before adding instances. Revisit aggregate abuse protection using
production traffic evidence before introducing network-level limits or
trusting forwarded client-IP headers.

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
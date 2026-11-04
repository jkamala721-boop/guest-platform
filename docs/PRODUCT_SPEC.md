# Hostvero Product Specification

## 1. Product Identity

Hostvero is a web-based hospitality management platform for:

- Airbnb hosts.
- Short-term rental operators.
- Serviced apartments.
- Guesthouses.
- Small hotels.

Its purpose is to help hosts manage properties, bookings, guests, payments, communication, and the guest stay journey through one organized platform.

Hostvero is host-oriented. Guests interact through secure web links and do not need to install an application or create Hostvero accounts.

### Core product objectives

1. Simplify property and booking management.
2. Reduce repetitive guest communication.
3. Organize guest registration and stay information.
4. Facilitate payment tracking and receipts.
5. Improve the guest experience.
6. Help hosts improve their properties using guest feedback.
7. Eventually connect hosts through a professional host network.

---

## 2. Product Scope and Status

This specification describes both existing functionality and approved future functionality.

Every feature must be classified as one of:

- Implemented: verified in the current application.
- Approved improvement: agreed but not necessarily implemented.
- Planned: intended for a future roadmap phase.
- Proposed: requires further product approval.

A feature appearing in this document must not automatically be treated as implemented.

The current implementation must be inspected before any feature is modified.

---

## 3. Host Account and Authentication

Hosts can:

- Register.
- Log in.
- Log out.
- Reset passwords.
- Edit their profiles.
- Manage properties.
- View guests.
- Create and manage bookings.
- View payments.
- View receipts.
- Manage property settings.
- Manage payout settings where supported.

Security requirements:

- Passwords must never be stored in plaintext.
- Protected host operations require authentication.
- Hosts may only access resources they are authorized to access.
- Session and token handling must follow the security specification.

---

## 4. Property Management

Each host can create and manage properties.

Property information includes:

- Property ID.
- Host/user ID.
- Property name.
- Property type.
- Location and address.
- Google Maps link.
- Maximum guest capacity.
- Default nightly rate.
- Currency.
- Check-in time.
- Check-out time.
- Wi-Fi name.
- Wi-Fi password.
- House rules.
- Check-in instructions.
- Host contact information.
- Property photographs.
- Active status.
- Creation and update timestamps.

Hosts should enter reusable property information once.

The information should be available for relevant bookings and guest-facing pages.

### Property photographs

The guest-facing property page should display property photographs before the guest proceeds with booking.

Requirements:

- Display a property photo gallery.
- Allow guests to open and browse photographs.
- Make the gallery mobile-friendly.
- Associate photographs with the correct property.
- Protect property-management operations through host authorization.
- Validate uploaded file types and sizes.
- Prevent unauthorized file access or modification.

The property gallery should reduce repeated requests from guests asking hosts to send photographs manually.

---

## 5. Digital Guest Register

The Digital Guest Register is a core Hostvero feature.

Guest information may include:

- Full name.
- Phone number.
- WhatsApp number.
- Email.
- ID/passport number where required.
- Number of guests.
- Booking history.
- Check-in date.
- Check-out date.
- Payment status.

Hosts should be able to:

- View guests.
- Search guests.
- Filter guests.
- View current guests.
- View upcoming guests.
- View past guests.
- View booking history associated with a guest record.

A guest record is a host-operational record, not a consumer account.

Guests must not be required to create Hostvero accounts.

Guest information must only be accessible to authorized users and through the appropriate secure guest workflow.

---

## 6. Property and Booking Creation Workflow

The intended revised workflow separates property configuration from guest booking details.

### Host workflow

1. Host selects or creates a property.
2. Host maintains property information and photographs.
3. Host reviews the property's default pricing.
4. Host can apply an approved price adjustment for a particular booking or guest offer.
5. Host reviews the relevant booking information.
6. Host generates and shares a guest link.

The host should not need to select the guest's check-in and check-out dates during the initial guest-link creation flow.

### Guest workflow

1. Guest opens the shared property/booking link.
2. Guest views the property information.
3. Guest browses property photographs.
4. Guest selects check-in and check-out dates.
5. Guest enters registration information.
6. The system validates property availability.
7. The system calculates or confirms the applicable booking amount.
8. Guest reviews the booking details.
9. Guest proceeds to payment.
10. After verified payment, the guest receives access to the applicable stay information.

### Pricing rules

The system must distinguish between:

- Property default nightly rate.
- Host-approved booking-specific price adjustment.
- Final booking amount.
- Hostvero service fee, where applicable.
- Payment-provider processing fees.
- Host payout amount.

The backend must calculate and validate all financial amounts.

Never trust a final price submitted only by the browser.

---

## 7. Availability Management

The system must use one centralized availability engine.

It must answer:

Is property X available between the requested start and end dates?

Availability logic must be reused by:

- Guest date selection.
- Booking creation.
- Extend Stay.
- Book Again.
- Future Host Network referrals.

Requirements:

- Prevent overlapping blocking bookings.
- Validate availability on the server.
- Recheck availability immediately before booking confirmation.
- Handle simultaneous booking attempts safely.
- Display unavailable dates clearly.
- Do not allow a guest to pay for a booking that cannot be confirmed.

Availability must be based on the actual booking and property-blocking rules, not frontend calendar assumptions.

---

## 8. Guest Temporary Links

Guests do not create Hostvero accounts.

Each booking or approved guest journey can use a secure guest link.

Public URLs must not expose predictable booking IDs as authorization credentials.

The guest link must support appropriate states.

### State A — Property and booking

The guest can access:

- Property information.
- Property photographs.
- Available date selection where applicable.
- Booking information.
- Registration fields.
- Applicable payment options.

### State B — Registration and payment

The guest can:

- Complete registration.
- Review booking details.
- Review the amount.
- Initiate payment.
- See payment status.

### State C — Paid and active Stay Page

After verified payment, the guest can access:

- Booking confirmation.
- Payment status.
- Receipt.
- Download Receipt action.
- Property location.
- Check-in instructions.
- Wi-Fi information.
- House rules.
- Host contact.
- Access information where configured.

### State D — Expired or revoked

After the applicable expiry time or revocation:

- Stay information is inaccessible.
- Wi-Fi and access instructions are inaccessible.
- Private booking information is inaccessible.
- The host retains the receipt in their account.
- The host can resend the receipt when appropriate.

Guest-link expiration must not delete the underlying booking, payment, receipt, or review record.

---

## 9. Payments

Payment methods and providers must follow the currently approved implementation and production configuration.

The system must support the approved payment-provider integrations without allowing browser-only confirmation to establish payment success.

Payment records should retain:

- Payment ID.
- Booking.
- Guest.
- Host where applicable.
- Provider.
- Provider reference.
- Amount.
- Currency.
- Payment method.
- Status.
- Created time.
- Confirmed time.
- Relevant service-fee and settlement accounting values.

Payment statuses should support at least:

- PENDING.
- SUCCEEDED.
- FAILED.
- CANCELLED.
- REFUNDED where refunds are supported.

### Payment requirements

- Verify payments server-side.
- Verify provider webhooks.
- Prevent duplicate payment processing.
- Use idempotency where appropriate.
- Keep payment and booking states consistent.
- Keep host payout accounting separate from guest payment confirmation.
- Never expose provider secrets to the browser.

The existing Paystack, M-Pesa and Stripe behavior must be verified against the implementation and deployment configuration before being changed.

---

## 10. Receipts

After successful payment:

- Generate a receipt.
- Associate it with the booking and payment.
- Make it available through the appropriate guest journey.
- Store it for the host.
- Allow the host to resend it.

Receipt information should include:

- Receipt number.
- Booking reference.
- Guest name.
- Property.
- Amount.
- Currency.
- Payment method.
- Payment reference.
- Payment date.
- Check-in.
- Check-out.
- Relevant host details.

Receipt persistence must not depend on the lifetime of the guest link.

---

## 11. Stay Information

After payment succeeds, relevant check-in information becomes available on the Stay Page according to the booking rules.

It may include:

- Property location.
- Check-in instructions.
- Wi-Fi details.
- House rules.
- Host contact.
- Door or lockbox instructions where configured.

Sensitive access information must be protected and must not remain available after the applicable guest-link expiry.

---

## 12. Future Bookings

When a guest uses Book Again:

1. Query the centralized availability engine.
2. Display valid available dates.
3. Allow the guest to select dates.
4. Create a future booking according to the approved booking rules.
5. Do not charge immediately unless the approved payment flow requires it.
6. Send the appropriate reminders.
7. Send final confirmation and payment instructions.
8. Verify payment.
9. Generate the receipt.
10. Unlock the Stay Page when the payment and booking conditions are satisfied.

The reminder schedule must be configurable and must not send duplicate notifications.

---

## 13. Checkout and Extend Stay

### Checkout reminder

One hour before checkout, the system should:

1. Check property availability after the current booking.
2. Send the appropriate checkout reminder.

If the property is available:
- Offer Extend Stay where supported.

If the property is unavailable:
- Do not offer an extension.
- Offer Book Again for another valid date where supported.

### Extend Stay

Before offering an extension:

- Check availability.
- Calculate the additional amount.
- Display the extension terms.

If the extension is confirmed:

- Update the booking appropriately.
- Record additional payment.
- Update or generate the appropriate receipt.
- Preserve the booking's financial history.

---

## 14. Host Dashboard

The dashboard should provide an operational overview.

Potential dashboard information:

- Arrivals today.
- Departures today.
- Current guests.
- Upcoming bookings.
- Pending payments.
- Today's revenue.
- Recent activity.
- Property overview.
- Review and rating summaries when implemented.

Main navigation:

- Dashboard.
- Bookings.
- Guests.
- Payments.
- Properties.
- Notifications.
- Settings.

Dashboard figures must be based on reliable underlying records.

---

## 15. Property Ratings and Guest Reviews

### Purpose

Allow guests to rate a property after completing their stay.

This feature is intended to help hosts understand guest satisfaction and identify property improvements.

### Guest review eligibility

A review must be associated with a genuine completed booking.

Requirements:

- Only eligible completed stays can be reviewed.
- One review per eligible booking.
- Guests do not need Hostvero accounts.
- Use a secure booking-associated review invitation or token.
- Prevent duplicate submissions.
- Prevent unauthorized reviews of another property.
- Apply review-link expiration and abuse protection.
- Do not expose another guest's private information.

### Rating structure

Guests may provide:

- Overall rating from 1 to 5 stars.
- Written feedback.
- Optional category ratings.

Potential categories:

- Cleanliness.
- Accuracy of property description.
- Location.
- Communication.
- Value.
- Check-in experience.
- Wi-Fi and connectivity.
- Amenities.

The final categories must be approved and kept concise enough for guests to complete the review easily.

### Review management

Hosts should eventually be able to:

- View property reviews.
- View average ratings.
- Read written feedback.
- Identify recurring issues.
- Track rating trends.

### Review moderation

The platform should support:

- Reporting inappropriate reviews.
- Moderation of abusive or fraudulent content.
- Protection against review manipulation.
- Clear rules for review visibility.

Do not allow hosts to delete legitimate negative feedback simply because it is unfavorable.

---

## 16. Hostvero Platform Rating and Feedback

Property reviews and Hostvero platform ratings are two separate systems.

Property reviews evaluate an accommodation.

Hostvero platform ratings evaluate the Hostvero application and user experience.

### Website rating page

Hostvero's website should include a permanent page where eligible users can rate the platform.

The page should support:

- Overall star rating.
- Written feedback.
- What the user finds useful.
- What the user would improve.
- Optional recommendation question.
- Clear submission confirmation.

### Email review request

The intended workflow:

1. A user registers for Hostvero.
2. The system observes meaningful product activity.
3. After an appropriate period of use, approximately seven days as an initial experiment, the user becomes eligible for a review request.
4. Hostvero sends an email inviting the user to rate the platform.
5. The email contains a direct link to the rating page.
6. The system records the request.
7. After submission, the user is marked as having provided platform feedback.
8. Duplicate or excessive requests are prevented.

The final timing and eligibility rules must be configurable.

Do not send repeated requests merely because a user signs in repeatedly.

Do not request feedback only from users expected to provide positive ratings.

Respect applicable email preferences and consent requirements.

### Feedback management

Authorized Hostvero administrators should eventually be able to:

- View submitted ratings.
- Read written feedback.
- Identify recurring problems.
- Track rating trends.
- Identify product improvement opportunities.

Public display of testimonials requires appropriate user consent.

---

## 17. AI Review Analysis and Improvement Recommendations

This is a future feature, not part of the current implementation unless independently verified.

### AI Review Analysis

The AI should analyze guest reviews associated with a host's properties.

It should identify:

- Recurring positive themes.
- Recurring negative themes.
- Common complaints.
- Common compliments.
- Frequency of each theme.
- Severity and potential impact.
- Changes in guest sentiment over time.

The AI must distinguish between isolated feedback and recurring patterns.

### AI Improvement Advice

After analyzing reviews, the AI should provide practical recommendations to the host.

Examples:

- Improve Wi-Fi reliability.
- Improve cleaning procedures.
- Make check-in instructions clearer.
- Replace or repair damaged amenities.
- Improve communication response times.
- Update inaccurate property photographs or descriptions.

Each recommendation should explain:

1. What guests are reporting.
2. How often the issue appears.
3. Why it matters.
4. What the host can do.
5. How the host can verify the improvement.

Recommendations must be grounded in actual feedback.

The AI must not invent complaints or claim certainty when evidence is limited.

### Recommendation prioritization

Recommendations may be prioritized according to:

- Frequency.
- Severity.
- Guest impact.
- Practical effort.
- Potential improvement in guest experience.

### Improvement tracking

Hosts should eventually be able to:

- Mark a recommendation as planned.
- Mark it as in progress.
- Mark it as completed.
- Record what they changed.
- Compare subsequent guest feedback.

The system may report whether ratings or feedback improved after a change, but must not claim that the change caused the improvement without sufficient evidence.

---

## 18. Adaptive Guest Feedback Questions

This is a future AI-assisted feature connected to review analysis.

The objective is to collect more useful and specific feedback without making reviews unnecessarily long.

Example:

If previous reviews repeatedly identify Wi-Fi problems, the system may include a neutral question:

"How was the Wi-Fi connection during your stay?"

Other possible questions:

- How was the cleanliness of the property?
- Were the check-in instructions easy to follow?
- Was communication with the host satisfactory?
- Were the listed amenities available and working?

Requirements:

- Questions must remain neutral.
- Questions must not pressure guests into positive or negative answers.
- Do not reveal another guest's private review.
- Do not disclose confidential AI analysis.
- Avoid asking questions irrelevant to the property.
- Keep the questionnaire short.
- Allow guests to skip optional questions.
- Do not make unsupported assumptions about the guest's experience.

The AI may select questions based on recurring property feedback, but the selection rules must be controlled and auditable.

---

## 19. Hostvero Community and Host Network

This is a future roadmap feature.

It should be developed only after the core individual-host workflows are reliable.

The community has two related components.

### A. Host Network — Host-to-Host Referrals

Purpose:

Allow hosts to refer guests to another suitable host when they cannot accommodate a booking.

Potential workflow:

1. A host receives a booking request.
2. The host cannot accommodate the requested dates.
3. The host searches the Hostvero Host Network.
4. The host identifies another suitable property.
5. The host sends a referral request.
6. The receiving host accepts or rejects.
7. If accepted, the guest receives the appropriate property or booking link.
8. The receiving host manages the referred booking through their own Hostvero account.

Potential features:

- Verified host profiles.
- Property verification.
- Network property search.
- Availability matching.
- Referral requests.
- Referral acceptance or rejection.
- Guest referral workflow.
- Secure temporary guest links.
- Referral notifications.
- Referral history.
- Referral status tracking.
- Premium access controls.

Initial business rules:

- Host Network access is intended as a premium feature.
- No referral commission in the initial model.
- Host verification is required.
- Availability must use the centralized availability engine.
- Guest information must only be shared with appropriate authorization and consent.
- Hosts must not see other hosts' private bookings, payment records, or guest information.

### B. Host Education and Knowledge Community

Potential features:

- Hosting guides.
- Educational resources.
- Host discussions.
- Knowledge sharing.
- Best practices.
- Property-improvement advice.
- Hosting business insights.
- Community learning.

The Host Network and educational community may share a Hostvero Community section, but must have separate permissions and workflows.

---

## 20. Host-Branded Communication

Future premium capability:

Allow hosts to customize guest communication templates using controlled branding options.

Potential customization:

- Logo.
- Business or property name.
- Brand colors.
- Footer.
- Contact information.
- Approved layout options.

Avoid unrestricted HTML uploads unless a separate security review approves them.

Hostvero should maintain control over email deliverability, template safety, and sender authentication.

---

## 21. Future AI Assistants and Host Intelligence

Potential future capabilities:

### AI Guest Assistant
Answer guest questions using verified property and booking information.

### AI Host Assistant
Help hosts understand bookings, guests, payments, and operations.

### AI Communication Assistant
Prepare personalized guest messages, reminders, and instructions.

### AI Support Assistant
Answer common Hostvero support questions and escalate complex issues.

### AI Host Insights
Identify operational patterns and improvement opportunities.

All AI features must respect authentication, property ownership, data permissions, and privacy requirements.

---

## 22. Non-Goals and Restrictions

Hostvero must not become a consumer guest marketplace as part of the initial product.

Do not introduce without explicit approval:

- Guest accounts.
- Guest mobile application.
- Public property marketplace.
- Consumer loyalty system.
- Unapproved payment providers.
- Unapproved AI features.
- Unapproved referral commissions.
- Unrestricted guest-data sharing.

---

## 23. Product Quality Requirements

Hostvero should be:

- Mobile-first.
- Secure.
- Reliable.
- Easy to understand.
- Fast to use.
- Accessible.
- Maintainable.
- Privacy-conscious.

The guest journey should require minimal effort.

The host workflow should reduce repetitive work rather than introduce additional administrative burden.

Every significant feature must be validated with appropriate tests and real user workflows.

---

## 24. Product Vision

Hostvero's long-term direction is to become an intelligent hospitality management and host-network platform.

It should help hosts:

1. Manage their hospitality operations.
2. Deliver better guest experiences.
3. Improve property quality using guest feedback.
4. Connect with other hosts.
5. Make better operational decisions.

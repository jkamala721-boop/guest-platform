# Frontend Documentation

## 1. Purpose

This document defines the frontend architecture, user experience principles, interaction standards, and development requirements for Hostvero.

It is intended to guide frontend development while maintaining consistency, usability, security, and compatibility with the backend.

**Important:** This document describes intended frontend behavior and standards. It does not confirm that every page, component, or feature described here currently exists.

Existing frontend implementation must be inspected before making changes.

## 2. Approved Technology

Hostvero's current frontend technology:

- HTML.
- CSS.
- JavaScript.

The backend uses Java 21 and Spring Boot.

The frontend communicates with the backend through its existing API.

Do not introduce React, Vue, Angular, Next.js, or another frontend framework without explicit approval.

Do not replace the existing frontend architecture merely to make development more convenient.

## 3. Frontend Principles

The frontend should follow these principles:

1. Simple and intuitive navigation.
2. Responsive design across mobile, tablet, and desktop.
3. Consistent visual identity.
4. Clear feedback after user actions.
5. Accessible forms and controls.
6. Minimal unnecessary steps.
7. Secure handling of user-facing information.
8. Consistent integration with backend business rules.
9. Reusable components and maintainable JavaScript.
10. Performance-conscious image and asset loading.

The interface should be professional, trustworthy, and easy for hosts who are not technically experienced.

## 4. Main User Experiences

Hostvero has two primary user experiences.

### 4.1 Host Experience

Hosts use the platform to manage their properties, guests, bookings, and related operations.

The host experience should provide access to relevant management capabilities, including:

- Account and profile management.
- Property management.
- Property photographs and gallery management.
- Booking management.
- Guest information.
- Digital Guest Register.
- Payment and receipt information.
- Notifications.
- Review management where implemented.
- Future Host Network and education features.

The exact navigation and page structure must follow the existing application architecture and approved product requirements.

### 4.2 Guest Experience

Guests primarily access Hostvero through secure web links.

Guests should not need to create a Hostvero account or install an application to complete the intended guest booking flow.

The guest experience should be mobile-friendly and focused on completing the required steps with minimal friction.

Depending on the implemented booking flow, this may include:

- Viewing property information.
- Browsing property photographs.
- Selecting check-in and check-out dates.
- Viewing applicable pricing.
- Registering required guest information.
- Completing payment.
- Accessing the Guest Stay Page.
- Viewing relevant stay information.

Do not expose private stay information before the required authorization and payment conditions have been satisfied.

## 5. Host Property Management

The property management interface should allow hosts to maintain accurate property information.

Where implemented, hosts should be able to:

- Create a property.
- Edit property details.
- Add and manage photographs.
- Update descriptions and facilities.
- View their properties.
- Manage relevant property settings.

Property information should be organized clearly.

Photograph management should make it easy to add, remove, reorder, and preview images where those operations are supported.

Image uploads must follow backend validation and security requirements.

Do not assume that an image has been successfully uploaded until the backend confirms the operation.

## 6. Property Gallery and Guest Presentation

Property photographs are an important part of the guest decision process.

The guest-facing experience should present photographs in a clear and browsable gallery.

The interface should:

- Display available property photographs.
- Allow guests to browse photographs.
- Use appropriate image sizing.
- Avoid distorted image proportions.
- Provide useful loading and error states.
- Work well on mobile devices.

Property photographs must be associated with the correct property.

Do not expose private image storage credentials or unrestricted storage access.

If a property has no photographs, show an appropriate empty state rather than a broken image.

## 7. Booking Flow

The intended booking experience should be straightforward.

### 7.1 Host Side

The host should be able to:

1. Select the relevant property.
2. Review the property's information and photographs.
3. Configure the booking offer and applicable price where supported.
4. Share the guest booking link.

The host must be able to understand the booking terms before sharing the link.

### 7.2 Guest Side

The intended guest flow is:

1. Open the secure guest link.
2. View the property information and photographs.
3. Select check-in and check-out dates.
4. Review applicable pricing and booking terms.
5. Provide the required guest information.
6. Complete the payment process.
7. Access the Guest Stay Page after the required verification.

This is the approved product direction. Inspect the current implementation before changing existing pages or claiming that this flow is fully available.

### 7.3 Booking Validation

The frontend should

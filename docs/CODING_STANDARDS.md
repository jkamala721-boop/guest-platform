# Hostvero Coding Standards

## 1. Purpose

These standards ensure that Hostvero remains readable, maintainable, testable, and easy to debug.

All new code must follow these rules.

Existing code should be brought into compliance when it is safely modified or deliberately refactored. Do not perform a risky full-project rewrite solely to reformat legacy code.

## 2. General Principles

- Readability comes before brevity.
- Prefer explicit code over clever code.
- Keep each method focused on one responsibility.
- Avoid unnecessary abstraction.
- Avoid duplicated business logic.
- Use descriptive names.
- Follow existing project conventions unless they conflict with these standards.
- Do not compress complex logic into one line.

## 3. Indentation and Formatting

Use `.editorconfig` as the shared formatting authority.

Default indentation:

- Java: 4 spaces.
- JavaScript: 2 spaces.
- HTML: 2 spaces.
- CSS: 2 spaces.
- JSON: 2 spaces.
- Markdown: use consistent readable indentation.

Do not mix tabs and spaces.

Use UTF-8 encoding.

Use consistent line endings.

Remove trailing whitespace.

End text files with a newline.

## 4. Line Length

Target a maximum of approximately 100 characters per line.

120 characters is a soft upper limit, not a reason to make code unreadable.

Break long:
- Method declarations.
- Parameter lists.
- Function calls.
- Conditional expressions.
- SQL statements.
- Object definitions.
- HTML attributes.
- CSS declarations.
- JavaScript expressions.

Do not split a simple expression unnecessarily when doing so reduces readability.

## 5. Java Standards

Use standard Java naming conventions:

- Classes: PascalCase.
- Methods: camelCase.
- Variables: camelCase.
- Constants: UPPER_SNAKE_CASE.
- Packages: lowercase.

Examples:

Good:
`BookingAvailabilityService`

Good:
`calculateBookingTotal()`

Bad:
`bookingservice`

Bad:
`calc()`

Additional rules:

- Keep classes focused.
- Avoid large controller classes.
- Keep business logic in services.
- Keep persistence logic in repositories.
- Use constructor injection.
- Use appropriate access modifiers.
- Avoid unnecessary mutable state.
- Prefer immutable values when practical.
- Use appropriate Java types.
- Avoid raw types.
- Avoid unexplained magic numbers.
- Use enums for well-defined state sets.
- Use meaningful exception types.
- Avoid broad exception handling unless justified.
- Do not return null when a clear alternative is available.
- Use transactions at appropriate service boundaries.
- Avoid N+1 database queries.
- Avoid unnecessary database calls.

## 6. Spring Boot Standards

Controllers:
- Handle HTTP requests and responses.
- Validate request input.
- Delegate business operations to services.
- Do not contain complex business rules.

Services:
- Contain business logic.
- Coordinate repositories and integrations.
- Enforce business invariants.
- Use transactions appropriately.

Repositories:
- Handle persistence.
- Avoid embedding unrelated business logic.

DTOs:
- Define clear request and response structures.
- Do not expose internal entities unnecessarily.
- Do not expose sensitive fields.

Security:
- Enforce authorization on the backend.
- Never depend on frontend restrictions alone.

## 7. JavaScript Standards

- Use the existing JavaScript module structure.
- Use descriptive variable and function names.
- Prefer const when reassignment is unnecessary.
- Use let when reassignment is required.
- Avoid var in new code.
- Avoid global variables.
- Keep functions focused.
- Avoid deeply nested callbacks.
- Handle rejected promises.
- Handle network failures.
- Avoid duplicate event listeners.
- Avoid mixing rendering, API communication, and business rules unnecessarily.
- Never place secret credentials in browser JavaScript.

## 8. HTML Standards

- Use semantic HTML elements.
- Keep the document structure readable.
- Use accessible labels for form controls.
- Provide appropriate alternative text for meaningful images.
- Avoid unnecessary inline styles.
- Avoid large inline JavaScript blocks.
- Keep reusable layout patterns consistent.
- Ensure keyboard accessibility where applicable.

## 9. CSS Standards

- Use existing design tokens.
- Keep selectors understandable.
- Avoid unnecessary specificity.
- Avoid excessive `!important`.
- Group related styles.
- Use responsive layouts.
- Avoid duplicated style declarations.
- Keep mobile and desktop behavior consistent.

## 10. Methods and Functions

Methods should have one clear responsibility.

Avoid:
- Extremely long methods.
- Deeply nested conditions.
- Large blocks of unrelated logic.
- Multiple unrelated side effects.
- Unclear boolean parameters.

Extract smaller methods when this improves understanding.

Do not create unnecessary tiny methods that make code harder to follow.

## 11. Naming

Names should communicate intent.

Avoid vague names:
- data
- obj
- temp
- value
- process
- manager
- helper

Use domain-specific names:
- bookingReference
- guestLinkToken
- paymentStatus
- propertyAvailability
- hostPayoutSettings

Use consistent terminology across the codebase and documentation.

## 12. Error Handling

- Handle expected errors explicitly.
- Never silently swallow exceptions.
- Do not expose internal implementation details to users.
- Return appropriate HTTP status codes.
- Log useful diagnostic context.
- Never log passwords, credentials, full guest tokens, or payment secrets.
- Preserve the original exception cause when wrapping errors.

## 13. Comments and Documentation

Comments should explain:
- Why a non-obvious decision exists.
- Important business constraints.
- Security-sensitive behavior.
- Complex calculations.
- External integration requirements.

Do not comment obvious operations.

Update comments when behavior changes.

Remove misleading or outdated comments.

## 14. Database and SQL

- Use parameterized queries.
- Avoid SQL injection risks.
- Use appropriate indexes.
- Use appropriate foreign keys and constraints.
- Avoid unnecessary columns and duplicate data.
- Respect transaction boundaries.
- Never modify an applied production Flyway migration.
- Use new versioned migrations for approved schema changes.

## 15. Dependencies

- Reuse existing dependencies where possible.
- Do not add a dependency for functionality already provided by the stack.
- Explain the reason for new dependencies.
- Avoid abandoned or unnecessary libraries.
- Review security and maintenance implications.

## 16. Formatting Enforcement

Use automated formatting and linting where appropriate.

The project should establish:
- `.editorconfig`
- A Java formatter.
- Java static analysis or style checks.
- JavaScript formatting.
- JavaScript linting.

Inspect the existing build configuration before adding tools.

Do not introduce competing formatters or conflicting rules.

## 17. Final Rule

Every developer and AI coding assistant must produce code that another developer can understand, debug, test, and safely maintain.

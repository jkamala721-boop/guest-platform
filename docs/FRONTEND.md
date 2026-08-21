# Hostvero web interface

Phase 8 ships a framework-free, mobile-first static interface from
`src/main/resources/static/`:

- `index.html` is the application shell.
- `css/` contains design tokens, layout, reusable components, and page styles.
- `js/api.js` owns authenticated API calls and session-only token handling.
- `js/ui.js` contains rendering helpers; `js/app.js` contains route-specific views.
- `images/hostvero-logo.png` is the supplied Hostvero brand-board asset used by the
  interface. Replace it with a production-ready cropped Hostvero logo at the same
  path when one is available.

Host routes are hash routes (`#/overview`, `#/bookings`, `#/guests`,
`#/properties`, `#/payments`, `#/notifications`, and `#/settings`) so static
hosting does not need a server-side route rewrite. Public guest links use the
shareable `/guest/{token}` path, which serves the shell and resolves data only
through `/api/public/guest/{token}`.

The browser stores the host bearer token in `sessionStorage`, never in source
code or a URL. Static host pages contain no protected data; every host record is
loaded through existing authenticated APIs. Public guest pages render only the
state-specific DTO returned by the existing token-scoped API.

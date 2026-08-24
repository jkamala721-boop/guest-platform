import { api, apiMany, del, get, post, put, session, ApiError } from './api.js';
import { $, $$, badge, confirmDialog, emptyState, escapeHtml, formatDate, formatDateTime, formatMoney, openModal, setButtonBusy, titleCase, toast } from './ui.js';

const app = $('#app');
const icons = {
  overview: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 11.5 12 4l9 7.5"></path>
      <path d="M5 10.5V20h14v-9.5"></path>
      <path d="M9 20v-6h6v6"></path>
    </svg>`,

  bookings: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="5" width="18" height="16" rx="2"></rect>
      <path d="M8 3v4M16 3v4M3 10h18"></path>
      <path d="M8 14h3M8 17h6"></path>
    </svg>`,

  guests: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="4"></circle>
      <path d="M4 21c.7-4.2 3.4-6.5 8-6.5S19.3 16.8 20 21"></path>
    </svg>`,

  properties: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 21V8l8-5 8 5v13"></path>
      <path d="M8 21v-8h8v8M9 9h.01M15 9h.01"></path>
    </svg>`,

  payments: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3" y="5" width="18" height="14" rx="2"></rect>
      <path d="M3 10h18M7 15h4"></path>
    </svg>`,

  notifications: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"></path>
      <path d="M10 21h4"></path>
    </svg>`,

  settings: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="3"></circle>
      <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 .6 1.7 1.7 0 0 0-.4 1.1V21H9.6v-.1A1.7 1.7 0 0 0 8 19.4a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 3.6 15a1.7 1.7 0 0 0-.6-1 1.7 1.7 0 0 0-1.1-.4H2V9.6h.1A1.7 1.7 0 0 0 3.6 8a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 8 3.6a1.7 1.7 0 0 0 1-.6 1.7 1.7 0 0 0 .4-1.1V2h4v.1A1.7 1.7 0 0 0 15 3.6a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.4 8c.08.38.29.73.6 1 .3.25.69.39 1.1.4h.1v4h-.1a1.7 1.7 0 0 0-1.7 1.6Z"></path>
    </svg>`,

  logout: `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M10 17l5-5-5-5M15 12H3"></path>
      <path d="M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5"></path>
    </svg>`
};

const navItems = [
  ['overview', 'Overview'],
  ['bookings', 'Bookings'],
  ['guests', 'Guests'],
  ['properties', 'Properties'],
  ['payments', 'Payments'],
  ['notifications', 'Notifications']
];
const state = { host: null, bookings: [], properties: [], guests: [] };

const hashRoute = () => location.hash.replace(/^#\/?/, '') || 'overview';
const go = (route) => { location.hash = `#/${route}`; };
const isPublicRoute = () => location.pathname.startsWith('/guest/');
const initials = (name = '') => name.split(/\s+/).filter(Boolean).slice(0, 2).map(value => value[0]).join('').toUpperCase() || 'HV';
const dateInput = (date = '') => String(date || '').slice(0, 10);
const pageTitle = route => ({ overview: 'Overview', bookings: 'Bookings', guests: 'Guests', properties: 'Properties', payments: 'Payments', notifications: 'Notifications', settings: 'Settings' })[route] || 'Hostvero';

function hostShell(route, content) {
  const active = route.split('/')[0];

  const nav = navItems.map(([key, label]) => `
    <a
      href="#/${key}"
      class="nav-link ${active === key ? 'active' : ''}"
      aria-current="${active === key ? 'page' : 'false'}"
    >
      <span class="nav-icon">${icons[key]}</span>
      <span>${label}</span>
    </a>
  `).join('');

  const mobile = navItems.slice(0, 5).map(([key, label]) => `
    <a
      href="#/${key}"
      class="${active === key ? 'active' : ''}"
      aria-current="${active === key ? 'page' : 'false'}"
    >
      <span class="nav-icon">${icons[key]}</span>
      <span>${label}</span>
    </a>
  `).join('');

  const hostName = state.host?.fullName || '';
  const hostInitials = initials(hostName);

  return `
    <div class="app-shell">

      <aside class="sidebar">

        <div class="sidebar-brand">
          <a href="#/overview" aria-label="Hostvero overview">
            <img
              src="/images/hostvero-logo-clean.png"
              alt="Hostvero"
              class="sidebar-logo"
            >
          </a>
        </div>

        <nav class="nav" aria-label="Primary navigation">
          ${nav}
        </nav>

        <div class="sidebar-footer">

          <a
            href="#/settings"
            class="nav-link ${active === 'settings' ? 'active' : ''}"
          >
            <span class="nav-icon">${icons.settings}</span>
            <span>Settings</span>
          </a>

          <button
            id="logout-button"
            class="nav-link sidebar-logout"
            type="button"
          >
            <span class="nav-icon">${icons.logout}</span>
            <span>Log out</span>
          </button>

        </div>

      </aside>

      <main class="main-area" id="app-main">

        <header class="topbar">

          <div class="topbar-left">
            <p class="topbar-title">
              ${escapeHtml(pageTitle(active))}
            </p>
          </div>

          <div class="topbar-actions">

            <a
              class="button small topbar-create"
              href="#/bookings/new"
            >
              <span aria-hidden="true">+</span>
              New booking
            </a>

            <a
              href="#/notifications"
              class="topbar-icon-button"
              aria-label="Notifications"
              title="Notifications"
            >
              ${icons.notifications}
            </a>

            <a
              href="#/settings"
              class="profile-control"
              title="Account settings"
            >
              <span class="avatar">
                ${hostInitials}
              </span>

              <span class="profile-control-text">
                <strong>${escapeHtml(hostName)}</strong>
                <small>Host account</small>
              </span>
            </a>

          </div>

        </header>

        <section class="page">
          ${content}
        </section>

      </main>

      <nav class="mobile-nav" aria-label="Mobile navigation">
        ${mobile}
      </nav>

    </div>
  `;
}

function heading(title, description = '', action = '') { return `<header class="page-heading"><div><p class="eyebrow">Hostvero operations</p><h1>${escapeHtml(title)}</h1>${description ? `<p class="muted">${escapeHtml(description)}</p>` : ''}</div>${action}</header>`; }
function loadingCards() { return `<div class="grid stats">${Array.from({ length: 4 }, () => '<div class="card stat-card"><div class="skeleton" style="width:55%"></div><div class="skeleton" style="width:65%;height:32px;margin-top:18px"></div></div>').join('')}</div>`; }
function statusClass(status) { return String(status || '').toLowerCase().replaceAll('_', '-'); }
function bookingStatusFilter(bookings, filter) { if (!filter || filter === 'ALL') return bookings; return bookings.filter(booking => booking.status === filter); }
function mapById(values) { return new Map(values.map(value => [value.id, value])); }

async function hydrateBasics() {
  const [properties, guests, bookings] = await apiMany(['/api/properties', '/api/guests', '/api/bookings']);
  state.properties = properties; state.guests = guests; state.bookings = bookings; return state;
}

async function renderOverview() {
  app.innerHTML = hostShell(
    'overview',
    `
      ${heading(
        'Overview',
        'See what needs your attention across bookings, guests and properties.'
      )}

      <section class="grid stats">
        ${Array.from({ length: 4 }, () => `
          <article class="card stat-card">
            <div class="skeleton" style="width:46%"></div>
            <div class="skeleton" style="width:32%;height:30px;margin-top:14px"></div>
          </article>
        `).join('')}
      </section>
    `
  );

  bindShell();

  try {
    const { properties, guests, bookings } = await hydrateBasics();
    const notifications = await get('/api/notifications');

    const today = new Date().toISOString().slice(0, 10);

    const activeProperties = properties.filter(
      property => property.active
    ).length;

    const arrivalsToday = bookings.filter(
      booking =>
        booking.checkInDate === today &&
        !['CANCELLED', 'COMPLETED'].includes(booking.status)
    );

    const departuresToday = bookings.filter(
      booking =>
        booking.checkOutDate === today &&
        !['CANCELLED'].includes(booking.status)
    );

    const pendingPayments = bookings.filter(
      booking => booking.status === 'PENDING_PAYMENT'
    );

    const upcoming = bookings
      .filter(
        booking =>
          booking.checkInDate >= today &&
          !['CANCELLED', 'COMPLETED'].includes(booking.status)
      )
      .sort((a, b) => a.checkInDate.localeCompare(b.checkInDate));

    const propertyMap = mapById(properties);
    const guestMap = mapById(guests);

    const upcomingRows = upcoming
      .slice(0, 6)
      .map(booking => {
        const guest = guestMap.get(booking.guestId);
        const property = propertyMap.get(booking.propertyId);

        return `
          <tr>
            <td>
              <a href="#/bookings/${booking.id}">
                ${escapeHtml(guest?.fullName || 'Guest pending')}
              </a>
            </td>

            <td>
              ${escapeHtml(property?.name || 'Property')}
            </td>

            <td>
              ${formatDate(booking.checkInDate)}
            </td>

            <td>
              ${formatDate(booking.checkOutDate)}
            </td>

            <td>
              ${badge(booking.status)}
            </td>
          </tr>
        `;
      })
      .join('');

    const reminders = notifications
      .slice(0, 5)
      .map(notification => `
        <div class="timeline-item">
          <span class="timeline-dot"></span>

          <div>
            <strong>
              ${escapeHtml(notificationLabel(notification.type))}
            </strong>

            <p>
              ${formatDateTime(notification.scheduledAt)}
              ·
              ${escapeHtml(titleCase(notification.status))}
            </p>
          </div>
        </div>
      `)
      .join('');

    const main = `
      ${heading(
        'Overview',
        'See what needs your attention across bookings, guests and properties.'
      )}

      <section class="grid stats">

        <article class="card stat-card">
          <span class="stat-label">Arrivals today</span>
          <p class="stat-value">${arrivalsToday.length}</p>
          <span class="stat-note">
            ${arrivalsToday.length === 1 ? 'guest arriving' : 'guests arriving'}
          </span>
        </article>

        <article class="card stat-card">
          <span class="stat-label">Departures today</span>
          <p class="stat-value">${departuresToday.length}</p>
          <span class="stat-note">
            ${departuresToday.length === 1 ? 'checkout today' : 'checkouts today'}
          </span>
        </article>

        <article class="card stat-card">
          <span class="stat-label">Pending payments</span>
          <p class="stat-value">${pendingPayments.length}</p>
          <span class="stat-note">Needs attention</span>
        </article>

        <article class="card stat-card">
          <span class="stat-label">Active properties</span>
          <p class="stat-value">${activeProperties}</p>
          <span class="stat-note">Available in Hostvero</span>
        </article>

      </section>

      <section class="grid two-col">

        <article class="card card-pad">

          <div class="section-title">
            <h2>Upcoming stays</h2>
            <a href="#/bookings">View all</a>
          </div>

          ${
            upcomingRows
              ? `
                <div class="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Guest</th>
                        <th>Property</th>
                        <th>Check-in</th>
                        <th>Check-out</th>
                        <th>Status</th>
                      </tr>
                    </thead>

                    <tbody>
                      ${upcomingRows}
                    </tbody>
                  </table>
                </div>
              `
              : `
                ${emptyState(
                  '⌂',
                  'No upcoming stays',
                  'New bookings will appear here.',
                  '<a class="button small" href="#/bookings/new">Create booking</a>'
                )}
              `
          }

        </article>

        <aside class="card card-pad">

          <div class="section-title">
            <h2>Needs attention</h2>
            <a href="#/notifications">View all</a>
          </div>

          ${
            reminders
              ? `
                <div class="timeline">
                  ${reminders}
                </div>
              `
              : `
                ${emptyState(
                  '✓',
                  'Nothing pending',
                  'You have no scheduled reminders right now.'
                )}
              `
          }

        </aside>

      </section>

      <section
        class="card card-pad"
        style="margin-top:1rem"
      >

        <div class="section-title">
          <h2>Quick actions</h2>
        </div>

        <div class="button-row">

          <a class="button" href="#/bookings/new">
            New booking
          </a>

          <a class="button secondary" href="#/properties/new">
            Add property
          </a>

          <a class="button secondary" href="#/bookings">
            View bookings
          </a>

        </div>

      </section>
    `;

    app.innerHTML = hostShell('overview', main);

    bindShell();

  } catch (error) {
    showHostError('overview', error);
  }
}

function bookingTable(bookings, properties, guests) {
  const propertyMap = mapById(properties);
  const guestMap = mapById(guests);

  return `
    <div class="table-wrap booking-table-wrap">

      <table class="booking-table">

        <thead>
          <tr>
            <th>Guest</th>
            <th>Property</th>
            <th>Check-in</th>
            <th>Check-out</th>
            <th>Amount</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>

        <tbody>

          ${bookings.map(booking => {

            const guest = guestMap.get(booking.guestId);
            const property = propertyMap.get(booking.propertyId);

            return `
              <tr>

                <td>
                  <a
                    class="booking-guest-link"
                    href="#/bookings/${booking.id}"
                  >
                    ${escapeHtml(
                      guest?.fullName || 'Guest pending'
                    )}
                  </a>
                </td>

                <td>
                  ${escapeHtml(
                    property?.name || 'Property'
                  )}
                </td>

                <td>
                  ${formatDate(
                    booking.checkInDate
                  )}
                </td>

                <td>
                  ${formatDate(
                    booking.checkOutDate
                  )}
                </td>

                <td>
                  <strong>
                    ${formatMoney(
                      booking.totalAmount,
                      booking.currency
                    )}
                  </strong>
                </td>

                <td>
                  ${badge(booking.status)}
                </td>

                <td>
                  <a
                    class="button small secondary"
                    href="#/bookings/${booking.id}"
                  >
                    View
                  </a>
                </td>

              </tr>
            `;

          }).join('')}

        </tbody>

      </table>

    </div>
  `;
}


async function renderBookings(route) {
  const isNew = route === 'bookings/new';

  const bookingId =
    route.split('/')[1];

  if (isNew) {
    return renderBookingForm();
  }

  if (bookingId) {
    return renderBookingDetail(bookingId);
  }


  app.innerHTML = hostShell(
    'bookings',
    `
      ${heading(
        'Bookings',
        'Manage upcoming stays, payments and booking status.',
        '<a class="button" href="#/bookings/new">+ New booking</a>'
      )}

      <section class="card card-pad">
        <div
          class="skeleton"
          style="height:200px"
        ></div>
      </section>
    `
  );

  bindShell();


  try {

    const {
      properties,
      guests,
      bookings
    } = await hydrateBasics();


    const filters = [
      ['ALL', 'All'],
      ['PENDING_PAYMENT', 'Pending payment'],
      ['PENDING_CONFIRMATION', 'Pending confirmation'],
      ['CONFIRMED', 'Confirmed'],
      ['CHECKED_IN', 'Checked in'],
      ['COMPLETED', 'Completed'],
      ['CANCELLED', 'Cancelled']
    ];


    const controls = `
      <div
        class="booking-filters"
        role="group"
        aria-label="Booking status filters"
      >

        ${filters.map(
          ([value, label], index) => `
            <button
              class="booking-filter ${index === 0 ? 'active' : ''}"
              data-booking-filter="${value}"
              type="button"
            >
              ${label}
            </button>
          `
        ).join('')}

      </div>
    `;


    const content = `
      ${heading(
        'Bookings',
        'Manage upcoming stays, payments and booking status.',
        '<a class="button" href="#/bookings/new">+ New booking</a>'
      )}


      <section class="card booking-list-card">

        <header class="booking-list-header">

          <div>
            <h2>
              ${bookings.length}
              booking${bookings.length === 1 ? '' : 's'}
            </h2>

            <p>
              Your current booking activity.
            </p>
          </div>

        </header>


        ${controls}


        <div
          id="booking-results"
          class="booking-results"
        >

          ${
            bookings.length
              ? bookingTable(
                  bookings,
                  properties,
                  guests
                )
              : emptyState(
                  '⌂',
                  'No bookings yet',
                  'Your bookings will appear here after you create your first stay.',
                  '<a class="button" href="#/bookings/new">Create booking</a>'
                )
          }

        </div>

      </section>
    `;


    app.innerHTML = hostShell(
      'bookings',
      content
    );

    bindShell();


    $$('[data-booking-filter]')
      .forEach(button => {

        button.addEventListener(
          'click',
          () => {

            $$('[data-booking-filter]')
              .forEach(item =>
                item.classList.remove('active')
              );

            button.classList.add('active');


            const filtered =
              bookingStatusFilter(
                bookings,
                button.dataset.bookingFilter
              );


            $('#booking-results').innerHTML =
              filtered.length
                ? bookingTable(
                    filtered,
                    properties,
                    guests
                  )
                : emptyState(
                    '⌕',
                    'No matching bookings',
                    'There are no bookings with this status.'
                  );

          }
        );

      });


  } catch (error) {

    showHostError(
      'bookings',
      error
    );

  }
}


async function renderBookingForm(existing = null) {
  app.innerHTML = hostShell(
    'bookings',
    `
      ${heading(
        existing ? 'Edit booking' : 'New booking',
        existing
          ? 'Update the stay details and booking status.'
          : 'Choose the property, dates and final amount for this stay.',
        '<a class="button secondary" href="#/bookings">← Bookings</a>'
      )}

      <section class="card card-pad">
        <div
          class="skeleton"
          style="height:320px"
        ></div>
      </section>
    `
  );

  bindShell();

  try {
    const {
      properties
    } = await hydrateBasics();

    if (!properties.length) {
      app.innerHTML = hostShell(
        'bookings',
        `
          ${heading(
            'New booking',
            'Create a property before creating your first booking.',
            '<a class="button secondary" href="#/bookings">← Bookings</a>'
          )}

          <section class="card card-pad">

            ${emptyState(
              '⌂',
              'No properties available',
              'Add your first property before creating a booking.',
              '<a class="button" href="#/properties/new">Add property</a>'
            )}

          </section>
        `
      );

      bindShell();
      return;
    }

    const value = (
      key,
      fallback = ''
    ) =>
      escapeHtml(
        existing?.[key] ?? fallback
      );

    const propertyOptions =
      properties
        .filter(
          property =>
            property.active ||
            property.id === existing?.propertyId
        )
        .map(
          property => `
            <option
              value="${property.id}"
              ${
                property.id === existing?.propertyId
                  ? 'selected'
                  : ''
              }
            >
              ${escapeHtml(property.name)}
              ·
              ${formatMoney(
                property.defaultNightlyRate,
                property.currency
              )}
            </option>
          `
        )
        .join('');

    const statusOptions = [
      'PENDING_PAYMENT',
      'PENDING_CONFIRMATION',
      'CONFIRMED',
      'CHECKED_IN',
      'COMPLETED',
      'CANCELLED'
    ]
      .map(
        status => `
          <option
            value="${status}"
            ${
              status ===
              (
                existing?.status ||
                'PENDING_CONFIRMATION'
              )
                ? 'selected'
                : ''
            }
          >
            ${titleCase(status)}
          </option>
        `
      )
      .join('');

    const form = `
      <form
        id="booking-form"
        class="booking-form"
      >

        <section class="booking-form-section">

          <header class="booking-form-section-header">
            <h2>Stay</h2>

            <p>
              Choose the property and stay dates.
            </p>
          </header>

          <div class="form-grid">

            <div class="field full">

              <label for="booking-property">
                Property
              </label>

              <select
                id="booking-property"
                name="propertyId"
                required
              >
                ${propertyOptions}
              </select>

            </div>

            <div class="field">

              <label for="booking-checkin">
                Check-in
              </label>

              <input
                id="booking-checkin"
                name="checkInDate"
                type="date"
                required
                value="${dateInput(
                  existing?.checkInDate
                )}"
              >

            </div>

            <div class="field">

              <label for="booking-checkout">
                Check-out
              </label>

              <input
                id="booking-checkout"
                name="checkOutDate"
                type="date"
                required
                value="${dateInput(
                  existing?.checkOutDate
                )}"
              >

            </div>

            <div class="field full">

              <label>
                Availability
              </label>

              <div
                id="booking-availability"
                class="date-feedback"
              >
                Choose your stay dates.
              </div>

            </div>

          </div>

        </section>

        <section class="booking-form-section">

          <header class="booking-form-section-header">
            <h2>Booking amount</h2>

            <p>
              Confirm the final amount the guest will pay.
            </p>
          </header>

          <div class="form-grid">

            <div class="field">

              <label for="booking-total">
                Final amount
              </label>

              <input
                id="booking-total"
                name="totalAmount"
                type="number"
                min="0"
                step="0.01"
                required
                value="${value(
                  'totalAmount',
                  ''
                )}"
              >

            </div>

            <div class="field">

              <label for="booking-currency">
                Currency
              </label>

              <input
                id="booking-currency"
                name="currency"
                maxlength="3"
                required
                value="${value(
                  'currency',
                  properties[0].currency
                )}"
              >

            </div>

          </div>

        </section>

        <section class="booking-form-section">

          <header class="booking-form-section-header">
            <h2>Booking details</h2>

            <p>
              The guest will provide their personal information through the secure guest link.
            </p>
          </header>

          <div class="form-grid">

            ${
              existing
                ? `
                  <div class="field">

                    <label for="booking-status">
                      Status
                    </label>

                    <select
                      id="booking-status"
                      name="status"
                    >
                      ${statusOptions}
                    </select>

                  </div>
                `
                : `
                  <input
                    type="hidden"
                    name="status"
                    value="PENDING_CONFIRMATION"
                  >
                `
            }

            <div class="field full">

              <label for="booking-notes">
                Private notes
                <span class="muted">
                  (optional)
                </span>
              </label>

              <textarea
                id="booking-notes"
                name="notes"
                maxlength="2000"
                placeholder="Notes about this stay..."
              >${value('notes')}</textarea>

            </div>

          </div>

        </section>

        <div class="form-actions">

          <a
            class="button secondary"
            href="#/bookings"
          >
            Cancel
          </a>

          <button
            class="button"
            type="submit"
          >
            ${
              existing
                ? 'Save changes'
                : 'Create booking'
            }
          </button>

        </div>

      </form>
    `;

    app.innerHTML = hostShell(
      'bookings',
      `
        ${heading(
          existing ? 'Edit booking' : 'New booking',
          existing
            ? 'Update the stay details and booking status.'
            : 'Choose the property, dates and final amount for this stay.',
          '<a class="button secondary" href="#/bookings">← Bookings</a>'
        )}

        <section class="card card-pad booking-form-card">
          ${form}
        </section>
      `
    );

    bindShell();

    const updateAmount = () => {
      const property =
        properties.find(
          item =>
            item.id ===
            $('#booking-property').value
        );

      const checkIn =
        $('#booking-checkin').value;

      const checkOut =
        $('#booking-checkout').value;

      if (
        property &&
        checkIn &&
        checkOut &&
        !existing
      ) {
        const nights =
          Math.max(
            0,
            Math.round(
              (
                new Date(checkOut) -
                new Date(checkIn)
              ) /
              86400000
            )
          );

        if (nights) {
          $('#booking-total').value =
            (
              Number(
                property.defaultNightlyRate
              ) *
              nights
            ).toFixed(2);

          $('#booking-currency').value =
            property.currency;
        }
      }
    };

    const checkAvailability = async () => {
      const propertyId =
        $('#booking-property').value;

      const checkIn =
        $('#booking-checkin').value;

      const checkOut =
        $('#booking-checkout').value;

      const feedback =
        $('#booking-availability');

      if (
        !propertyId ||
        !checkIn ||
        !checkOut
      ) {
        feedback.className =
          'date-feedback';

        feedback.textContent =
          'Choose your stay dates.';

        return false;
      }

      if (checkOut <= checkIn) {
        feedback.className =
          'date-feedback unavailable';

        feedback.textContent =
          'Check-out must be after check-in.';

        return false;
      }

      try {
        const result = await get(
          `/api/properties/${propertyId}/availability?checkIn=${checkIn}&checkOut=${checkOut}`
        );

        feedback.className =
          `date-feedback ${
            result.available
              ? 'available'
              : 'unavailable'
          }`;

        feedback.textContent =
          result.available
            ? 'These dates are available.'
            : 'These dates are not available.';

        return result.available;

      } catch {
        feedback.className =
          'date-feedback';

        feedback.textContent =
          'Availability will be confirmed when you save.';

        return true;
      }
    };

    [
      'booking-property',
      'booking-checkin',
      'booking-checkout'
    ].forEach(id => {
      $(`#${id}`).addEventListener(
        'change',
        () => {
          updateAmount();
          checkAvailability();
        }
      );
    });

    $('#booking-form').addEventListener(
      'submit',
      async event => {
        event.preventDefault();

        const button = $(
          'button[type="submit"]',
          event.currentTarget
        );

        setButtonBusy(
          button,
          true,
          existing
            ? 'Saving…'
            : 'Creating…'
        );

        const data =
          Object.fromEntries(
            new FormData(
              event.currentTarget
            )
          );

        data.currency =
          data.currency.toUpperCase();

        data.totalAmount =
          Number(data.totalAmount);

        if (!await checkAvailability()) {
          setButtonBusy(
            button,
            false
          );

          toast(
            'Those dates are not available.',
            'error'
          );

          return;
        }

        try {
         const saved = existing
           ? await put(
               `/api/bookings/${existing.id}`,
               data
             )
           : await post(
               '/api/bookings',
               data
             );

         if (existing) {
           toast(
             'Booking updated.',
             'success'
           );

           go(
            `bookings/${saved.id}`
          );

          return;
        }

        const guestLink = await post(
          `/api/bookings/${saved.id}/guest-link`,
          {}
        );

        toast(
          'Booking created and guest link generated.',
          'success'
        );

        const publicUrl =
          `${window.location.origin}/guest/${guestLink.token}`;

        sessionStorage.setItem(
          `hostvero.guest-link.${saved.id}`,
          publicUrl
        );

        go(
          `bookings/${saved.id}`
        );

        } catch (error) {
          handleFormError(
            error,
            event.currentTarget
          );

        } finally {
          setButtonBusy(
            button,
            false
          );
        }
      }
    );

  } catch (error) {
    showHostError(
      'bookings',
      error
    );
  }
}
async function renderBookingDetail(id) {
  app.innerHTML = hostShell(
    'bookings',
    `
      ${heading(
        'Booking details',
        'Review the stay, payment status and booking actions.',
        '<a class="button secondary" href="#/bookings">← Bookings</a>'
      )}

      <section class="card card-pad">
        <div class="skeleton" style="height:320px"></div>
      </section>
    `
  );

  bindShell();

  try {
    const [booking, properties, guests] = await Promise.all([
      get(`/api/bookings/${id}`),
      get('/api/properties'),
      get('/api/guests')
    ]);

    const property = properties.find(
      item => item.id === booking.propertyId
    );

    const guest = guests.find(
      item => item.id === booking.guestId
    );

    const guestLinkUrl = sessionStorage.getItem(
      `hostvero.guest-link.${booking.id}`
    );
    const receiptGuestUrl = guestLinkUrl
      ? `${guestLinkUrl.replace(/\/$/, '')}/receipt/document`
      : null;

    const [payments, notifications, receipt] = await Promise.all([
      get(`/api/bookings/${id}/payments`),
      get(`/api/bookings/${id}/notifications`),
      get(`/api/bookings/${id}/receipt`).catch(() => null)
    ]);

    state.properties = properties;
    state.guests = guests;


    const paymentRows = payments
      .map(payment => `
        <div class="booking-detail-row">
          <div>
            <strong>
              ${escapeHtml(titleCase(payment.provider))}
            </strong>

            <span>
              ${formatMoney(
                payment.amount,
                payment.currency
              )}
            </span>
          </div>

          ${badge(payment.status)}
        </div>
      `)
      .join('');

    const hasSucceededPayment = payments.some(payment => payment.status === 'SUCCEEDED');


    const notificationRows = notifications
      .map(notification => `
        <div class="timeline-item">

          <span class="timeline-dot"></span>

          <div>
            <strong>
              ${escapeHtml(
                notificationLabel(notification.type)
              )}
            </strong>

            <p>
              ${formatDateTime(notification.scheduledAt)}
              ·
              ${escapeHtml(
                titleCase(notification.status)
              )}
            </p>
          </div>

        </div>
      `)
      .join('');


    const content = `
      ${heading(
        'Booking details',
        `${escapeHtml(property?.name || 'Property')} · ${formatDate(booking.checkInDate)} – ${formatDate(booking.checkOutDate)}`,
        '<a class="button secondary" href="#/bookings">← Bookings</a>'
      )}


      <section class="card booking-detail-header">

        <div class="booking-detail-header-main">

          <div class="booking-detail-status">
            ${badge(booking.status)}
          </div>

          <h1>
            ${escapeHtml(
              guest?.fullName || 'Guest pending'
            )}
          </h1>

          <p>
            ${escapeHtml(
              property?.name || 'Property'
            )}
          </p>

        </div>


        <div class="booking-detail-header-meta">

          <div>
            <span>Check-in</span>
            <strong>
              ${formatDate(booking.checkInDate)}
            </strong>
          </div>

          <div>
            <span>Check-out</span>
            <strong>
              ${formatDate(booking.checkOutDate)}
            </strong>
          </div>

          <div>
            <span>Total</span>
            <strong>
              ${formatMoney(
                booking.totalAmount,
                booking.currency
              )}
            </strong>
          </div>

        </div>


        <div class="booking-detail-header-actions">

          <button
            class="button secondary"
            data-edit-booking
            type="button"
          >
            Edit booking
          </button>

          ${
            hasSucceededPayment
              ? '<span class="muted">Paid bookings cannot be cancelled here.</span>'
              : `
                <button
                  class="button secondary"
                  data-cancel-booking
                  type="button"
                >
                  Cancel
                </button>
              `
          }

        </div>

      </section>


      <section class="grid booking-detail-grid">

        <article class="card booking-detail-card">

          <header class="booking-detail-card-header">
            <h2>Stay details</h2>
          </header>

          <div class="booking-detail-list">

            <div class="booking-detail-row">
              <span>Property</span>
              <strong>
                ${escapeHtml(
                  property?.name || '—'
                )}
              </strong>
            </div>

            <div class="booking-detail-row">
              <span>Guest</span>
              <strong>
                ${escapeHtml(
                  guest?.fullName || 'Guest pending'
                )}
              </strong>
            </div>

            <div class="booking-detail-row">
              <span>Check-in</span>
              <strong>
                ${formatDate(
                  booking.checkInDate
                )}
              </strong>
            </div>

            <div class="booking-detail-row">
              <span>Check-out</span>
              <strong>
                ${formatDate(
                  booking.checkOutDate
                )}
              </strong>
            </div>

            <div class="booking-detail-row">
              <span>Amount</span>
              <strong>
                ${formatMoney(
                  booking.totalAmount,
                  booking.currency
                )}
              </strong>
            </div>

            <div class="booking-detail-row">
              <span>Guest access</span>
              <div class="field">
                <label class="sr-only" for="guest-access-policy">Guest access policy</label>
                <select id="guest-access-policy" aria-label="Guest access policy">
                  <option value="AFTER_PAYMENT" ${booking.guestAccessPolicy !== 'BEFORE_PAYMENT' ? 'selected' : ''}>After payment</option>
                  <option value="BEFORE_PAYMENT" ${booking.guestAccessPolicy === 'BEFORE_PAYMENT' ? 'selected' : ''}>Before payment</option>
                </select>
                <small class="muted" id="guest-access-policy-help">${booking.guestAccessPolicy === 'BEFORE_PAYMENT' ? 'Guest stay details are available before payment; payment is still required.' : 'Guest stay details unlock after verified payment.'}</small>
              </div>
            </div>

          </div>

        </article>


        <aside class="card booking-detail-card">

          <header class="booking-detail-card-header">
            <h2>Actions</h2>
          </header>

          <div class="booking-detail-actions">

            <button
              class="button secondary"
              data-extend
              type="button"
            >
              Extend stay
            </button>

            <button
              class="button secondary"
              data-book-again
              type="button"
            >
              Book again
            </button>

            ${
              guestLinkUrl
                ? `
                  <div class="field">
                    <label for="guest-link-value">Secure guest link</label>
                    <input id="guest-link-value" readonly value="${escapeHtml(guestLinkUrl)}">
                  </div>
                  <button class="button secondary" data-copy-link type="button">Copy link</button>
                  <button class="button secondary" data-email-guest-link type="button">Send by email</button>
                `
                : `
                  <button
                    class="button secondary"
                    data-guest-link
                    type="button"
                  >
                    Create guest link
                  </button>
                `
            }

            ${
              booking.status === 'PENDING_PAYMENT' && !hasSucceededPayment
                ? `
                  <button
                    class="button"
                    data-initiate-payment
                    type="button"
                  >
                    Initiate payment
                  </button>
                  <button
                    class="button secondary"
                    data-confirm-cash-payment
                    type="button"
                  >
                    Confirm cash payment
                  </button>
                `
                : ''
            }

            <button class="button secondary" data-send-notification type="button">Send notification</button>

          </div>

        </aside>

      </section>


      <section class="grid booking-detail-grid booking-detail-lower">

        <article class="card booking-detail-card">

          <header class="booking-detail-card-header">

            <div>
              <h2>Payments & receipt</h2>
            </div>

          </header>


          <div class="booking-detail-card-body">

            ${
              paymentRows
                ? `
                  <div class="booking-detail-list">
                    ${paymentRows}
                  </div>
                `
                : `
                  <p class="booking-detail-empty">
                    No payment has been initiated.
                  </p>
                `
            }


            ${
              receipt
                ? `
                  <div class="booking-receipt">

                    <div>
                      <span>Receipt</span>

                      <strong>
                        ${escapeHtml(
                          receipt.receiptNumber
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Amount</span>

                      <strong>
                        ${formatMoney(
                          receipt.amount,
                          receipt.currency
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Issued</span>

                      <strong>
                        ${formatDateTime(
                          receipt.issuedAt
                        )}
                      </strong>
                    </div>

                  </div>

                  <div class="booking-detail-actions">
                    <button class="button secondary" data-view-receipt type="button">View receipt</button>
                    <button class="button secondary" data-download-receipt type="button">Download receipt</button>
                    ${receiptGuestUrl ? `
                      <button class="button secondary" data-copy-receipt-link type="button">Copy receipt link</button>
                    ` : ''}
                  </div>
                `
                : ''
            }

          </div>

        </article>


        <article class="card booking-detail-card">

          <header class="booking-detail-card-header">
            <h2>Notifications</h2>
          </header>


          <div class="booking-detail-card-body">

            ${
              notificationRows
                ? `
                  <div class="timeline">
                    ${notificationRows}
                  </div>
                `
                : `
                  <p class="booking-detail-empty">
                    No reminders scheduled.
                  </p>
                `
            }

          </div>

        </article>

      </section>
    `;


    app.innerHTML = hostShell(
      'bookings',
      content
    );

    bindShell();


    $('[data-edit-booking]').addEventListener(
      'click',
      () => renderBookingForm(booking)
    );


    $('[data-cancel-booking]')?.addEventListener(
      'click',
      async () => {

        if (
          await confirmDialog({
            title: 'Cancel this booking?',
            message:
              'This will cancel the booking. This action cannot be undone from this screen.',
            confirmLabel:
              'Cancel booking',
            danger: true
          })
        ) {

          try {

            await del(
              `/api/bookings/${id}`
            );

            toast(
              'Booking cancelled.',
              'success'
            );

            renderBookingDetail(id);

          } catch (error) {

            toast(
              error.message,
              'error'
            );

          }

        }

      }
    );


    $('[data-guest-link]')?.addEventListener(
      'click',
      () => createGuestLink(id)
    );

    $('#guest-access-policy')?.addEventListener('change', async event => {
      const control = event.currentTarget;
      control.disabled = true;
      try {
        await put(`/api/bookings/${id}/guest-access-policy`, { policy: control.value });
        toast('Guest access policy updated.', 'success');
        renderBookingDetail(id);
      } catch (error) {
        toast(error.message, 'error');
        control.value = booking.guestAccessPolicy || 'AFTER_PAYMENT';
        control.disabled = false;
      }
    });

    $('[data-copy-link]')?.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(guestLinkUrl);
        toast('Guest link copied.', 'success');
      } catch {
        $('#guest-link-value').select();
        toast('Select and copy the guest link.', '');
      }
    });

    $('[data-email-guest-link]')?.addEventListener('click', () => {
      const token = guestLinkUrl ? guestLinkUrl.split('/').filter(Boolean).pop() : null;
      if (!token) {
        toast('Create or recover the secure guest link before sending it by email.', 'error');
        return;
      }
      post(`/api/bookings/${id}/guest-link/email`, { token })
        .then(notification => toast(
          notification.status === 'SENT' ? 'Guest link email sent.' : 'Guest link email could not be delivered.',
          notification.status === 'SENT' ? 'success' : 'error'
        ))
        .catch(error => toast(error.message, 'error'));
    });

    $('[data-send-notification]')?.addEventListener('click', () => openManualNotification(id));


    $('[data-initiate-payment]')
      ?.addEventListener(
        'click',
        () =>
          initiatePayment(
            `/api/bookings/${id}/payments`,
            'Payment initiated. Complete verification through the configured provider callback.'
          )
      );

    $('[data-confirm-cash-payment]')?.addEventListener('click', async () => {
      if (!await confirmDialog({
        title: 'Confirm cash payment?',
        message: 'Confirm that you have received the full booking amount in cash?',
        confirmLabel: 'Confirm cash payment'
      })) return;

      try {
        await post(`/api/bookings/${id}/payments/cash/confirm`, {});
        toast('Cash payment confirmed.', 'success');
        renderBookingDetail(id);
      } catch (error) {
        toast(error.message, 'error');
      }
    });

    $('[data-view-receipt]')?.addEventListener('click', () => {
      openHostReceiptDocument(id, false).catch(error => toast(error.message, 'error'));
    });

    $('[data-download-receipt]')?.addEventListener('click', () => {
      openHostReceiptDocument(id, true).catch(error => toast(error.message, 'error'));
    });

    $('[data-copy-receipt-link]')?.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(receiptGuestUrl);
        toast('Receipt link copied.', 'success');
      } catch {
        toast('Your browser could not copy the receipt link.', 'error');
      }
    });


    $('[data-extend]').addEventListener(
      'click',
      () =>
        extendStay(
          booking,
          property,
          () => renderBookingDetail(id)
        )
    );


    $('[data-book-again]').addEventListener(
      'click',
      () =>
        bookAgain(
          booking,
          property,
          () => renderBookingDetail(id)
        )
    );

  } catch (error) {

    showHostError(
      'bookings',
      error
    );

  }
}

async function createGuestLink(bookingId) { try { const link = await post(`/api/bookings/${bookingId}/guest-link`, {}); const url = `${location.origin}/guest/${link.token}`; sessionStorage.setItem(`hostvero.guest-link.${bookingId}`, url); const modal = openModal({ title: 'Guest link created', body: `<p class="notice">Copy this link now. For security, Hostvero never retrieves it from a token hash later.</p><div class="field" style="margin-top:1rem"><label for="guest-link-value">Secure guest link</label><input id="guest-link-value" readonly value="${escapeHtml(url)}"></div>`, actions: `<button class="button" type="button" data-copy-link>Copy link</button>` }); $('[data-copy-link]', modal.root).addEventListener('click', async () => { try { await navigator.clipboard.writeText(url); toast('Guest link copied.', 'success'); } catch { $('#guest-link-value', modal.root).select(); toast('Select and copy the link.', ''); } }); } catch (error) { toast(error.message, 'error'); } }

function openManualNotification(bookingId) {
  const modal = openModal({ title: 'Send notification', body: '<form id="manual-notification-form"><div class="field"><label for="notification-channel">Channel</label><select id="notification-channel" name="channel"><option value="EMAIL">Email</option></select></div><div class="field"><label for="notification-subject">Subject</label><input id="notification-subject" name="subject" maxlength="200" required></div><div class="field"><label for="notification-message">Message</label><textarea id="notification-message" name="message" maxlength="4000" required></textarea></div><div class="form-actions"><button class="button secondary" type="button" data-close>Cancel</button><button class="button" type="submit">Send</button></div></form>' });
  $('[data-close]', modal.root).addEventListener('click', modal.close);
  $('#manual-notification-form', modal.root).addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('button[type="submit"]', event.currentTarget);
    setButtonBusy(button, true, 'Sending…');
    try {
      const result = await post(`/api/bookings/${bookingId}/notifications/manual`, Object.fromEntries(new FormData(event.currentTarget)));
      modal.close();
      toast(result.status === 'SENT' ? 'Notification sent.' : 'Notification could not be delivered.', result.status === 'SENT' ? 'success' : 'error');
    } catch (error) { handleFormError(error, event.currentTarget); }
    finally { setButtonBusy(button, false); }
  });
}

async function openHostReceiptDocument(bookingId, download) {
  const receiptWindow = download ? null : window.open('', '_blank');
  try {
    const response = await fetch(`/api/bookings/${bookingId}/receipt/document${download ? '?download=true' : ''}`, {
      headers: { Authorization: `Bearer ${session.token}` }
    });
    if (!response.ok) throw new ApiError(response.status, 'We could not open that receipt.');
    const documentUrl = URL.createObjectURL(await response.blob());
    if (download) {
      const link = document.createElement('a');
      link.href = documentUrl;
      link.download = 'hostvero-receipt.html';
      link.click();
    } else if (receiptWindow) {
      receiptWindow.location.href = documentUrl;
    } else {
      URL.revokeObjectURL(documentUrl);
      throw new Error('Please allow pop-ups to view the receipt.');
    }
    window.setTimeout(() => URL.revokeObjectURL(documentUrl), 60000);
  } catch (error) {
    receiptWindow?.close();
    throw error;
  }
}

function initiatePayment(path, successMessage) { const modal = openModal({ title: 'Initiate payment', body: '<p class="muted">Choose the configured payment provider. Payment success is always verified server-side.</p><div class="field"><label for="payment-provider">Provider</label><select id="payment-provider"><option value="PAYSTACK">Paystack (M-Pesa or card)</option><option value="STRIPE">Stripe</option></select></div>', actions: '<button class="button" type="button" data-start-payment>Initiate payment</button>' }); $('[data-start-payment]', modal.root).addEventListener('click', async event => { const button = event.currentTarget; setButtonBusy(button, true, 'Starting…'); try { const result = await post(path, { provider: $('#payment-provider', modal.root).value }); modal.close(); openModal({ title: 'Payment initiated', body: `<div class="notice">${escapeHtml(successMessage)}</div><div class="detail-list" style="margin-top:1rem"><div class="detail-row"><span>Amount</span><strong>${formatMoney(result.amount, result.currency)}</strong></div>${Number(result.serviceFee || 0) > 0 ? `<div class="detail-row"><span>Hostvero service fee</span><strong>${formatMoney(result.serviceFee, result.currency)}</strong></div>` : ''}<div class="detail-row"><span>Reference</span><strong>${escapeHtml(result.providerReference)}</strong></div><div class="detail-row"><span>Status</span>${badge(result.status)}</div></div>` }); } catch (error) { toast(error.message, 'error'); setButtonBusy(button, false); } }); }

function extendStay(booking, property, refresh) { const modal = openModal({ title: 'Extend stay', body: `<p class="muted">Current checkout: <strong>${formatDate(booking.checkOutDate)}</strong></p><form id="extend-form"><div class="field"><label for="extension-checkout">New checkout date</label><input id="extension-checkout" type="date" min="${dateInput(booking.checkOutDate)}" required></div><div id="extension-feedback" class="date-feedback">Choose a new date. Availability is checked by Hostvero.</div><div class="form-actions"><button class="button secondary" type="button" data-close>Cancel</button><button class="button" type="submit">Request extension</button></div></form>` }); $('[data-close]', modal.root).addEventListener('click', modal.close); $('#extend-form', modal.root).addEventListener('submit', async event => { event.preventDefault(); const button = $('button[type="submit"]', event.currentTarget); setButtonBusy(button, true); const newCheckOutDate = $('#extension-checkout', modal.root).value; try { const response = await post(`/api/bookings/${booking.id}/extend`, { newCheckOutDate }); modal.close(); if (response.status === 'PENDING_PAYMENT') { const paymentModal = openModal({ title: 'Extension payment required', body: `<p class="notice warning">Your booking has not changed yet. ${formatMoney(response.additionalAmount, response.currency)} is due for ${response.addedNights} additional night${response.addedNights === 1 ? '' : 's'}.</p><p class="muted">Checkout updates only after Hostvero verifies the additional payment.</p>`, actions: '<button class="button" type="button" data-extension-payment>Initiate extension payment</button>' }); $('[data-extension-payment]', paymentModal.root).addEventListener('click', () => { paymentModal.close(); initiatePayment(`/api/booking-extensions/${response.id}/payments`, 'The extension will apply only after payment verification.'); }); } else { toast(`Stay extended to ${formatDate(response.requestedCheckOutDate)}.`, 'success'); refresh(); } } catch (error) { $('#extension-feedback', modal.root).className = 'date-feedback unavailable'; $('#extension-feedback', modal.root).textContent = error.status === 409 ? 'Those dates are no longer available.' : error.message; setButtonBusy(button, false); } }); }

function bookAgain(booking, property, refresh) { const modal = openModal({ title: 'Book again', body: `<p class="muted">A new pending-payment booking will be created for the same guest and property.</p><form id="again-form"><div class="form-grid"><div class="field"><label for="again-checkin">Check-in date</label><input id="again-checkin" type="date" required></div><div class="field"><label for="again-checkout">Check-out date</label><input id="again-checkout" type="date" required></div></div><div id="again-feedback" class="date-feedback">Choose dates to check availability.</div><div class="form-actions"><button class="button secondary" type="button" data-close>Cancel</button><button class="button" type="submit">Create future booking</button></div></form>` }); $('[data-close]', modal.root).addEventListener('click', modal.close); const availability = async () => { const from = $('#again-checkin', modal.root).value, to = $('#again-checkout', modal.root).value, feedback = $('#again-feedback', modal.root); if (!from || !to || to <= from) return false; try { const calendar = await get(`/api/properties/${property.id}/availability?from=${from}&to=${to}`); const conflict = calendar.unavailableRanges.some(range => range.checkInDate < to && range.checkOutDate > from); feedback.className = `date-feedback ${conflict ? 'unavailable' : 'available'}`; feedback.textContent = conflict ? 'Those dates are not available.' : 'Those dates are available.'; return !conflict; } catch { return true; } }; ['again-checkin','again-checkout'].forEach(id => $(`#${id}`, modal.root).addEventListener('change', availability)); $('#again-form', modal.root).addEventListener('submit', async event => { event.preventDefault(); const button = $('button[type="submit"]', event.currentTarget); setButtonBusy(button, true); if (!await availability()) { setButtonBusy(button, false); return; } try { const result = await post(`/api/bookings/${booking.id}/book-again`, { checkInDate: $('#again-checkin', modal.root).value, checkOutDate: $('#again-checkout', modal.root).value }); modal.close(); const url = `${location.origin}/guest/${result.guestLink.token}`; openModal({ title: 'Future booking created', body: `<div class="notice">${escapeHtml(guestLinkState(result.guestLink.state))}. Payment is pending.</div><p style="margin-top:1rem">The new guest link is available once:</p><input readonly value="${escapeHtml(url)}">`, actions: `<a class="button" href="#/bookings/${result.booking.id}">View new booking</a>` }); refresh(); } catch (error) { $('#again-feedback', modal.root).className = 'date-feedback unavailable'; $('#again-feedback', modal.root).textContent = error.status === 409 ? 'Those dates are no longer available.' : error.message; setButtonBusy(button, false); } }); }

async function renderGuests(route) {
  const guestId = route.split('/')[1];

  if (guestId) {
    return renderGuestForm(guestId);
  }

  app.innerHTML = hostShell(
    'guests',
    `
      ${heading(
        'Guests',
        'Guest records collected through Hostvero stay flows.'
      )}

      <section class="card card-pad">
        <div class="skeleton" style="height:180px"></div>
      </section>
    `
  );

  bindShell();

  try {
    const guests = await get('/api/guests');

    state.guests = guests;

    const rows = guests
      .map(guest => `
        <tr>

          <td>
            <a
              class="guest-name-link"
              href="#/guests/${guest.id}"
            >
              ${escapeHtml(guest.fullName || 'Guest')}
            </a>
          </td>

          <td>
            ${escapeHtml(guest.phone || '—')}
          </td>

          <td>
            ${escapeHtml(guest.email || '—')}
          </td>

          <td>
            ${
              guest.nationality
                ? escapeHtml(guest.nationality)
                : '—'
            }
          </td>

          <td>
            ${formatDateTime(guest.updatedAt)}
          </td>

          <td>
            <a
              class="button small secondary"
              href="#/guests/${guest.id}"
            >
              View
            </a>
          </td>

        </tr>
      `)
      .join('');

    const content = `
      ${heading(
        'Guests',
        'Guest records collected through Hostvero stay flows.'
      )}

      <section class="card guest-register-card">

        <header class="guest-register-header">

          <div>
            <h2>
              ${guests.length}
              guest${guests.length === 1 ? '' : 's'}
            </h2>

            <p>
              Guest information is stored here after registration.
            </p>
          </div>

        </header>

        ${
          guests.length
            ? `
              <div class="table-wrap guest-table-wrap">

                <table>

                  <thead>
                    <tr>
                      <th>Guest</th>
                      <th>Phone</th>
                      <th>Email</th>
                      <th>Nationality</th>
                      <th>Updated</th>
                      <th></th>
                    </tr>
                  </thead>

                  <tbody>
                    ${rows}
                  </tbody>

                </table>

              </div>
            `
            : `
              ${emptyState(
                '♙',
                'No guest records yet',
                'Guests will appear here after they complete their information through a secure Hostvero booking link.'
              )}
            `
        }

      </section>
    `;

    app.innerHTML = hostShell(
      'guests',
      content
    );

    bindShell();

  } catch (error) {
    showHostError(
      'guests',
      error
    );
  }
}


async function renderGuestForm(id) {
  if (!id) {
    go('guests');
    return;
  }

  app.innerHTML = hostShell(
    'guests',
    `
      ${heading(
        'Guest details',
        'Review and manage the information associated with this guest.',
        '<a class="button secondary" href="#/guests">← Guests</a>'
      )}

      <section class="card card-pad">
        <div class="skeleton" style="height:300px"></div>
      </section>
    `
  );

  bindShell();

  try {
    const guest = await get(`/api/guests/${id}`);

    const fields = [
      ['fullName', 'Full name', 'text'],
      ['phone', 'Phone', 'tel'],
      ['email', 'Email', 'email'],
      ['whatsappNumber', 'WhatsApp number', 'tel'],
      ['idType', 'ID type', 'text'],
      ['idNumber', 'ID / passport number', 'text'],
      ['nationality', 'Nationality', 'text']
    ];

    const form = `
      <form id="guest-form" class="guest-detail-form">

        <section class="guest-detail-section">

          <header class="guest-detail-section-header">

            <h2>Guest information</h2>

            <p>
              Information supplied for this guest record.
            </p>

          </header>

          <div class="form-grid">

            ${fields
              .map(([key, label, type]) => `
                <div class="field">

                  <label for="guest-${key}">
                    ${label}
                    ${
                      ['fullName', 'phone', 'email'].includes(key)
                        ? ' *'
                        : ''
                    }
                  </label>

                  <input
                    id="guest-${key}"
                    name="${key}"
                    type="${type}"
                    value="${escapeHtml(guest[key] || '')}"
                    ${
                      ['fullName', 'phone', 'email'].includes(key)
                        ? 'required'
                        : ''
                    }
                  >

                </div>
              `)
              .join('')}

          </div>

        </section>


        <section class="guest-detail-section">

          <header class="guest-detail-section-header">

            <h2>Private notes</h2>

            <p>
              Notes visible only inside the host workspace.
            </p>

          </header>

          <div class="field">

            <textarea
              id="guest-notes"
              name="notes"
              maxlength="2000"
              placeholder="Add private notes about this guest..."
            >${escapeHtml(guest.notes || '')}</textarea>

          </div>

        </section>


        <div class="form-actions">

          <button
            class="button danger"
            type="button"
            data-delete-guest
          >
            Remove guest
          </button>

          <a
            class="button secondary"
            href="#/guests"
          >
            Cancel
          </a>

          <button
            class="button"
            type="submit"
          >
            Save changes
          </button>

        </div>

      </form>
    `;

    app.innerHTML = hostShell(
      'guests',
      `
        ${heading(
          'Guest details',
          'Review and manage the information associated with this guest.',
          '<a class="button secondary" href="#/guests">← Guests</a>'
        )}

        <section class="card guest-detail-card">
          ${form}
        </section>
      `
    );

    bindShell();

    $('#guest-form').addEventListener(
      'submit',
      async event => {

        event.preventDefault();

        const button = $(
          'button[type="submit"]',
          event.currentTarget
        );

        setButtonBusy(
          button,
          true,
          'Saving…'
        );

        try {
          const data =
            Object.fromEntries(
              new FormData(event.currentTarget)
            );

          await put(
            `/api/guests/${id}`,
            data
          );

          toast(
            'Guest updated.',
            'success'
          );

          renderGuestForm(id);

        } catch (error) {

          handleFormError(
            error,
            event.currentTarget
          );

        } finally {

          setButtonBusy(
            button,
            false
          );

        }

      }
    );


    $('[data-delete-guest]')
      .addEventListener(
        'click',
        async () => {

          if (
            await confirmDialog({
              title: 'Remove this guest?',
              message:
                'Guests with booking history will be archived so historical records stay intact.',
              confirmLabel:
                'Remove guest',
              danger: true
            })
          ) {

            try {

              const result = await post(`/api/guests/${id}/remove`, {});

              toast(
                result.archived ? 'Guest archived; booking history was preserved.' : 'Guest deleted.',
                'success'
              );

              go('guests');

            } catch (error) {

              toast(
                error.message,
                'error'
              );

            }

          }

        }
      );

  } catch (error) {

    showHostError(
      'guests',
      error
    );

  }
}

async function renderProperties(route) {
  if (route === 'properties/new') {
    return renderPropertyForm(null);
  }

  const propertyId = route.split('/')[1];

  if (propertyId) {
    return renderPropertyForm(propertyId);
  }


  app.innerHTML = hostShell(
    'properties',
    `
      ${heading(
        'Properties',
        'Manage the places you host with Hostvero.',
        '<a class="button" href="#/properties/new">+ Add property</a>'
      )}

      <section class="card card-pad">
        <div class="skeleton" style="height:220px"></div>
      </section>
    `
  );

  bindShell();


  try {

    const properties = await get('/api/properties');

    state.properties = properties;


    const cards = properties.map(property => {

      const type = titleCase(property.propertyType || 'Property');

      const checkIn = property.checkInTime
        ? String(property.checkInTime).slice(0, 5)
        : '—';

      const checkOut = property.checkOutTime
        ? String(property.checkOutTime).slice(0, 5)
        : '—';

      const status = property.active
        ? '<span class="badge active">Active</span>'
        : '<span class="badge cancelled">Inactive</span>';


      return `
        <article class="card property-card">

          <header>

            <div>
              <h2>
                ${escapeHtml(property.name)}
              </h2>

              <p class="property-meta">
                ${escapeHtml(type)}
                ·
                ${escapeHtml(property.address || 'Address not set')}
              </p>
            </div>

            ${status}

          </header>


          <div>

            <p class="price">
              ${formatMoney(
                property.defaultNightlyRate,
                property.currency
              )}

              <span
                class="muted"
                style="
                  font-size:.76rem;
                  font-weight:500;
                "
              >
                / night
              </span>
            </p>

          </div>


          <div class="detail-list">

            <div class="detail-row">
              <span>Guests</span>
              <strong>
                Up to ${property.maxGuests}
              </strong>
            </div>

            <div class="detail-row">
              <span>Check-in</span>
              <strong>
                ${escapeHtml(checkIn)}
              </strong>
            </div>

            <div class="detail-row">
              <span>Check-out</span>
              <strong>
                ${escapeHtml(checkOut)}
              </strong>
            </div>

          </div>


          <div class="property-actions">

            <a
              class="button small secondary"
              href="#/properties/${property.id}"
            >
              Manage
            </a>

            <a
              class="button small"
              href="#/bookings/new"
            >
              New booking
            </a>

          </div>

        </article>
      `;

    }).join('');


    const content = `
      ${heading(
        'Properties',
        'Manage the places you host with Hostvero.',
        '<a class="button" href="#/properties/new">+ Add property</a>'
      )}

      ${
        properties.length
          ? `
            <section
              class="grid three-col"
              aria-label="Properties"
            >
              ${cards}
            </section>
          `
          : `
            <section class="card card-pad">

              ${emptyState(
                '⌂',
                'No properties yet',
                'Add your first property to start managing bookings and guest stays.',
                '<a class="button" href="#/properties/new">Add property</a>'
              )}

            </section>
          `
      }
    `;


    app.innerHTML = hostShell(
      'properties',
      content
    );

    bindShell();

  } catch (error) {

    showHostError(
      'properties',
      error
    );

  }
}

async function renderPropertyForm(id) {
  app.innerHTML = hostShell(
    'properties',
    `
      ${heading(
        id ? 'Property settings' : 'Add property',
        id
          ? 'Update the information used for bookings and guest stay pages.'
          : 'Add a property to start creating bookings and guest links.'
      )}

      <section class="card card-pad">
        <div class="skeleton" style="height:320px"></div>
      </section>
    `
  );

  bindShell();

  try {
    const property = id
      ? await get(`/api/properties/${id}`)
      : {};

    const value = (key, fallback = '') =>
      escapeHtml(property[key] ?? fallback);

    const typeOptions = [
      'APARTMENT',
      'HOUSE',
      'VILLA',
      'GUESTHOUSE',
      'HOTEL',
      'OTHER'
    ]
      .map(
        type => `
          <option
            value="${type}"
            ${type === property.propertyType ? 'selected' : ''}
          >
            ${titleCase(type)}
          </option>
        `
      )
      .join('');

    const form = `
      <form id="property-form">

        <div class="form-grid">

          <div class="field">
            <label for="property-name">
              Property name *
            </label>

            <input
              id="property-name"
              name="name"
              required
              maxlength="160"
              placeholder="e.g. Kilimani Garden Apartment"
              value="${value('name')}"
            >
          </div>


          <div class="field">
            <label for="property-type">
              Property type *
            </label>

            <select
              id="property-type"
              name="propertyType"
              required
            >
              ${typeOptions}
            </select>
          </div>


          <div class="field full">
            <label for="property-address">
              Address *
            </label>

            <input
              id="property-address"
              name="address"
              required
              maxlength="500"
              placeholder="Building, street, area"
              value="${value('address')}"
            >
          </div>


          <div class="field full">
            <label for="property-maps">
              Google Maps URL *
            </label>

            <input
              id="property-maps"
              name="mapsUrl"
              type="url"
              required
              placeholder="https://maps.google.com/..."
              value="${value('mapsUrl')}"
            >

            <span class="help">
              Used to help guests find the property.
            </span>
          </div>


          <div class="field">
            <label for="property-guests">
              Maximum guests *
            </label>

            <input
              id="property-guests"
              name="maxGuests"
              type="number"
              min="1"
              required
              value="${value('maxGuests', '1')}"
            >
          </div>


          <div class="field">
            <label for="property-rate">
              Nightly rate *
            </label>

            <input
              id="property-rate"
              name="defaultNightlyRate"
              type="number"
              min="0"
              step="0.01"
              required
              placeholder="0"
              value="${value('defaultNightlyRate')}"
            >
          </div>


          <div class="field">
            <label for="property-currency">
              Currency *
            </label>

            <input
              id="property-currency"
              name="currency"
              maxlength="3"
              required
              value="${value('currency', 'KES')}"
            >
          </div>


          <div class="field">
            <label for="property-status">
              Property status
            </label>

            <select
              id="property-status"
              name="active"
            >
              <option
                value="true"
                ${property.active !== false ? 'selected' : ''}
              >
                Active
              </option>

              <option
                value="false"
                ${property.active === false ? 'selected' : ''}
              >
                Inactive
              </option>
            </select>
          </div>


          <div class="field">
            <label for="property-checkin">
              Check-in time *
            </label>

            <input
              id="property-checkin"
              name="checkInTime"
              type="time"
              required
              value="${value('checkInTime', '14:00').slice(0, 5)}"
            >
          </div>


          <div class="field">
            <label for="property-checkout">
              Check-out time *
            </label>

            <input
              id="property-checkout"
              name="checkOutTime"
              type="time"
              required
              value="${value('checkOutTime', '10:00').slice(0, 5)}"
            >
          </div>

        </div>


        <div style="margin:1.5rem 0 1.1rem">
          <h2>Guest stay information</h2>
          <p class="muted" style="margin:0;font-size:.82rem">
            These details can appear on the guest stay page when appropriate.
          </p>
        </div>


        <div class="form-grid">

          <div class="field">
            <label for="property-wifi-name">
              Wi-Fi name
            </label>

            <input
              id="property-wifi-name"
              name="wifiName"
              maxlength="100"
              placeholder="Network name"
              value="${value('wifiName')}"
            >
          </div>


          <div class="field">
            <label for="property-wifi-password">
              Wi-Fi password
            </label>

            <input
              id="property-wifi-password"
              name="wifiPassword"
              maxlength="200"
              placeholder="Wi-Fi password"
              value="${value('wifiPassword')}"
            >
          </div>


          <div class="field full">
            <label for="property-checkin-instructions">
              Check-in instructions
            </label>

            <textarea
              id="property-checkin-instructions"
              name="checkInInstructions"
              placeholder="Access instructions, keys, reception, parking..."
            >${value('checkInInstructions')}</textarea>
          </div>


          <div class="field full">
            <label for="property-house-rules">
              House rules
            </label>

            <textarea
              id="property-house-rules"
              name="houseRules"
              placeholder="Quiet hours, visitors, smoking rules..."
            >${value('houseRules')}</textarea>
          </div>


          <div class="field">
            <label for="property-contact">
              Guest contact phone
            </label>

            <input
              id="property-contact"
              name="contactPhone"
              type="tel"
              placeholder="Contact number"
              value="${value('contactPhone')}"
            >
          </div>

        </div>


        <div class="form-actions">

          ${
            id
              ? `
                <button
                  class="button danger"
                  type="button"
                  data-deactivate-property
                >
                  Deactivate
                </button>
              `
              : ''
          }

          <a
            class="button secondary"
            href="#/properties"
          >
            Cancel
          </a>

          <button
            class="button"
            type="submit"
          >
            ${id ? 'Save changes' : 'Add property'}
          </button>

        </div>

      </form>
    `;


    app.innerHTML = hostShell(
      'properties',
      `
        ${heading(
          id ? 'Property settings' : 'Add property',
          id
            ? 'Update the information used for bookings and guest stay pages.'
            : 'Add a property to start creating bookings and guest links.',
          '<a class="button secondary" href="#/properties">← Properties</a>'
        )}

        <section class="card card-pad">
          ${form}
        </section>
      `
    );

    bindShell();


    $('#property-form').addEventListener(
      'submit',
      async event => {

        event.preventDefault();

        const button = $(
          'button[type="submit"]',
          event.currentTarget
        );

        setButtonBusy(
          button,
          true,
          id ? 'Saving…' : 'Adding…'
        );

        try {
          const data = Object.fromEntries(
            new FormData(event.currentTarget)
          );

          data.maxGuests = Number(data.maxGuests);

          data.defaultNightlyRate =
            Number(data.defaultNightlyRate);

          data.active =
            data.active === 'true';

          data.currency =
            data.currency.toUpperCase();

          const saved = id
            ? await put(
                `/api/properties/${id}`,
                data
              )
            : await post(
                '/api/properties',
                data
              );

          toast(
            id
              ? 'Property updated.'
              : 'Property added.',
            'success'
          );

          go(`properties/${saved.id}`);

        } catch (error) {

          handleFormError(
            error,
            event.currentTarget
          );

        } finally {

          setButtonBusy(
            button,
            false
          );

        }

      }
    );


    $('[data-deactivate-property]')
      ?.addEventListener(
        'click',
        async () => {

          if (
            await confirmDialog({
              title: 'Deactivate this property?',
              message:
                'It will no longer be available for new bookings.',
              confirmLabel:
                'Deactivate property',
              danger: true
            })
          ) {

            try {

              await del(
                `/api/properties/${id}`
              );

              toast(
                'Property deactivated.',
                'success'
              );

              go('properties');

            } catch (error) {

              toast(
                error.message,
                'error'
              );

            }

          }

        }
      );

  } catch (error) {

    showHostError(
      'properties',
      error
    );

  }
}

async function renderPayments() {
  app.innerHTML = hostShell(
    'payments',
    `
      ${heading(
        'Payments & receipts',
        'Track booking payments and issued receipts.'
      )}

      <section class="grid stats">
        ${Array.from({ length: 4 }, () => `
          <article class="card stat-card">
            <div class="skeleton" style="width:45%"></div>
            <div class="skeleton" style="width:35%;height:28px;margin-top:14px"></div>
          </article>
        `).join('')}
      </section>
    `
  );

  bindShell();

  try {
    const [bookings, guests, receipts] = await apiMany([
      '/api/bookings',
      '/api/guests',
      '/api/receipts'
    ]);

    const guestMap = mapById(guests);

    const nestedPayments = await Promise.all(
      bookings.map(
        booking =>
          get(`/api/bookings/${booking.id}/payments`)
            .catch(() => [])
      )
    );

    const payments = nestedPayments
      .flat()
      .map(payment => ({
        ...payment,
        booking: bookings.find(
          booking => booking.id === payment.bookingId
        )
      }))
      .sort(
        (a, b) =>
          new Date(b.paidAt || b.createdAt || 0) -
          new Date(a.paidAt || a.createdAt || 0)
      );


    const successfulPayments = payments.filter(
      payment =>
        ['SUCCEEDED', 'PAID', 'SUCCESS'].includes(
          String(payment.status || '').toUpperCase()
        )
    );

    const pendingPayments = payments.filter(
      payment =>
        ['PENDING', 'PROCESSING', 'PENDING_PAYMENT'].includes(
          String(payment.status || '').toUpperCase()
        )
    );


    /*
     * Payments may eventually contain multiple currencies.
     * For now, only calculate a total when all successful
     * payments use the same currency.
     */
    const paymentCurrencies = [
      ...new Set(
        successfulPayments
          .map(payment => payment.currency)
          .filter(Boolean)
      )
    ];

    const totalPaid =
      paymentCurrencies.length === 1
        ? successfulPayments.reduce(
            (sum, payment) =>
              sum + Number(payment.amount || 0),
            0
          )
        : null;

    const totalPaidLabel =
      totalPaid !== null
        ? formatMoney(
            totalPaid,
            paymentCurrencies[0]
          )
        : successfulPayments.length
          ? 'Multiple currencies'
          : '—';


    const paymentRows = payments
      .map(payment => {
        const booking = payment.booking;
        const guest = guestMap.get(
          booking?.guestId
        );

        return `
          <tr>

            <td>
              <strong>
                ${escapeHtml(
                  guest?.fullName || 'Guest pending'
                )}
              </strong>
            </td>

            <td>
              ${
                booking
                  ? `
                    <a href="#/bookings/${booking.id}">
                      View booking
                    </a>
                  `
                  : '—'
              }
            </td>

            <td>
              <strong>
                ${formatMoney(
                  payment.amount,
                  payment.currency
                )}
              </strong>
            </td>

            <td>
              ${escapeHtml(
                titleCase(
                  payment.provider || 'Unknown'
                )
              )}
            </td>

            <td>
              ${badge(payment.status)}
            </td>

            <td>
              ${formatDateTime(
                payment.paidAt ||
                payment.createdAt
              )}
            </td>

          </tr>
        `;
      })
      .join('');


    const receiptRows = receipts
      .slice()
      .sort(
        (a, b) =>
          new Date(b.issuedAt || 0) -
          new Date(a.issuedAt || 0)
      )
      .map(receipt => `
        <tr>

          <td>
            <strong>
              ${escapeHtml(
                receipt.receiptNumber || 'Receipt'
              )}
            </strong>
          </td>

          <td>
            ${formatMoney(
              receipt.amount,
              receipt.currency
            )}
          </td>

          <td>
            ${formatDateTime(
              receipt.issuedAt
            )}
          </td>

        </tr>
      `)
      .join('');


    const content = `
      ${heading(
        'Payments & receipts',
        'Track booking payments and issued receipts.'
      )}


      <section class="grid stats payment-stats">

        <article class="card stat-card">
          <span class="stat-label">
            Total received
          </span>

          <p class="stat-value payment-stat-money">
            ${totalPaidLabel}
          </p>

          <span class="stat-note">
            Verified payments
          </span>
        </article>


        <article class="card stat-card">
          <span class="stat-label">
            Successful
          </span>

          <p class="stat-value">
            ${successfulPayments.length}
          </p>

          <span class="stat-note">
            Completed payments
          </span>
        </article>


        <article class="card stat-card">
          <span class="stat-label">
            Pending
          </span>

          <p class="stat-value">
            ${pendingPayments.length}
          </p>

          <span class="stat-note">
            Awaiting completion
          </span>
        </article>


        <article class="card stat-card">
          <span class="stat-label">
            Receipts
          </span>

          <p class="stat-value">
            ${receipts.length}
          </p>

          <span class="stat-note">
            Issued receipts
          </span>
        </article>

      </section>


      <section class="card payment-section">

        <header class="payment-section-header">

          <div>
            <h2>Payments</h2>

            <p>
              Payment activity across your bookings.
            </p>
          </div>

        </header>


        ${
          payments.length
            ? `
              <div class="table-wrap payment-table-wrap">

                <table>

                  <thead>
                    <tr>
                      <th>Guest</th>
                      <th>Booking</th>
                      <th>Amount</th>
                      <th>Provider</th>
                      <th>Status</th>
                      <th>Date</th>
                    </tr>
                  </thead>

                  <tbody>
                    ${paymentRows}
                  </tbody>

                </table>

              </div>
            `
            : emptyState(
                '◈',
                'No payments yet',
                'Payments will appear here after they are initiated for a booking.'
              )
        }

      </section>


      <section
        class="card payment-section"
        style="margin-top:1rem"
      >

        <header class="payment-section-header">

          <div>
            <h2>Receipts</h2>

            <p>
              Receipts created after verified payments.
            </p>
          </div>

        </header>


        ${
          receipts.length
            ? `
              <div class="table-wrap payment-table-wrap">

                <table>

                  <thead>
                    <tr>
                      <th>Receipt</th>
                      <th>Amount</th>
                      <th>Issued</th>
                    </tr>
                  </thead>

                  <tbody>
                    ${receiptRows}
                  </tbody>

                </table>

              </div>
            `
            : emptyState(
                '▤',
                'No receipts yet',
                'Receipts will appear here after successful payment verification.'
              )
        }

      </section>
    `;


    app.innerHTML = hostShell(
      'payments',
      content
    );

    bindShell();

  } catch (error) {

    showHostError(
      'payments',
      error
    );

  }
}
async function renderNotifications() {
  app.innerHTML = hostShell(
    'notifications',
    `
      ${heading(
        'Notifications',
        'Track scheduled guest communication and delivery status.'
      )}

      <section class="card card-pad">
        <div class="skeleton" style="height:190px"></div>
      </section>
    `
  );

  bindShell();

  try {
    const notifications = await get('/api/notifications');

    const scheduled = notifications.filter(
      notification =>
        ['PENDING', 'SCHEDULED', 'PROCESSING'].includes(
          String(notification.status || '').toUpperCase()
        )
    );

    const sent = notifications.filter(
      notification =>
        ['SENT', 'DELIVERED', 'SUCCESS'].includes(
          String(notification.status || '').toUpperCase()
        )
    );

    const failed = notifications.filter(
      notification =>
        ['FAILED', 'CANCELLED', 'EXPIRED'].includes(
          String(notification.status || '').toUpperCase()
        )
    );

    const rows = notifications
      .slice()
      .sort(
        (a, b) =>
          new Date(b.scheduledAt || 0) -
          new Date(a.scheduledAt || 0)
      )
      .map(notification => `
        <tr>

          <td>
            <strong class="notification-name">
              ${escapeHtml(
                notificationLabel(notification.type)
              )}
            </strong>
          </td>

          <td>
            ${
              notification.bookingId
                ? `
                  <a href="#/bookings/${notification.bookingId}">
                    View booking
                  </a>
                `
                : '—'
            }
          </td>

          <td>
            ${escapeHtml(
              titleCase(
                notification.channel || 'Unknown'
              )
            )}
          </td>

          <td>
            ${badge(notification.status)}
          </td>

          <td>
            ${formatDateTime(
              notification.scheduledAt
            )}
          </td>

          <td class="notification-detail">
            ${escapeHtml(
              notification.deliveryDetail || '—'
            )}
          </td>

        </tr>
      `)
      .join('');


    const content = `
      ${heading(
        'Notifications',
        'Track scheduled guest communication and delivery status.'
      )}


      <section class="grid stats notification-stats">

        <article class="card stat-card">

          <span class="stat-label">
            Total
          </span>

          <p class="stat-value">
            ${notifications.length}
          </p>

          <span class="stat-note">
            All notifications
          </span>

        </article>


        <article class="card stat-card">

          <span class="stat-label">
            Scheduled
          </span>

          <p class="stat-value">
            ${scheduled.length}
          </p>

          <span class="stat-note">
            Waiting to send
          </span>

        </article>


        <article class="card stat-card">

          <span class="stat-label">
            Sent
          </span>

          <p class="stat-value">
            ${sent.length}
          </p>

          <span class="stat-note">
            Delivered or completed
          </span>

        </article>


        <article class="card stat-card">

          <span class="stat-label">
            Failed
          </span>

          <p class="stat-value">
            ${failed.length}
          </p>

          <span class="stat-note">
            Needs attention
          </span>

        </article>

      </section>


      <section class="card notification-section">

        <header class="notification-section-header">

          <div>
            <h2>Communication activity</h2>

            <p>
              Guest reminders and their delivery status.
            </p>
          </div>

        </header>


        ${
          notifications.length
            ? `
              <div class="table-wrap notification-table-wrap">

                <table>

                  <thead>
                    <tr>
                      <th>Notification</th>
                      <th>Booking</th>
                      <th>Channel</th>
                      <th>Status</th>
                      <th>Scheduled</th>
                      <th>Detail</th>
                    </tr>
                  </thead>

                  <tbody>
                    ${rows}
                  </tbody>

                </table>

              </div>
            `
            : `
              ${emptyState(
                '✓',
                'No notifications yet',
                'Scheduled guest communication will appear here when bookings generate reminders.'
              )}
            `
        }

      </section>
    `;


    app.innerHTML = hostShell(
      'notifications',
      content
    );

    bindShell();

  } catch (error) {

    showHostError(
      'notifications',
      error
    );

  }
}
async function renderSettings() {
  app.innerHTML = hostShell(
    'settings',
    `
      ${heading(
        'Settings',
        'Manage your Hostvero account and workspace preferences.'
      )}

      <section class="settings-grid">
        <article class="card settings-card settings-account-card">
          <div class="skeleton" style="height:180px"></div>
        </article>
      </section>
    `
  );

  bindShell();

  try {
    const [host, payoutSettings, payoutBanks] = await Promise.all([
      get('/api/me'),
      get('/api/me/payout-settings'),
      get('/api/me/payout-settings/banks')
    ]);

    state.host = host;

    const accountForm = `
      <form id="profile-form" class="settings-profile-form">

        <div class="settings-form-grid">

          <div class="field">
            <label for="settings-name">
              Full name
            </label>

            <input
              id="settings-name"
              name="fullName"
              required
              maxlength="120"
              value="${escapeHtml(host.fullName || '')}"
            >
          </div>


          <div class="field">
            <label for="settings-email">
              Email address
            </label>

            <input
              id="settings-email"
              type="email"
              readonly
              value="${escapeHtml(host.email || '')}"
            >

            <span class="help">
              Email changes are not available yet.
            </span>
          </div>


          <div class="field">
            <label for="settings-phone">
              Phone
            </label>

            <input
              id="settings-phone"
              name="phone"
              type="tel"
              maxlength="32"
              value="${escapeHtml(host.phone || '')}"
            >
          </div>


          <div class="field">
            <label for="settings-status">
              Account status
            </label>

            <input
              id="settings-status"
              readonly
              value="${host.active ? 'Active' : 'Inactive'}"
            >
          </div>

        </div>


        <div class="settings-form-footer">

          <div>
            <strong>Account information</strong>
            <span>
              Used across your Hostvero workspace.
            </span>
          </div>

          <button
            class="button"
            type="submit"
          >
            Save changes
          </button>

        </div>

      </form>
    `;

    const payoutMethod = payoutSettings.payoutMethod || 'BANK_ACCOUNT';
    const bankOptions = payoutBanks.map(bank => `
      <option value="${escapeHtml(bank.code)}" ${bank.code === payoutSettings.settlementBankCode ? 'selected' : ''}>
        ${escapeHtml(bank.name)}
      </option>`).join('');
    const isBankPayout = payoutMethod === 'BANK_ACCOUNT';
const isMpesaPayout = payoutMethod === 'MPESA';

const payoutForm = `
  <form id="payout-settings-form" class="settings-profile-form">

    <div class="field">
      <label>Payout method</label>

      <div class="settings-option-list">

        <label class="settings-option" style="cursor:pointer;">
          <div>
            <strong>Bank account</strong>
            <span>
              Settle the booking amount to a Kenyan bank account through Paystack.
            </span>
          </div>

          <input
            type="radio"
            name="payoutMethod"
            value="BANK_ACCOUNT"
            ${isBankPayout ? 'checked' : ''}
          >
        </label>

        <label class="settings-option" style="cursor:pointer;">
          <div>
            <strong>M-Pesa</strong>
            <span>
              Receive your Hostvero payout through Safaricom M-Pesa.
            </span>
          </div>

          <input
            type="radio"
            name="payoutMethod"
            value="MPESA"
            ${isMpesaPayout ? 'checked' : ''}
          >
        </label>

      </div>
    </div>


    <!-- BANK PAYOUT -->

    <div
      class="settings-form-grid"
      data-payout-bank
      ${isBankPayout ? '' : 'hidden'}
    >

      <div class="field">
        <label for="settlement-bank-code">
          Bank
        </label>

        <select
          id="settlement-bank-code"
          name="settlementBankCode"
          ${isBankPayout ? 'required' : 'disabled'}
        >
          <option value="">
            Choose bank
          </option>

          ${bankOptions}
        </select>
      </div>


      <div class="field">
        <label for="payout-account-name">
          Account name
        </label>

        <input
          id="payout-account-name"
          name="accountName"
          maxlength="160"
          value="${escapeHtml(payoutSettings.accountName || '')}"
          ${isBankPayout ? 'required' : 'disabled'}
        >
      </div>


      <div
        class="field"
        style="grid-column:1 / -1;"
      >

        <label for="payout-account-number">
          Account number
        </label>

        <input
          id="payout-account-number"
          name="accountNumber"
          inputmode="numeric"
          pattern="[0-9]{5,34}"
          maxlength="34"
          autocomplete="off"
          ${isBankPayout ? 'required' : 'disabled'}
        >

        <span class="help">
          ${
            payoutSettings.maskedAccountNumber
              ? `Current destination: ${escapeHtml(
                  payoutSettings.maskedAccountNumber
                )}. Re-enter the full account number to update it.`
              : 'Your account number is sent securely to Paystack and is not stored in full by Hostvero.'
          }
        </span>

      </div>

    </div>


    <!-- MPESA PAYOUT -->

    <div
      class="settings-form-grid"
      data-payout-mpesa
      ${isMpesaPayout ? '' : 'hidden'}
    >

      <div
        class="field"
        style="grid-column:1 / -1;"
      >

        <label for="payout-mpesa-phone">
          M-Pesa phone number
        </label>

        <input
          id="payout-mpesa-phone"
          name="mpesaPhone"
          type="tel"
          inputmode="tel"
          pattern="(?:\\+2547\\d{8}|2547\\d{8}|07\\d{8})"
          maxlength="13"
          autocomplete="off"
          placeholder="07XXXXXXXX"
          ${isMpesaPayout ? 'required' : 'disabled'}
        >

        <span class="help">
          ${
            payoutSettings.maskedMpesaPhone
              ? `Current destination: ${escapeHtml(
                  payoutSettings.maskedMpesaPhone
                )}. Re-enter the full number to change it.`
              : 'Enter the Safaricom M-Pesa number that should receive your payouts.'
          }
        </span>

      </div>

    </div>


    <div class="settings-form-footer">

      <div>
        <strong>
          Paystack payout destination
        </strong>

        <span>
          Choose either your bank account or M-Pesa as your payout destination.
        </span>
      </div>

      <button
        class="button"
        type="submit"
      >
        Save payout settings
      </button>

    </div>

  </form>
`;

    const payoutConfiguredView = `
      <div class="settings-profile-form">
        <div class="settings-option-list">
          <div class="settings-option">
            <div>
              <strong>Payout method</strong>
              <span>${payoutSettings.payoutMethod === 'MPESA' ? 'M-Pesa' : 'Bank account'}</span>
            </div>
            <span class="settings-pill">Configured</span>
          </div>

          <div class="settings-option">
            <div>
              <strong>${payoutSettings.payoutMethod === 'MPESA' ? 'M-Pesa destination' : 'Bank destination'}</strong>
              <span>${payoutSettings.payoutMethod === 'MPESA'
                ? escapeHtml(payoutSettings.maskedMpesaPhone || 'Configured')
                : `${escapeHtml(payoutSettings.settlementBankName || 'Bank account')}${payoutSettings.maskedAccountNumber
                  ? ` · ${escapeHtml(payoutSettings.maskedAccountNumber)}`
                  : ''}`}</span>
            </div>
          </div>
        </div>

        <div class="settings-form-footer">
          <div>
            <strong>Payout destination active</strong>
            <span>${payoutSettings.payoutMethod === 'MPESA'
              ? 'Future Hostvero payouts will be sent to this M-Pesa destination.'
              : 'Future Hostvero payouts will be settled to this bank destination.'}</span>
          </div>
          <button class="button secondary" type="button" id="reconfigure-payout">
            Reconfigure payout
          </button>
        </div>
      </div>
    `;


    const content = `
      ${heading(
        'Settings',
        'Manage your Hostvero account and workspace preferences.'
      )}

      <section class="settings-grid">


        <!-- ACCOUNT -->

        <article class="card settings-card settings-account-card">

          <header class="settings-card-header">

            <div>

              <span class="settings-icon">
                ${icons.settings}
              </span>

              <div>
                <h2>Account</h2>
                <p>
                  Your personal host information.
                </p>
              </div>

            </div>

            <span class="settings-status ${host.active ? 'active' : 'inactive'}">
              ${host.active ? 'Active' : 'Inactive'}
            </span>

          </header>

          ${accountForm}

        </article>


        <!-- PAYOUT SETTINGS -->

        <article class="card settings-card">

          <header class="settings-card-header">

            <div>

              <span class="settings-icon">
                ${icons.payments}
              </span>

              <div>
                <h2>Payout settings</h2>
                <p>
                  Where Paystack settles your booking amount.
                </p>
              </div>

            </div>

            <span class="settings-pill">
              ${payoutSettings.configured ? 'Configured' : 'Not configured'}
            </span>

          </header>

          <div id="payout-settings-content">
            ${payoutSettings.configured ? payoutConfiguredView : payoutForm}
          </div>

        </article>


        <!-- NOTIFICATIONS -->

        <article class="card settings-card">

          <header class="settings-card-header">

            <div>

              <span class="settings-icon">
                ${icons.notifications}
              </span>

              <div>
                <h2>Notifications</h2>
                <p>
                  Guest reminders and communication.
                </p>
              </div>

            </div>

            <span class="settings-pill">
              Not configured
            </span>

          </header>


          <div class="settings-option-list">

            <div class="settings-option">

              <div>
                <strong>WhatsApp reminders</strong>
                <span>
                  Automated booking and stay messages.
                </span>
              </div>

              <span class="settings-pill">
                Not configured
              </span>

            </div>


            <div class="settings-option">

              <div>
                <strong>Email notifications</strong>
                <span>
                  Booking and payment communication.
                </span>
              </div>

              <span class="settings-pill">
                Not configured
              </span>

            </div>

          </div>

        </article>


        <!-- PAYMENTS -->

        <article class="card settings-card">

          <header class="settings-card-header">

            <div>

              <span class="settings-icon">
                ${icons.payments}
              </span>

              <div>
                <h2>Payments</h2>
                <p>
                  Payment providers for guest bookings.
                </p>
              </div>

            </div>

            <span class="settings-pill">
              Production setup
            </span>

          </header>


          <div class="settings-option-list">

            <div class="settings-option">

              <div>
                <strong>M-Pesa</strong>
                <span>
                  Accept mobile payments from guests.
                </span>
              </div>

              <span class="settings-pill">
                Production setup
              </span>

            </div>


            <div class="settings-option">

              <div>
                <strong>Stripe</strong>
                <span>
                  Accept supported card payments.
                </span>
              </div>

              <span class="settings-pill">
                Production setup
              </span>

            </div>

          </div>

        </article>


        <!-- PROPERTY DEFAULTS -->

        <article class="card settings-card">

          <header class="settings-card-header">

            <div>

              <span class="settings-icon">
                ${icons.properties}
              </span>

              <div>
                <h2>Property defaults</h2>
                <p>
                  Reusable defaults for future properties.
                </p>
              </div>

            </div>

            <span class="settings-pill">
              Not configured
            </span>

          </header>


          <div class="settings-option-list">

            <div class="settings-option">

              <div>
                <strong>Default check-in time</strong>
                <span>
                  No workspace-wide default is configured.
                </span>
              </div>

              <span class="settings-pill">
                Not configured
              </span>

            </div>


            <div class="settings-option">

              <div>
                <strong>Default check-out time</strong>
                <span>
                  No workspace-wide default is configured.
                </span>
              </div>

              <span class="settings-pill">
                Not configured
              </span>

            </div>

          </div>

        </article>


        <!-- SECURITY -->

        <article class="card settings-card">

          <header class="settings-card-header">

            <div>

              <span class="settings-icon">
                ${icons.settings}
              </span>

              <div>
                <h2>Security</h2>
                <p>
                  Account access and security controls.
                </p>
              </div>

            </div>

            <span class="settings-pill">
              Coming later
            </span>

          </header>


          <div class="settings-option-list">

            <div class="settings-option">

              <div>
                <strong>Password management</strong>
                <span>
                  Password changes will be added in a later version.
                </span>
              </div>

              <span class="settings-pill">
                Coming later
              </span>

            </div>

          </div>

        </article>

      </section>
    `;


    app.innerHTML = hostShell(
      'settings',
      content
    );

    bindShell();


    $('#profile-form').addEventListener(
      'submit',
      async event => {

        event.preventDefault();

        const button = $(
          'button[type="submit"]',
          event.currentTarget
        );

        setButtonBusy(
          button,
          true,
          'Saving…'
        );

        try {

          state.host = await put(
            '/api/me',
            Object.fromEntries(
              new FormData(event.currentTarget)
            )
          );

          toast(
            'Settings saved.',
            'success'
          );

          renderSettings();

        } catch (error) {

          handleFormError(
            error,
            event.currentTarget
          );

        } finally {

          setButtonBusy(
            button,
            false
          );

        }

      }
    );

    const bindPayoutSettingsForm = () => {
      const form = $('#payout-settings-form');
      if (!form) return;

      form.addEventListener(
        'submit',
        async event => {
        event.preventDefault();
        const button = $('button[type="submit"]', event.currentTarget);
        setButtonBusy(button, true, 'Saving…');
        try {
          const formData = Object.fromEntries(
            new FormData(event.currentTarget)
          );

          await put('/api/me/payout-settings', formData);

          if (formData.payoutMethod === 'MPESA') {
            toast(
              'M-Pesa payout configured. Future Hostvero payouts will be sent to your saved M-Pesa destination.',
              'success'
            );
          } else {
            toast(
              'Bank payout configured. Future Hostvero payouts will be settled to your saved bank account.',
              'success'
            );
          }

          renderSettings();
        } catch (error) {
          handleFormError(error, event.currentTarget);
        } finally {
          setButtonBusy(button, false);
        }
        }
      );

      const syncPayoutMethod = () => {
        const selected = form.querySelector(
        'input[name="payoutMethod"]:checked'
        );

        if (!selected) return;

        const isBank = selected.value === 'BANK_ACCOUNT';

        form.querySelectorAll('[data-payout-bank]').forEach(section => {
          section.style.display = isBank ? '' : 'none';

          section.querySelectorAll('input, select').forEach(input => {
            input.disabled = !isBank;
            input.required = isBank;
          });
        });

        form.querySelectorAll('[data-payout-mpesa]').forEach(section => {
          section.style.display = isBank ? 'none' : '';

          section.querySelectorAll('input, select').forEach(input => {
            input.disabled = isBank;
            input.required = !isBank;
          });
        });
      };

      form.querySelectorAll('input[name="payoutMethod"]').forEach(input => {
        input.addEventListener('change', syncPayoutMethod);
      });
      syncPayoutMethod();
    };

    $('#reconfigure-payout')?.addEventListener('click', () => {
      $('#payout-settings-content').innerHTML = payoutForm;
      bindPayoutSettingsForm();
    });
    bindPayoutSettingsForm();

  } catch (error) {

    showHostError(
      'settings',
      error
    );

  }
}
function renderAuth(mode = 'login') {
  const registration = mode === 'register';

  const passwordHelp = registration
    ? `<p class="auth-field-help">
         Use at least 12 characters with uppercase, lowercase, a number, and a symbol.
       </p>`
    : '';

  app.innerHTML = `
    <main class="auth-page" id="app-main">

      <!-- LEFT BRAND PANEL -->
      <section class="auth-brand-panel">

        <div class="auth-brand-content">

          <a class="auth-logo-link" href="#/login" aria-label="Hostvero">
            <img
              src="/images/hostvero-logo-clean.png"
              alt="Hostvero"
              class="auth-logo"
            >
          </a>

          <div class="auth-brand-message">

            <span class="auth-eyebrow">
              HOSTVERO FOR HOSTS
            </span>

            <h1>
              Run every stay from one calm workspace.
            </h1>

            <p>
              Manage bookings, guest registration, payments,
              property details and stay operations without
              jumping between different tools.
            </p>

          </div>

          <div class="auth-benefits">

            <div class="auth-benefit">
              <span class="auth-benefit-icon">
                ${icons.properties}
              </span>
              <div>
                <strong>Property operations</strong>
                <span>Keep every stay connected to the right property.</span>
              </div>
            </div>

            <div class="auth-benefit">
              <span class="auth-benefit-icon">
                ${icons.guests}
              </span>
              <div>
                <strong>Guest registration</strong>
                <span>Guests complete their own information securely.</span>
              </div>
            </div>

            <div class="auth-benefit">
              <span class="auth-benefit-icon">
                ${icons.payments}
              </span>
              <div>
                <strong>Payments and receipts</strong>
                <span>Track payment status and stay records in one place.</span>
              </div>
            </div>

          </div>

        </div>

        <p class="auth-brand-footer">
          MANAGE · CONNECT · GROW
        </p>

      </section>


      <!-- RIGHT AUTH PANEL -->
      <section class="auth-form-panel">

        <div class="auth-form-container">

          <a class="auth-mobile-logo" href="#/login" aria-label="Hostvero">
            <img
              src="/images/hostvero-logo-clean.png"
              alt="Hostvero"
            >
          </a>

          <header class="auth-header">

            <span class="auth-eyebrow auth-eyebrow-mobile">
              ${registration ? 'CREATE YOUR ACCOUNT' : 'WELCOME BACK'}
            </span>

            <h1>
              ${registration ? 'Create your host account' : 'Sign in to Hostvero'}
            </h1>

            <p>
              ${
                registration
                  ? 'Set up your workspace and start managing your stays.'
                  : 'Enter your details to continue to your workspace.'
              }
            </p>

          </header>


          <form id="auth-form" class="auth-form" novalidate>

            ${
              registration
                ? `
                  <div class="auth-field">

                    <label for="auth-name">
                      Full name
                    </label>

                    <input
                      id="auth-name"
                      name="fullName"
                      type="text"
                      required
                      maxlength="120"
                      autocomplete="name"
                      placeholder="Your full name"
                    >

                  </div>


                  <div class="auth-field">

                    <label for="auth-phone">
                      Phone
                      <span class="optional-label">Optional</span>
                    </label>

                    <input
                      id="auth-phone"
                      name="phone"
                      type="tel"
                      maxlength="32"
                      autocomplete="tel"
                      placeholder="+254..."
                    >

                  </div>
                `
                : ''
            }


            <div class="auth-field">

              <label for="auth-email">
                Email address
              </label>

              <input
                id="auth-email"
                name="email"
                required
                type="email"
                autocomplete="email"
                placeholder="you@example.com"
              >

            </div>


            <div class="auth-field">

              <label for="auth-password">
                Password
              </label>

              <div class="password-input-wrap">

                <input
                  id="auth-password"
                  name="password"
                  required
                  type="password"
                  autocomplete="${registration ? 'new-password' : 'current-password'}"
                  placeholder="Enter your password"
                >

                <button
                  class="password-toggle"
                  type="button"
                  data-password-target="auth-password"
                  aria-label="Show password"
                >
                  Show
                </button>

              </div>

              ${passwordHelp}

            </div>


            ${
              registration
                ? `
                  <div class="auth-field">

                    <label for="auth-confirmation">
                      Confirm password
                    </label>

                    <div class="password-input-wrap">

                      <input
                        id="auth-confirmation"
                        name="passwordConfirmation"
                        required
                        type="password"
                        autocomplete="new-password"
                        placeholder="Enter your password again"
                      >

                      <button
                        class="password-toggle"
                        type="button"
                        data-password-target="auth-confirmation"
                        aria-label="Show password"
                      >
                        Show
                      </button>

                    </div>

                  </div>
                `
                : ''
            }


            <button
              class="button auth-submit"
              type="submit"
            >
              ${registration ? 'Create account' : 'Sign in'}
            </button>

          </form>


          <p class="auth-switch">

            ${
              registration
                ? `
                  Already have an account?
                  <a href="#/login">Sign in</a>
                `
                : `
                  New to Hostvero?
                  <a href="#/register">Create an account</a>
                `
            }

          </p>

        </div>

      </section>

    </main>
  `;


  /* ---------------------------------------------
     SHOW / HIDE PASSWORD
     --------------------------------------------- */

  $$('.password-toggle').forEach(button => {

    button.addEventListener('click', () => {

      const input = $(`#${button.dataset.passwordTarget}`);

      if (!input) return;

      const hidden = input.type === 'password';

      input.type = hidden ? 'text' : 'password';

      button.textContent = hidden ? 'Hide' : 'Show';

      button.setAttribute(
        'aria-label',
        hidden ? 'Hide password' : 'Show password'
      );

    });

  });


  /* ---------------------------------------------
     AUTH SUBMIT
     --------------------------------------------- */

  $('#auth-form').addEventListener('submit', async event => {

    event.preventDefault();

    const form = event.currentTarget;

    const button = $('button[type="submit"]', form);

    const formData = Object.fromEntries(
      new FormData(form)
    );


    /* Registration password confirmation */

    if (
      registration &&
      formData.password !== formData.passwordConfirmation
    ) {

      toast('Passwords do not match.', 'error');

      $('#auth-confirmation').focus();

      return;

    }


    setButtonBusy(
      button,
      true,
      registration ? 'Creating account…' : 'Signing in…'
    );


    try {

      const response = await post(
        registration
          ? '/api/auth/register'
          : '/api/auth/login',
        formData
      );

      session.token = response.accessToken;

      state.host = response.host;

      toast(
        registration
          ? 'Your Hostvero account is ready.'
          : 'Welcome back.',
        'success'
      );

      go('overview');

    } catch (error) {

      handleFormError(error, form);

    } finally {

      setButtonBusy(button, false);

    }

  });
}
async function renderPublicGuest() {
  const token = decodeURIComponent(
    location.pathname
      .split('/')
      .filter(Boolean)
      .pop() || ''
  );

  if (!token) {
    return renderGuestUnavailable();
  }


  app.innerHTML = `
    <main class="public-page" id="app-main">

      <div class="public-shell">

        <header class="public-topbar">
          <a
            class="public-logo-link"
            href="/"
            aria-label="Hostvero"
          >
            <img
              src="/images/hostvero-logo-clean.png"
              alt="Hostvero"
              class="public-logo"
            >
          </a>

          <span class="public-secure-label">
            Secure guest link
          </span>
        </header>


        <section class="public-card">

          <div class="public-loading">
            <div class="skeleton" style="height:22px;width:38%"></div>
            <div class="skeleton" style="height:38px;width:70%;margin-top:14px"></div>
            <div class="skeleton" style="height:120px;margin-top:28px"></div>
          </div>

        </section>

      </div>

    </main>
  `;


  try {
    const result = await get(
      `/api/public/guest/${encodeURIComponent(token)}`
    );

    const stay = result.stay;
    const property = result.property;

    const isActive =
      result.state === 'STAY_ACTIVE';


    app.innerHTML = `
      <main class="public-page" id="app-main">

        <div class="public-shell">

          <header class="public-topbar">

            <a
              class="public-logo-link"
              href="/"
              aria-label="Hostvero"
            >
              <img
                src="/images/hostvero-logo-clean.png"
                alt="Hostvero"
                class="public-logo"
              >
            </a>

            <span class="public-secure-label">
              Secure guest link
            </span>

          </header>


          <section class="public-card">

            <header class="public-stay-header">

              <div class="public-stay-status">
                ${
                  isActive
                    ? `
                      <span class="public-status-dot"></span>
                      Stay confirmed
                    `
                    : `
                      <span class="public-status-dot pending"></span>
                      Upcoming stay
                    `
                }
              </div>


              <h1>
                ${escapeHtml(property.name)}
              </h1>


              <p>
                ${formatDate(stay.checkInDate)}
                –
                ${formatDate(stay.checkOutDate)}
              </p>

            </header>


            <div class="public-body">

              ${
                isActive
                  ? renderActiveStay(result, token)
                  : renderRegistrationStay(result, token)
              }

            </div>

          </section>


          <footer class="public-footer">
            <span>
              Powered by Hostvero
            </span>
          </footer>

        </div>

      </main>
    `;

  } catch {
    renderGuestUnavailable();
  }
}


function renderRegistrationStay(result, token) {
  if (!result.registrationRequired) {
    return renderPaymentStay(result);
  }

  return `
    <section class="public-summary">

      <div class="public-summary-item">
        <span>Check-in</span>

        <strong>
          ${formatDate(result.stay.checkInDate)}
        </strong>
      </div>


      <div class="public-summary-item">
        <span>Check-out</span>

        <strong>
          ${formatDate(result.stay.checkOutDate)}
        </strong>
      </div>


      <div class="public-summary-item">
        <span>Amount due</span>

        <strong>
          ${formatMoney(
            result.payment.amount,
            result.payment.currency
          )}
        </strong>
      </div>


      <div class="public-summary-item">
        <span>Payment</span>

        <div>
          ${badge(result.payment.status)}
        </div>
      </div>

    </section>


    <section class="public-section">

      <header class="public-section-header">
        <h2>Complete your details</h2>

        <p>
          Your host needs this information to prepare for your stay.
        </p>
      </header>


      <form
        id="public-registration"
        class="public-registration-form"
      >

        <div class="form-grid">

          <div class="field full">

            <label for="public-full-name">
              Full name
            </label>

            <input
              id="public-full-name"
              name="fullName"
              autocomplete="name"
              required
              placeholder="Your full name"
            >

          </div>


          <div class="field">

            <label for="public-phone">
              Phone
            </label>

            <input
              id="public-phone"
              name="phone"
              type="tel"
              autocomplete="tel"
              required
              placeholder="+254..."
            >

          </div>


          <div class="field">

            <label for="public-email">
              Email
            </label>

            <input
              id="public-email"
              name="email"
              type="email"
              autocomplete="email"
              required
              placeholder="you@example.com"
            >

          </div>


          <div class="field">

            <label for="public-whatsapp">
              WhatsApp
              <span class="muted">
                (optional)
              </span>
            </label>

            <input
              id="public-whatsapp"
              name="whatsappNumber"
              type="tel"
              autocomplete="tel"
              placeholder="+254..."
            >

          </div>


          <div class="field">

            <label for="public-nationality">
              Nationality
              <span class="muted">
                (optional)
              </span>
            </label>

            <input
              id="public-nationality"
              name="nationality"
              autocomplete="country-name"
              placeholder="Nationality"
            >

          </div>


          <div class="field">

            <label for="public-id-type">
              ID type
              <span class="muted">
                (optional)
              </span>
            </label>

            <input
              id="public-id-type"
              name="idType"
              placeholder="Passport, National ID..."
            >

          </div>


          <div class="field">

            <label for="public-id-number">
              ID / passport number
              <span class="muted">
                (optional)
              </span>
            </label>

            <input
              id="public-id-number"
              name="idNumber"
              placeholder="Identification number"
            >

          </div>

        </div>


        <div class="public-form-note">
          Your information is shared only with the host managing this stay.
        </div>


        <div class="public-form-actions">

          <button
            class="button"
            type="submit"
          >
            Continue
          </button>

        </div>

      </form>

    </section>
  `;
}

function renderPaymentStay(result) {
  const paymentPending = result.payment.status !== 'PROCESSING';
  return `
    <section class="public-summary">
      <div class="public-summary-item"><span>Check-in</span><strong>${formatDate(result.stay.checkInDate)}</strong></div>
      <div class="public-summary-item"><span>Check-out</span><strong>${formatDate(result.stay.checkOutDate)}</strong></div>
      <div class="public-summary-item"><span>Stay</span><strong>${formatMoney(result.payment.amount, result.payment.currency)}</strong></div>
      <div class="public-summary-item"><span>Payment</span><div>${badge(result.payment.status)}</div></div>
    </section>
    ${renderEmailVerification(result)}
    ${result.stayAccess ? renderPrePaymentStayAccess(result.stayAccess) : ''}
    <section class="public-section">
      <header class="public-section-header">
        <h2>${paymentPending ? 'Complete payment' : 'Payment in progress'}</h2>
        <p>${paymentPending ? 'Choose a payment provider to start the secure payment process.' : 'Hostvero will unlock your stay after the payment provider verifies your payment. Refresh this page in a few moments to see your confirmed stay and check-in details.'}</p>
      </header>
      ${paymentPending ? `
        <form id="public-payment" class="public-registration-form">
          <div class="field">
            <label for="public-payment-provider">Payment method</label>
            <select id="public-payment-provider" name="provider"><option value="PAYSTACK">Paystack — M-Pesa or card</option><option value="STRIPE">Stripe</option></select>
          </div>
          <div class="public-summary" data-paystack-fee>
            <div class="public-summary-item"><span>Hostvero service fee (5%)</span><strong>${formatMoney(result.payment.paystackServiceFee, result.payment.currency)}</strong></div>
            <div class="public-summary-item"><span>Total with Paystack</span><strong>${formatMoney(result.payment.paystackTotal, result.payment.currency)}</strong></div>
          </div>
          <div class="public-form-actions"><button class="button" type="submit">Pay securely</button></div>
        </form>
      ` : '<div class="public-form-note">Refresh this page in a few moments after your provider confirms payment.</div>'}
    </section>
  `;
}

function renderPrePaymentStayAccess(property) {
  const checkInTime = property.checkInTime ? String(property.checkInTime).slice(0, 5) : '—';
  const checkOutTime = property.checkOutTime ? String(property.checkOutTime).slice(0, 5) : '—';
  return `
    ${property.mapsUrl ? `<div class="public-primary-action"><a class="button secondary" target="_blank" rel="noopener noreferrer" href="${escapeHtml(property.mapsUrl)}">Open directions</a></div>` : ''}
    <section class="public-section">
      <header class="public-section-header"><h2>Your stay information</h2><p>Your host has made these details available before payment. Payment is still required to confirm your booking.</p></header>
      <div class="public-info-list">
        <div class="public-info-row"><div><span>Check-in</span><strong>${escapeHtml(checkInTime)}</strong></div></div>
        <div class="public-info-row"><div><span>Check-out</span><strong>${escapeHtml(checkOutTime)}</strong></div></div>
        <div class="public-info-row"><div><span>Wi-Fi network</span><strong>${escapeHtml(property.wifiName || 'Ask your host')}</strong></div></div>
        <div class="public-info-row"><div><span>Wi-Fi password</span><strong>${escapeHtml(property.wifiPassword || 'Ask your host')}</strong></div></div>
        <div class="public-info-row"><div><span>Host contact</span><strong>${escapeHtml(property.contactPhone || 'Contact your host directly')}</strong></div></div>
      </div>
    </section>
    <section class="public-section"><header class="public-section-header"><h2>Check-in instructions</h2></header><p class="public-text">${escapeHtml(property.checkInInstructions || 'Your host will provide arrival instructions.')}</p></section>
    <section class="public-section"><header class="public-section-header"><h2>House rules</h2></header><p class="public-text">${escapeHtml(property.houseRules || 'Please treat the property with care.')}</p></section>
  `;
}

function renderEmailVerification(result) {
  if (result.emailVerified) {
    return `
      <section class="public-section">
        <header class="public-section-header">
          <h2>Email verified</h2>
          <p>Your email address has been verified for this stay.</p>
        </header>
      </section>
    `;
  }

  const resendAt = result.emailVerificationResendAvailableAt;
  const coolingDown = resendAt && Date.parse(resendAt) > Date.now();
  return `
    <section class="public-section">
      <header class="public-section-header">
        <h2>Verify email</h2>
        <p>Enter the six-digit code we sent to your email. This confirms you can access that mailbox.</p>
      </header>
      <form id="public-email-verification" class="public-registration-form">
        <div class="field">
          <label for="public-email-verification-code">Verification code</label>
          <input id="public-email-verification-code" name="code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required placeholder="123456">
        </div>
        <div class="public-form-actions">
          <button class="button" type="submit">Verify email</button>
          <button class="button secondary" data-resend-email-verification data-resend-available-at="${escapeHtml(resendAt || '')}" type="button" ${coolingDown ? 'disabled' : ''}>Resend code</button>
        </div>
      </form>
    </section>
  `;
}


function renderActiveStay(result, token) {
  const property = result.property;


  const checkInTime =
    property.checkInTime
      ? String(property.checkInTime).slice(0, 5)
      : '—';


  const checkOutTime =
    property.checkOutTime
      ? String(property.checkOutTime).slice(0, 5)
      : '—';


  return `
    <section class="public-summary">

      <div class="public-summary-item">

        <span>Check-in</span>

        <strong>
          ${formatDate(result.stay.checkInDate)}
        </strong>

        <small>
          ${escapeHtml(checkInTime)}
        </small>

      </div>


      <div class="public-summary-item">

        <span>Check-out</span>

        <strong>
          ${formatDate(result.stay.checkOutDate)}
        </strong>

        <small>
          ${escapeHtml(checkOutTime)}
        </small>

      </div>


      <div class="public-summary-item">

        <span>Receipt</span>

        <strong>
          ${
            result.receipt
              ? escapeHtml(result.receipt.receiptNumber)
              : '—'
          }
        </strong>

      </div>


      <div class="public-summary-item">

        <span>Payment</span>

        <div>
          ${badge(result.payment.status)}
        </div>

      </div>

    </section>


    ${
      property.mapsUrl
        ? `
          <div class="public-primary-action">

            <a
              class="button secondary"
              target="_blank"
              rel="noopener noreferrer"
              href="${escapeHtml(property.mapsUrl)}"
            >
              Open directions
            </a>

          </div>
        `
        : ''
    }

    ${
      result.receipt
        ? `
          <div class="public-primary-action">
            <a class="button secondary" target="_blank" rel="noopener noreferrer" href="/api/public/guest/${encodeURIComponent(token)}/receipt/document">View receipt</a>
            <a class="button secondary" href="/api/public/guest/${encodeURIComponent(token)}/receipt/document?download=true">Download receipt</a>
          </div>
        `
        : ''
    }


    <section class="public-section">

      <header class="public-section-header">

        <h2>Your stay</h2>

        <p>
          Everything you need while staying at this property.
        </p>

      </header>


      <div class="public-info-list">


        <div class="public-info-row">

          <div>
            <span>Wi-Fi network</span>

            <strong>
              ${escapeHtml(
                property.wifiName ||
                'Ask your host'
              )}
            </strong>
          </div>

        </div>


        <div class="public-info-row">

          <div>
            <span>Wi-Fi password</span>

            <strong>
              ${escapeHtml(
                property.wifiPassword ||
                'Ask your host'
              )}
            </strong>
          </div>

        </div>


        <div class="public-info-row">

          <div>
            <span>Host contact</span>

            <strong>
              ${escapeHtml(
                property.contactPhone ||
                'Contact your host directly'
              )}
            </strong>
          </div>

        </div>

      </div>

    </section>


    <section class="public-section">

      <header class="public-section-header">

        <h2>Check-in instructions</h2>

      </header>

      <p class="public-text">
        ${escapeHtml(
          property.checkInInstructions ||
          'Your host will provide arrival instructions.'
        )}
      </p>

    </section>


    <section class="public-section">

      <header class="public-section-header">

        <h2>House rules</h2>

      </header>

      <p class="public-text">
        ${escapeHtml(
          property.houseRules ||
          'Please treat the property with care.'
        )}
      </p>

    </section>


    <section class="public-section public-manage-stay">

      <header class="public-section-header">

        <h2>Manage your stay</h2>

        <p>
          Availability is checked before any change is confirmed.
        </p>

      </header>


      <div class="public-stay-actions">

        <button
          class="button secondary"
          id="public-extend"
          type="button"
        >
          Extend stay
        </button>


        <button
          class="button secondary"
          id="public-book-again"
          type="button"
        >
          Book again
        </button>

      </div>

    </section>
  `;
}
function bindPublicForms() {
  const token = decodeURIComponent(location.pathname.split('/').filter(Boolean).pop() || '');
  const encodedToken = encodeURIComponent(token);

  $('#public-registration')?.addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('button[type="submit"]', event.currentTarget);
    setButtonBusy(button, true);
    try {
      await put(`/api/public/guest/${encodedToken}/registration`, Object.fromEntries(new FormData(event.currentTarget)));
      await renderPublicGuest();
      bindPublicForms();
      try {
        await post(`/api/public/guest/${encodedToken}/email-verification`);
        await renderPublicGuest();
        bindPublicForms();
        toast('Your details have been saved. We sent an email verification code.', 'success');
      } catch (error) {
        toast(`Your details were saved. ${error.message}`, 'error');
      }
    } catch (error) {
      handleFormError(error, event.currentTarget);
    } finally {
      setButtonBusy(button, false);
    }
  });

  $('#public-email-verification')?.addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('button[type="submit"]', event.currentTarget);
    setButtonBusy(button, true);
    try {
      await post(`/api/public/guest/${encodedToken}/email-verification/confirm`, Object.fromEntries(new FormData(event.currentTarget)));
      await renderPublicGuest();
      bindPublicForms();
      toast('Your email has been verified.', 'success');
    } catch (error) {
      handleFormError(error, event.currentTarget);
    } finally {
      setButtonBusy(button, false);
    }
  });

  const resendButton = $('[data-resend-email-verification]');
  if (resendButton) {
    const startCooldown = availableAt => {
      const endAt = Date.parse(availableAt);
      const update = () => {
        const remaining = Math.max(0, Math.ceil((endAt - Date.now()) / 1000));
        resendButton.disabled = remaining > 0;
        resendButton.textContent = remaining > 0 ? `Resend in ${remaining}s` : 'Resend code';
        return remaining;
      };
      if (update() > 0) {
        const interval = window.setInterval(() => {
          if (update() === 0) window.clearInterval(interval);
        }, 1000);
      }
    };
    resendButton.addEventListener('click', async () => {
      setButtonBusy(resendButton, true, 'Sending…');
      try {
        const status = await post(`/api/public/guest/${encodedToken}/email-verification`);
        startCooldown(status.resendAvailableAt);
        toast('A new verification code has been sent.', 'success');
      } catch (error) {
        toast(error.message, 'error');
      } finally {
        if (!resendButton.disabled) setButtonBusy(resendButton, false);
      }
    });
    if (resendButton.dataset.resendAvailableAt) startCooldown(resendButton.dataset.resendAvailableAt);
  }

  $('#public-payment')?.addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('button[type="submit"]', event.currentTarget);
    setButtonBusy(button, true, 'Starting…');
    try {
      const payment = await post(`/api/public/guest/${encodedToken}/payments`, Object.fromEntries(new FormData(event.currentTarget)));
      if ((payment.provider === 'STRIPE' && isStripeCheckoutUrl(payment.nextAction))
          || (payment.provider === 'PAYSTACK' && isPaystackCheckoutUrl(payment.nextAction))) {
        window.location.assign(payment.nextAction);
        return;
      }
      await renderPublicGuest();
      bindPublicForms();
      toast(payment.nextAction || 'Payment initiated. Await provider verification.', 'success');
    } catch (error) {
      handleFormError(error, event.currentTarget);
    } finally {
      setButtonBusy(button, false);
    }
  });
  $('#public-payment-provider')?.addEventListener('change', event => {
    document.querySelectorAll('[data-paystack-fee]').forEach(item => {
      item.hidden = event.currentTarget.value !== 'PAYSTACK';
    });
  });
  $('#public-extend')?.addEventListener('click', () => publicStayAction(token, 'extend'));
  $('#public-book-again')?.addEventListener('click', () => publicStayAction(token, 'again'));
}
function isStripeCheckoutUrl(value) { try { const url = new URL(value); return url.protocol === 'https:' && url.hostname === 'checkout.stripe.com'; } catch { return false; } }
function isPaystackCheckoutUrl(value) { try { const url = new URL(value); return url.protocol === 'https:' && url.hostname === 'checkout.paystack.com'; } catch { return false; } }

function publicStayAction(token, type) { const extend = type === 'extend'; const modal = openModal({ title: extend ? 'Extend your stay' : 'Book again', body: `<p class="muted">Hostvero will check availability before creating your request.</p><form id="public-stay-form">${extend ? '<div class="field"><label>New checkout date</label><input name="newCheckOutDate" type="date" required></div>' : '<div class="form-grid"><div class="field"><label>Check-in date</label><input name="checkInDate" type="date" required></div><div class="field"><label>Check-out date</label><input name="checkOutDate" type="date" required></div></div>'}<div class="form-actions"><button class="button" type="submit">Check availability</button></div></form>` }); $('#public-stay-form', modal.root).addEventListener('submit', async event => { event.preventDefault(); const button = $('button[type="submit"]', event.currentTarget); setButtonBusy(button, true); try { const result = await post(`/api/public/guest/${encodeURIComponent(token)}/${extend ? 'extend' : 'book-again'}`, Object.fromEntries(new FormData(event.currentTarget))); modal.close(); toast(extend && result.status === 'PENDING_PAYMENT' ? 'Extension payment is required before your checkout changes.' : extend ? 'Your stay request was confirmed.' : 'Your future booking was created.', 'success'); renderPublicGuest(); } catch (error) { toast(error.status === 409 ? 'Those dates are no longer available.' : error.message, 'error'); setButtonBusy(button, false); } }); }

function renderGuestUnavailable() {
  app.innerHTML = `
    <main
      class="public-page safe-expired"
      id="app-main"
    >

      <section class="public-unavailable-card">

        <a
          class="public-logo-link"
          href="/"
          aria-label="Hostvero"
        >
          <img
            src="/images/hostvero-logo-clean.png"
            alt="Hostvero"
            class="public-logo"
          >
        </a>


        <div
          class="public-unavailable-icon"
          aria-hidden="true"
        >
          ⌁
        </div>


        <h1>
          This guest link is unavailable
        </h1>


        <p>
          The link may have expired or is no longer active.
          Please contact your host if you need a new one.
        </p>


        <div class="public-unavailable-note">
          For your security, Hostvero guest links are temporary.
        </div>

      </section>

    </main>
  `;
}
function notificationLabel(type) { return ({ TWO_DAY_REMINDER: 'Two-day arrival reminder', TWENTY_FOUR_HOUR_PAYMENT_REQUEST: '24-hour payment request', PAYMENT_REMINDER: 'Payment reminder', CHECKOUT_REMINDER: 'Checkout reminder' })[type] || titleCase(type); }
function guestLinkState(value) { return value === 'REGISTRATION_OR_PAYMENT' ? 'Guest registration and payment link ready' : titleCase(value); }
function handleFormError(error, form) {
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.';
  $$('.field-error', form).forEach(item => item.remove());

  if (error.details) {
    Object.entries(error.details).forEach(([key, value]) => {
      const field = $(`[name="${key}"]`, form);
      if (field) {
        const detail = document.createElement('span');
        detail.className = 'help field-error';
        detail.style.color = 'var(--hv-danger)';
        detail.textContent = value;
        field.after(detail);
      }
    });
    toast('Please check the highlighted fields.', 'error');
  } else {
    toast(message, 'error');
  }
}
function showHostError(route, error) { if (error?.status === 401) { session.clear(); state.host = null; go('login'); toast('Your session has expired.', 'error'); return; } app.innerHTML = hostShell(route, `${heading(pageTitle(route))}${emptyState('!', 'Unable to load this page', 'Please try again in a moment.', '<button class="button" type="button" id="retry-page">Try again</button>')}`); bindShell(); $('#retry-page').addEventListener('click', renderRoute); }
function bindShell() { $('#logout-button')?.addEventListener('click', async () => { try { await post('/api/auth/logout', {}); } catch { /* Session is cleared regardless. */ } session.clear(); state.host = null; go('login'); }); }

async function renderRoute() {
  if (isPublicRoute()) { await renderPublicGuest(); bindPublicForms(); return; }
  const route = hashRoute(); if (route === 'login' || route === 'register') { renderAuth(route); return; }
  if (!session.token) { go('login'); return; }
  try { state.host = state.host || await get('/api/me'); } catch (error) { session.clear(); state.host = null; go('login'); return; }
  if (route === 'overview') return renderOverview(); if (route.startsWith('bookings')) return renderBookings(route); if (route.startsWith('guests')) return renderGuests(route); if (route === 'properties/new') return renderPropertyForm(null); if (route.startsWith('properties')) return renderProperties(route); if (route === 'payments') return renderPayments(); if (route === 'notifications') return renderNotifications(); if (route === 'settings') return renderSettings(); go('overview');
}

window.addEventListener('hashchange', renderRoute);
renderRoute();

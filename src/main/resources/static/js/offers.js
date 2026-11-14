import { get, post } from './api.js';
import { $, escapeHtml, formatMoney, formatDateTime, setButtonBusy, toast } from './ui.js';

// Offer links identify reusable terms; a reservation always receives its own guest link.
export async function renderPublicOffer() {
  const app = $('#app');
  const token = encodeURIComponent(location.pathname.split('/').filter(Boolean).pop());
  app.innerHTML = '<main class="public-page"><div class="public-shell"><p>Loading property offer…</p></div></main>';
  try {
    const offer = await get(`/api/public/offers/${token}`);
    app.innerHTML = `<main class="public-page" id="app-main"><div class="public-shell">
      <header class="public-topbar"><a href="/" class="public-logo-link"><img src="/images/hostvero-logo-clean.png" alt="Hostvero" class="public-logo"></a><span>Property offer</span></header>
      <section class="public-section"><h1>${escapeHtml(offer.propertyName)}</h1>
      <p>${formatMoney(offer.nightlyPrice, offer.currency)} per night · Offer expires ${formatDateTime(offer.expiresAt)}</p>
      <p>Choose your dates to check availability. Dates are reserved only when you continue to registration. All stay times use Africa/Nairobi.</p>
      <form id="offer-dates" class="public-registration-form"><div class="form-grid">
        <div class="field"><label for="offer-in">Check-in</label><input id="offer-in" type="date" name="checkInDate" required></div>
        <div class="field"><label for="offer-out">Check-out</label><input id="offer-out" type="date" name="checkOutDate" required></div>
      </div><button class="button" type="submit">Check dates and price</button></form>
      <div id="offer-quote" role="status" aria-live="polite"></div></section></div></main>`;
    const form = $('#offer-dates');
    let attemptId = null;
    let attemptDates = null;
    form.addEventListener('input', () => { $('#offer-quote').replaceChildren(); });
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const button = $('button', form);
      const dates = Object.fromEntries(new FormData(form));
      const datesKey = JSON.stringify(dates);
      setButtonBusy(button, true);
      try {
        const quote = await post(`/api/public/offers/${token}/quote`, dates);
        if (JSON.stringify(Object.fromEntries(new FormData(form))) !== datesKey) return;
        $('#offer-quote').innerHTML = `<h2>${quote.available ? 'Your stay quote' : 'These dates are unavailable'}</h2>
          <p>${quote.nights} nights · ${formatMoney(quote.totalAmount, quote.currency)} accommodation.</p>
          <p>Paystack adds a 5% service fee of ${formatMoney(quote.paystackServiceFee, quote.currency)}: total ${formatMoney(quote.paystackTotal, quote.currency)}. Stripe total: ${formatMoney(quote.totalAmount, quote.currency)}.</p>
          ${quote.available ? '<p>Continue to start a 30-minute checkout hold. Register and explicitly confirm a future reservation, or pay now if check-in is within 24 hours. Payment deadlines will be shown on your booking link.</p><button id="offer-reserve" class="button" type="button">Continue to registration</button>' : '<p>Choose different dates and check again.</p>'}`;
        $('#offer-reserve')?.addEventListener('click', async event => {
          if (JSON.stringify(Object.fromEntries(new FormData(form))) !== datesKey) return;
          const reserveButton = event.currentTarget;
          setButtonBusy(reserveButton, true);
          try {
            // Retain the attempt across network retries; editing dates starts a new attempt.
            const stored = sessionStorage.getItem(`hostvero.offer-attempt.${token}.${datesKey}`);
            if (stored) { attemptId = stored; attemptDates = datesKey; }
            if (!attemptId || attemptDates !== datesKey) {
              attemptId = crypto.randomUUID(); attemptDates = datesKey;
              sessionStorage.setItem(`hostvero.offer-attempt.${token}.${datesKey}`, attemptId);
            }
            const existingLink = sessionStorage.getItem(`hostvero.offer-booking.${attemptId}`);
            if (existingLink) { location.assign(existingLink); return; }
            const reservation = await post(`/api/public/offers/${token}/bookings`, { ...dates, checkoutAttemptId: attemptId });
            sessionStorage.setItem(`hostvero.offer-booking.${attemptId}`, `/guest/${encodeURIComponent(reservation.guestLink.token)}`);
            location.assign(`/guest/${encodeURIComponent(reservation.guestLink.token)}`);
          } catch (error) { toast(error.message, 'error'); setButtonBusy(reserveButton, false); }
        });
        if (quote.available && sessionStorage.getItem(`hostvero.offer-attempt.${token}.${datesKey}`)) {
          const restart = document.createElement('button');
          restart.type = 'button'; restart.className = 'button secondary'; restart.textContent = 'Start a new checkout attempt';
          restart.addEventListener('click', () => {
            const oldId = sessionStorage.getItem(`hostvero.offer-attempt.${token}.${datesKey}`);
            if (oldId) sessionStorage.removeItem(`hostvero.offer-booking.${oldId}`);
            attemptId = null; attemptDates = null;
            sessionStorage.removeItem(`hostvero.offer-attempt.${token}.${datesKey}`);
            restart.remove(); toast('Ready for a new checkout attempt. Continue to registration.');
          });
          $('#offer-quote').append(restart);
        }
      } catch (error) { toast(error.message, 'error'); }
      finally { setButtonBusy(button, false); }
    });
  } catch (error) {
    app.innerHTML = `<main class="public-page"><div class="public-shell"><h1>Offer unavailable</h1><p>${escapeHtml(error.message)}</p><p>Please ask your host for a new offer.</p></div></main>`;
  }
}

export async function hostOffersContent(propertyId) {
  const property = await get(`/api/properties/${propertyId}`);
  const offers = await get(`/api/properties/${propertyId}/offers`);
  return `<section class="card card-pad"><h2>${escapeHtml(property.name)} — property offers</h2>
    <p>Share a price without selecting dates. Each offer lasts seven days and can be reused until revoked or expired.</p>
    <form id="create-offer"><div class="field"><label for="offer-price">Approved nightly price (${escapeHtml(property.currency)})</label>
    <input id="offer-price" name="nightlyPrice" type="number" min="0.01" max="9999999999.99" step="0.01" required value="${escapeHtml(property.defaultNightlyRate)}"></div>
    <p>Price and currency cannot be edited after creation.</p><button class="button" type="submit">Create shareable offer</button></form>
    <div id="new-offer-link" role="status"></div>
    <h3>Offers</h3>${offers.length ? offers.map(o => `<div class="detail-row"><span>${formatMoney(o.nightlyPrice, o.currency)} / night · Expires ${formatDateTime(o.expiresAt)}</span>
      ${o.revokedAt ? '<strong>Revoked</strong>' : new Date(o.expiresAt) <= new Date() ? '<strong>Expired</strong>' : `<button class="button secondary small" data-revoke-offer="${o.id}" type="button">Revoke</button>`}</div>`).join('') : '<p>No offers yet.</p>'}
    <p><a href="#/properties">Back to properties</a></p></section>`;
}

export function bindHostOffers(propertyId, refresh) {
  $('#create-offer').addEventListener('submit', async event => {
    event.preventDefault();
    const button = $('button', event.currentTarget);
    const price = new FormData(event.currentTarget).get('nightlyPrice');
    setButtonBusy(button, true);
    try {
      const result = await post(`/api/properties/${propertyId}/offers`, { nightlyPrice: price });
      await refresh();
      const url = `${location.origin}/offer/${encodeURIComponent(result.token)}`;
      $('#new-offer-link').innerHTML = `<p>Copy this link now. It is shown only once.</p><label for="share-offer-link">Shareable offer link</label><input id="share-offer-link" readonly value="${escapeHtml(url)}"><button id="copy-offer" class="button secondary" type="button">Copy link</button>`;
      $('#copy-offer').addEventListener('click', async () => {
        try { await navigator.clipboard.writeText(url); toast('Offer link copied.', 'success'); }
        catch { $('#share-offer-link').select(); toast('Select and copy the offer link.'); }
      });
    } catch (error) { toast(error.message, 'error'); }
    finally { setButtonBusy(button, false); }
  });
  document.querySelectorAll('[data-revoke-offer]').forEach(button => button.addEventListener('click', async () => {
    setButtonBusy(button, true);
    try { await post(`/api/offers/${button.dataset.revokeOffer}/revoke`, {}); await refresh(); }
    catch (error) { toast(error.message, 'error'); setButtonBusy(button, false); }
  }));
}

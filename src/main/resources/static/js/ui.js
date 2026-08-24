export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

export function escapeHtml(value = '') {
  return String(value).replace(/[&<>'"]/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]);
}

export function formatMoney(value, currency = '') {
  const number = Number(value || 0);
  try { return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency || 'KES', maximumFractionDigits: 2 }).format(number); }
  catch { return `${currency || ''} ${number.toFixed(2)}`.trim(); }
}

export function formatDate(value, options = { day: 'numeric', month: 'short', year: 'numeric' }) {
  if (!value) return '—';
  const date = new Date(`${String(value).slice(0, 10)}T12:00:00`);
  return Number.isNaN(date.valueOf()) ? '—' : new Intl.DateTimeFormat(undefined, options).format(date);
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? '—' : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

export function titleCase(value = '') { return String(value).toLowerCase().split('_').map(part => part ? part[0].toUpperCase() + part.slice(1) : '').join(' '); }
export function badge(status = '') { const label = titleCase(status); return `<span class="badge ${String(status).toLowerCase().replaceAll('_', '-')}">${escapeHtml(label)}</span>`; }

export function toast(message, kind = '') {
  const region = $('#toast-region');
  const item = document.createElement('div'); item.className = `toast ${kind}`; item.textContent = message;
  region.append(item); setTimeout(() => item.remove(), 4600);
}

export function openModal({ title, body, actions = '' }) {
  const backdrop = document.createElement('div'); backdrop.className = 'modal-backdrop'; backdrop.innerHTML = `<section class="modal" role="dialog" aria-modal="true" aria-labelledby="dialog-title"><header class="modal-header"><h2 id="dialog-title">${escapeHtml(title)}</h2><button class="icon-button" type="button" aria-label="Close dialog">×</button></header><div class="modal-body">${body}${actions ? `<div class="form-actions">${actions}</div>` : ''}</div></section>`;
  const close = () => { document.removeEventListener('keydown', onKeydown); backdrop.remove(); };
  const onKeydown = event => { if (event.key === 'Escape') close(); };
  $('.icon-button', backdrop).addEventListener('click', close); backdrop.addEventListener('click', event => { if (event.target === backdrop) close(); });
  document.body.append(backdrop); document.addEventListener('keydown', onKeydown); $('.icon-button', backdrop).focus(); return { root: backdrop, close };
}

export function confirmDialog({ title, message, confirmLabel = 'Confirm', danger = false }) {
  return new Promise(resolve => {
    const modal = openModal({ title, body: `<p class="muted">${escapeHtml(message)}</p>`, actions: `<button class="button secondary" type="button" data-cancel>Keep it</button><button class="button ${danger ? 'danger' : ''}" type="button" data-confirm>${escapeHtml(confirmLabel)}</button>` });
    $('[data-cancel]', modal.root).addEventListener('click', () => { modal.close(); resolve(false); });
    $('[data-confirm]', modal.root).addEventListener('click', () => { modal.close(); resolve(true); });
  });
}

export function setButtonBusy(button, busy, label = 'Saving…') { if (!button) return; if (busy) { button.dataset.label = button.textContent; button.textContent = label; button.disabled = true; } else { button.textContent = button.dataset.label || button.textContent; button.disabled = false; } }

export function emptyState(icon, title, description, action = '') { return `<section class="card empty"><div><div class="empty-icon" aria-hidden="true">${icon}</div><h2>${escapeHtml(title)}</h2><p class="muted">${escapeHtml(description)}</p>${action}</div></section>`; }

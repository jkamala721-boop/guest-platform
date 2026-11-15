import { api, get, put, del } from './api.js';
import { escapeHtml, toast } from './ui.js';

// Signed URLs live only in this view's memory. No local/session storage or service-worker caching.
export async function mountGallery(root, endpoint, manage = false) {
  if (!root) return;
  let gallery;
  let refreshPromise;
  const refresh = async () => {
    if (!refreshPromise) refreshPromise = get(endpoint).finally(() => { refreshPromise = null; });
    gallery = await refreshPromise;
    return gallery;
  };
  const current = () => root.isConnected;
  function image(photo, full = false) {
    const img = document.createElement('img');
    img.alt = 'Property photograph'; img.referrerPolicy = 'no-referrer';
    img.loading = full || photo.cover ? 'eager' : 'lazy';
    img.width = full ? photo.displayWidth : photo.thumbnailWidth;
    img.height = full ? photo.displayHeight : photo.thumbnailHeight;
    let retryUsed = false;
    const assign = p => {
      img.src = full ? p.displayUrl : p.thumbnailUrl;
      if (!full && p.thumbnailWidth !== p.displayWidth) {
        // Width descriptors must reflect actual long-edge resizing, including portraits.
        img.srcset = `${p.thumbnailUrl} ${p.thumbnailWidth}w, ${p.displayUrl} ${p.displayWidth}w`;
        img.sizes = '(max-width: 600px) 45vw, 320px';
      }
    };
    img.addEventListener('error', async () => {
      if (retryUsed || !current()) { img.alt = 'Photograph unavailable'; return; }
      retryUsed = true;
      try {
        await refresh();
        const replacement = gallery.photos.find(p => p.id === photo.id);
        if (replacement && current()) assign(replacement); else img.alt = 'Photograph unavailable';
      } catch { img.alt = 'Photograph unavailable'; }
    });
    assign(photo); return img;
  }
  async function fullscreen(id, trigger) {
    try { await refreshIfNeeded(); } catch { toast('Photographs are temporarily unavailable.', 'error'); return; }
    if (!current()) return;
    const dialog = document.createElement('dialog'); dialog.className = 'photo-lightbox';
    dialog.setAttribute('aria-label', 'Property photographs');
    dialog.innerHTML = '<div class="photo-lightbox-controls"><button type="button" data-prev aria-label="Previous photograph">Previous</button><span aria-live="polite"></span><button type="button" data-next aria-label="Next photograph">Next</button><button type="button" data-close>Close</button></div><div class="photo-lightbox-image"></div>';
    const show = photoId => {
      const index = gallery.photos.findIndex(p => p.id === photoId);
      if (index < 0) { dialog.close(); return; }
      id = photoId;
      dialog.querySelector('span').textContent = `Photograph ${index + 1} of ${gallery.photos.length}`;
      const img = image(gallery.photos[index], true);
      img.alt = `Property photograph ${index + 1} of ${gallery.photos.length}`;
      dialog.querySelector('.photo-lightbox-image').replaceChildren(img);
    };
    const step = async delta => {
      try { await refreshIfNeeded(); if (!dialog.open) return;
        const i = gallery.photos.findIndex(p => p.id === id);
        if (gallery.photos.length) show(gallery.photos[(i + delta + gallery.photos.length) % gallery.photos.length].id);
        else dialog.close();
      } catch { toast('Photographs are temporarily unavailable.', 'error'); }
    };
    dialog.querySelector('[data-prev]').onclick = () => step(-1);
    dialog.querySelector('[data-next]').onclick = () => step(1);
    dialog.querySelector('[data-close]').onclick = () => dialog.close();
    dialog.addEventListener('keydown', e => {
      if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); step(e.key === 'ArrowLeft' ? -1 : 1); }
      // Native modal dialog confines keyboard focus and handles Escape.
    });
    let startX = null;
    dialog.addEventListener('touchstart', e => { startX = e.changedTouches[0].clientX; }, { passive: true });
    dialog.addEventListener('touchend', e => {
      if (startX !== null && Math.abs(e.changedTouches[0].clientX - startX) > 60) step(e.changedTouches[0].clientX < startX ? 1 : -1);
      startX = null;
    }, { passive: true });
    dialog.addEventListener('close', () => { dialog.remove(); if (trigger.isConnected) trigger.focus(); }, { once: true });
    document.body.append(dialog); dialog.showModal(); show(id); dialog.querySelector('[data-close]').focus();
    // A route change must not leave a private gallery modal on screen.
    const observer = new MutationObserver(() => { if (!current()) { dialog.close(); observer.disconnect(); } });
    observer.observe(document.querySelector('#app'), { childList: true, subtree: true });
    dialog.addEventListener('close', () => observer.disconnect(), { once: true });
  }
  async function refreshIfNeeded() {
    if (gallery.photos.some(p => Date.parse(p.expiresAt) <= Date.now() + 5000)) await refresh();
  }
  const mutation = async action => {
    if (root.dataset.busy === 'true') return;
    root.dataset.busy = 'true'; root.querySelectorAll('button,input').forEach(b => { b.disabled = true; });
    try { await action(); await refresh(); render(); }
    catch (error) { toast(error.message, 'error'); }
    finally { root.dataset.busy = 'false'; root.querySelectorAll('button,input').forEach(b => { b.disabled = b.dataset.limit === 'true' || b.dataset.boundary === 'true'; }); }
  };
  function render() {
    if (!current()) return;
    if (!gallery.enabled) { root.replaceChildren(); return; }
    root.innerHTML = `<h2>Property photographs</h2>${manage ? '<p>JPEG or PNG, up to 10 MiB and 20 megapixels. Maximum 15 photographs. Originals are discarded.</p><form class="photo-upload"><label>Choose photograph <input type="file" name="file" accept="image/jpeg,image/png" required></label><button class="button" type="submit">Upload photograph</button></form>' : ''}<div class="photo-grid"></div><p class="photo-status" role="status">${gallery.photos.length ? '' : 'No photographs added yet.'}</p>`;
    const grid = root.querySelector('.photo-grid');
    const display = [...gallery.photos].sort((a,b) => Number(b.cover) - Number(a.cover) || a.sortOrder-b.sortOrder);
    for (const photo of (manage ? gallery.photos : display)) {
      const figure = document.createElement('figure');
      const button = document.createElement('button'); button.type = 'button'; button.className = 'photo-open';
      button.setAttribute('aria-label', `View photograph ${gallery.photos.indexOf(photo)+1}${photo.cover ? ', cover' : ''} full screen`);
      button.append(image(photo)); button.onclick = () => fullscreen(photo.id, button); figure.append(button);
      if (manage) {
        const caption = document.createElement('figcaption');
        caption.innerHTML = `${photo.cover ? '<strong>Cover</strong>' : '<button type="button" data-cover>Make cover</button>'}<button type="button" data-up aria-label="Move photograph earlier">Earlier</button><button type="button" data-down aria-label="Move photograph later">Later</button><button type="button" data-delete>Delete</button>`;
        const index = gallery.photos.indexOf(photo);
        caption.querySelector('[data-up]').disabled = index === 0;
        caption.querySelector('[data-down]').disabled = index === gallery.photos.length-1;
        caption.querySelectorAll('button:disabled').forEach(b => { b.dataset.boundary = 'true'; });
        caption.querySelector('[data-cover]')?.addEventListener('click', () => mutation(() => put(`${endpoint}/cover`, { photoId: photo.id })));
        caption.querySelector('[data-delete]').onclick = () => {
          if (confirm('Delete this photograph?')) mutation(() => del(`${endpoint}/${photo.id}`));
        };
        const move = delta => mutation(async () => {
          const ids = gallery.photos.map(p => p.id);
          const position = ids.indexOf(photo.id);
          if (position < 0 || position + delta < 0 || position + delta >= ids.length) {
            throw new Error('Photographs changed. Refresh and retry.');
          }
          [ids[position], ids[position+delta]] = [ids[position+delta], ids[position]];
          await put(`${endpoint}/order`, { photoIds: ids });
        });
        caption.querySelector('[data-up]').onclick = () => move(-1);
        caption.querySelector('[data-down]').onclick = () => move(1);
        figure.append(caption);
      }
      grid.append(figure);
    }
    if (manage) {
      const form = root.querySelector('form');
      if (gallery.photos.length >= 15) form.querySelectorAll('input,button').forEach(b => { b.disabled = true; b.dataset.limit = 'true'; });
      form.onsubmit = event => {
        event.preventDefault();
        const file = form.querySelector('input').files[0];
        if (!file || file.size > 10 * 1024 * 1024 || !['image/jpeg','image/png'].includes(file.type)) {
          toast('Choose a JPEG or PNG no larger than 10 MiB.', 'error'); return;
        }
        mutation(() => api(endpoint, { method: 'POST', body: new FormData(form) }));
      };
    }
  }
  try { await refresh(); render(); }
  catch {
    if (current()) {
      root.innerHTML = '<p>Photographs are temporarily unavailable.</p><button type="button">Retry photographs</button>';
      root.querySelector('button').onclick = () => mountGallery(root, endpoint, manage);
    }
  }
}

export async function hostPhotosContent(propertyId) {
  const property = await get(`/api/properties/${propertyId}`);
  return `<section class="card card-pad"><h1>${escapeHtml(property.name)} — photographs</h1><div id="property-photos"></div><a href="#/properties">Back to properties</a></section>`;
}

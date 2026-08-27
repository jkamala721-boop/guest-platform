const CACHE_NAME = 'hostvero-shell-v1';
const SHELL_ASSETS = [
  '/offline.html',
  '/css/tokens.css', '/css/base.css', '/css/layout.css', '/css/components.css', '/css/pages.css',
  '/js/app.js', '/js/api.js', '/js/ui.js',
  '/images/hostvero-logo-clean.png',
  '/images/pwa/hostvero-192.png', '/images/pwa/hostvero-512.png', '/images/pwa/hostvero-maskable-512.png'
];
self.addEventListener('install', event => { event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(SHELL_ASSETS))); self.skipWaiting(); });
self.addEventListener('activate', event => { event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))))); self.clients.claim(); });
function isSensitive(url) { return url.pathname.startsWith('/api/') || url.pathname.startsWith('/admin/') || url.pathname.startsWith('/guest/'); }
self.addEventListener('fetch', event => {
  const request = event.request; const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (isSensitive(url)) { event.respondWith(fetch(request, { cache: 'no-store' })); return; }
  if (request.mode === 'navigate') { event.respondWith(fetch(request).catch(() => caches.match('/offline.html'))); return; }
  if (SHELL_ASSETS.includes(url.pathname)) event.respondWith(caches.match(request).then(cached => cached || fetch(request)));
});

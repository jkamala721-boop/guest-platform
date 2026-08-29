const CACHE_NAME = 'hostvero-shell-v2';

const SHELL_ASSETS = [
  '/offline.html',
  '/css/tokens.css',
  '/css/base.css',
  '/css/layout.css',
  '/css/components.css',
  '/css/pages.css',
  '/js/app.js',
  '/js/api.js',
  '/js/ui.js',
  '/images/hostvero-logo-clean.png',
  '/images/pwa/hostvero-192.png',
  '/images/pwa/hostvero-512.png',
  '/images/pwa/hostvero-maskable-512.png'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(SHELL_ASSETS))
  );

  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys =>
        Promise.all(
          keys
            .filter(key => key !== CACHE_NAME)
            .map(key => caches.delete(key))
        )
      )
      .then(() => self.clients.claim())
  );
});

function isSensitive(url) {
  return (
    url.pathname.startsWith('/api/') ||
    url.pathname.startsWith('/admin/') ||
    url.pathname.startsWith('/guest/')
  );
}

async function networkFirst(request) {
  try {
    const response = await fetch(request, { cache: 'no-cache' });

    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      await cache.put(request, response.clone());
    }

    return response;
  } catch (error) {
    const cached = await caches.match(request);

    if (cached) {
      return cached;
    }

    throw error;
  }
}

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);

  if (request.method !== 'GET' || url.origin !== self.location.origin) {
    return;
  }

  // Never cache authenticated/private application data.
  if (isSensitive(url)) {
    event.respondWith(
      fetch(request, { cache: 'no-store' })
    );
    return;
  }

  // Navigations should always prefer the current server version.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request, { cache: 'no-store' })
        .catch(() => caches.match('/offline.html'))
    );
    return;
  }

  // App shell assets prefer the newest deployed version,
  // but remain available when the user is offline.
  if (SHELL_ASSETS.includes(url.pathname)) {
    event.respondWith(networkFirst(request));
  }
});
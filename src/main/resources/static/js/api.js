const TOKEN_KEY = 'hostvero.session-token';

export const session = {
  get token() { return sessionStorage.getItem(TOKEN_KEY); },
  set token(value) { value ? sessionStorage.setItem(TOKEN_KEY, value) : sessionStorage.removeItem(TOKEN_KEY); },
  clear() { sessionStorage.removeItem(TOKEN_KEY); }
};

export class ApiError extends Error {
  constructor(status, message, details) { super(message); this.status = status; this.details = details; }
}

function safeMessage(status, body) {
  if (status === 401) return 'Your session has expired. Please sign in again.';
  if (status === 404) return 'We could not find that item.';
  if (status === 409) return 'That action cannot be completed right now. Please refresh and try again.';
  if (status >= 500) return 'Hostvero is temporarily unavailable. Please try again shortly.';
  return body?.message || 'Please check the form and try again.';
}

export async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  const token = session.token;
  const isUnauthenticatedEndpoint = path === '/api/auth/login' || path === '/api/auth/register'
    || path.startsWith('/api/public/');
  if (token && !isUnauthenticatedEndpoint) headers.set('Authorization', `Bearer ${token}`);
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers });
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('json') ? await response.json().catch(() => null) : null;
  if (!response.ok) throw new ApiError(response.status, safeMessage(response.status, body), body?.validationErrors || null);
  return body;
}

export const get = (path) => api(path);
export const post = (path, data) => api(path, { method: 'POST', body: JSON.stringify(data) });
export const put = (path, data) => api(path, { method: 'PUT', body: JSON.stringify(data) });
export const del = (path) => api(path, { method: 'DELETE' });

export async function apiMany(paths) { return Promise.all(paths.map(get)); }

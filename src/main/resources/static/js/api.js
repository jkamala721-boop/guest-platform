export class ApiError extends Error {
  constructor(status, code, message, details = null, retryable = false) {
    super(message); this.status = status; this.code = code; this.details = details; this.retryable = retryable;
  }
}

export async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('json') ? await response.json().catch(() => null) : null;
  if (!response.ok) {
    throw new ApiError(response.status, body?.code || 'INTERNAL_ERROR',
      body?.message || 'Something went wrong. Please try again.', body?.validationErrors || null,
      Boolean(body?.retryable));
  }
  return body;
}

export const get = (path) => api(path);
export const post = (path, data) => api(path, { method: 'POST', body: JSON.stringify(data) });
export const put = (path, data) => api(path, { method: 'PUT', body: JSON.stringify(data) });
export const del = (path) => api(path, { method: 'DELETE' });
export async function apiMany(paths) { return Promise.all(paths.map(get)); }

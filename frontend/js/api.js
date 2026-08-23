const API_BASE = 'http://localhost:8080';
const TOKEN_KEY = 'shortlink_token';
const EMAIL_KEY = 'shortlink_email';

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function getStoredEmail() {
  return localStorage.getItem(EMAIL_KEY);
}

function setSession(token, email) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(EMAIL_KEY, email);
}

function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(EMAIL_KEY);
}

async function apiFetch(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body && body.message) {
        message = body.message;
      }
    } catch (parseError) {
      // response body wasn't JSON - fall back to the generic message above
    }
    throw new ApiError(message, response.status);
  }

  return response.status === 204 ? null : response.json();
}

function register(email, password) {
  return apiFetch('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  });
}

function login(email, password) {
  return apiFetch('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  });
}

function createUrl({ originalUrl, customAlias, expiresAt }, idempotencyKey) {
  return apiFetch('/api/v1/urls', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({
      originalUrl,
      customAlias: customAlias || null,
      expiresAt: expiresAt || null
    })
  });
}

function listUrls(limit, cursor) {
  const params = new URLSearchParams({ limit });
  if (cursor) {
    params.set('cursor', cursor);
  }
  return apiFetch(`/api/v1/urls?${params}`);
}

function disableUrl(id) {
  return apiFetch(`/api/v1/urls/${id}/disable`, { method: 'POST' });
}

function deleteUrl(id) {
  return apiFetch(`/api/v1/urls/${id}`, { method: 'DELETE' });
}

function getAnalytics(id) {
  return apiFetch(`/api/v1/urls/${id}/analytics`);
}

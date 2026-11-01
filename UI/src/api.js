// ============================================================
// FraudShield — API client
// All data comes from the backend (FEAT-001 contract). No mock data here.
// ============================================================

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

const SESSION_KEY = 'fraudshield-ui-session';

function readSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function clearSession() {
  try { localStorage.removeItem(SESSION_KEY); } catch {}
}

// The UI no longer chooses mock vs real. The backend config
// (fraudshield.mock.enabled) is the single source of truth. Real data is
// injected via Postman/REST. The UI simply displays whatever the backend has.
async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };

  if (auth) {
    const session = readSession();
    if (session?.accessToken) {
      headers['Authorization'] = 'Bearer ' + session.accessToken;
    }
  }

  const res = await fetch(API_BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401) {
    clearSession();
    if (window.location.pathname !== '/login') {
      window.location.assign('/login');
    }
    throw new Error('Session expired. Please sign in again.');
  }

  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }

  if (!res.ok) {
    const message = (data && data.message) || res.statusText || 'Request failed';
    throw new Error(message);
  }

  return data;
}

function toQuery(params) {
  const search = new URLSearchParams();
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') search.append(k, v);
  });
  const qs = search.toString();
  return qs ? '?' + qs : '';
}

// ---- Auth ----
export function login(email, password) {
  return request('/api/auth/login', { method: 'POST', body: { email, password }, auth: false });
}

export function refresh(refreshToken) {
  return request('/api/auth/refresh', { method: 'POST', body: { refreshToken }, auth: false });
}

export function logout() {
  return request('/api/auth/logout', { method: 'POST' });
}

export function getMe() {
  return request('/api/auth/me');
}

// ---- Transactions ----
export function getTransactions({ q, verdict, status } = {}) {
  return request('/api/transactions' + toQuery({ q, verdict, status }));
}

export function getTransaction(id) {
  return request('/api/transactions/' + encodeURIComponent(id));
}

export function createTransaction(body) {
  return request('/api/transactions', { method: 'POST', body });
}

// ---- Admin / verdicts ----
export function overrideVerdict(id, decision) {
  return request('/api/admin/verdicts/' + encodeURIComponent(id), { method: 'PUT', body: { decision } });
}

// ---- Rules ----
export function getRules() {
  return request('/api/admin/rules');
}

export function updateRule(id, patch) {
  return request('/api/admin/rules/' + encodeURIComponent(id), { method: 'PUT', body: patch });
}

// ---- Audit ----
export function getAuditLogs() {
  return request('/api/admin/audit-logs');
}

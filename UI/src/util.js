// ============================================================
// FraudShield — pure formatting helpers (no data).
// ============================================================

export function timeAgo(ts) {
  const s = Math.floor((Date.now() - ts) / 1000);
  if (s < 60) return s + 's ago';
  const m = Math.floor(s / 60);
  if (m < 60) return m + 'm ago';
  const h = Math.floor(m / 60);
  if (h < 24) return h + 'h ago';
  return Math.floor(h / 24) + 'd ago';
}

export function fmtMoney(amount, currency) {
  const sym = { USD: '$', EUR: '€', GBP: '£', INR: '₹', JPY: '¥' }[currency] || '';
  return sym + amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

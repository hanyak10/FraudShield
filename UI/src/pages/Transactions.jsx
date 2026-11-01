import { useState, useMemo, useEffect, useCallback } from 'react';
import { getTransactions, overrideVerdict } from '../api.js';
import { timeAgo, fmtMoney } from '../util.js';
import { useAuth } from '../auth.jsx';

export default function Transactions() {
  const { user } = useAuth();
  const canOverride = user?.role !== 'ROLE_USER'; // analysts+ can override
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [selected, setSelected] = useState(null);
  const [actionError, setActionError] = useState('');

  const load = useCallback(() => {
    let active = true;
    setLoading(true);
    setError('');
    getTransactions({})
      .then((data) => { if (active) setRows(Array.isArray(data) ? data : []); })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => load(), [load]);

  const filtered = useMemo(() => {
    return rows.filter((t) => {
      if (filter === 'FLAGGED' && t.verdict !== 'FLAGGED') return false;
      if (filter === 'PASSED' && t.verdict !== 'PASSED') return false;
      if (filter === 'PENDING' && t.status !== 'PENDING') return false;
      if (q) {
        const s = q.toLowerCase();
        return t.id.toLowerCase().includes(s) || t.userId.toLowerCase().includes(s) || t.merchantId.toLowerCase().includes(s);
      }
      return true;
    });
  }, [rows, q, filter]);

  const [toast, setToast] = useState('');

  async function override(id, decision) {
    setActionError('');
    try {
      const updated = await overrideVerdict(id, decision);
      setRows((prev) => prev.map((t) => (t.id === id ? updated : t)));
      setSelected(null); // close drawer so the updated row is clearly visible
      setToast(
        decision === 'FALSE_POSITIVE'
          ? `${id} marked as false positive → OVERRIDDEN`
          : `${id} confirmed as fraud`
      );
      setTimeout(() => setToast(''), 3500);
    } catch (err) {
      setActionError(err.message);
    }
  }

  if (loading) return <div className="empty">Loading transactions…</div>;
  if (error) return <div className="login-error">⚠️ {error}</div>;

  return (
    <>
      {toast && <div className="toast-ok">✓ {toast}</div>}
      <div className="table-card">
        <div className="table-toolbar">
          <input className="search-box" placeholder="🔍 Search by ID, user, or merchant…" value={q} onChange={(e) => setQ(e.target.value)} />
          <select className="filter-sel" value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="ALL">All verdicts</option>
            <option value="FLAGGED">Flagged only</option>
            <option value="PASSED">Passed only</option>
            <option value="PENDING">Pending review</option>
          </select>
          <span style={{ fontSize: 12.5, color: 'var(--text-muted)', fontWeight: 600 }}>{filtered.length} results</span>
        </div>
        <table>
          <thead>
            <tr>
              <th>ID</th><th>User</th><th>Amount</th><th>Merchant</th><th>Country</th><th>Score</th><th>Verdict</th><th>Status</th><th>Time</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((t) => (
              <tr key={t.id} className="clickable" onClick={() => { setActionError(''); setSelected(t); }}>
                <td className="mono">{t.id}</td>
                <td className="mono">{t.userId}</td>
                <td style={{ fontWeight: 700, color: 'var(--text)' }}>{fmtMoney(Number(t.amount), t.currency)}</td>
                <td className="mono">{t.merchantId}</td>
                <td className="mono">{t.country}</td>
                <td>
                  <span className="score-bar">
                    <span className="score-track"><span className="score-fill" style={{ width: Math.min(t.score, 100) + '%', background: t.score >= 50 ? 'var(--danger)' : 'var(--accent)' }} /></span>
                    <b>{t.score}</b>
                  </span>
                </td>
                <td><span className={'chip ' + t.verdict.toLowerCase()}>{t.verdict}</span></td>
                <td><span className={'chip ' + t.status.toLowerCase()}>{t.status}</span></td>
                <td style={{ color: 'var(--text-muted)' }}>{timeAgo(t.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && <div className="empty">No transactions match your filters.</div>}
      </div>

      <div className={'drawer-overlay' + (selected ? ' open' : '')} onClick={() => setSelected(null)} />
      <div className={'drawer' + (selected ? ' open' : '')}>
        {selected && (
          <>
            <div className="drawer-head">
              <h3>Transaction {selected.id}</h3>
              <span className={'chip ' + selected.verdict.toLowerCase()}>{selected.verdict}</span>
              <button className="icon-btn" onClick={() => setSelected(null)}>✕</button>
            </div>
            <div className="drawer-body">
              <div className="detail-row"><span className="detail-k">Amount</span><span className="detail-v">{fmtMoney(Number(selected.amount), selected.currency)}</span></div>
              <div className="detail-row"><span className="detail-k">User ID</span><span className="detail-v mono">{selected.userId}</span></div>
              <div className="detail-row"><span className="detail-k">Merchant</span><span className="detail-v mono">{selected.merchantId}</span></div>
              <div className="detail-row"><span className="detail-k">Country</span><span className="detail-v">{selected.country}</span></div>
              <div className="detail-row"><span className="detail-k">Fraud Score</span><span className="detail-v">{selected.score} / 100</span></div>
              <div className="detail-row"><span className="detail-k">Status</span><span className="detail-v"><span className={'chip ' + selected.status.toLowerCase()}>{selected.status}</span></span></div>
              <div className="detail-row"><span className="detail-k">Received</span><span className="detail-v">{timeAgo(selected.createdAt)}</span></div>

              <h4 style={{ margin: '20px 0 10px', fontSize: 13, textTransform: 'uppercase', letterSpacing: '.05em', color: 'var(--text-muted)' }}>Triggered Rules</h4>
              {(selected.triggered || []).length === 0 && <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>No fraud rules fired — this transaction passed cleanly.</p>}
              {(selected.triggered || []).map((r) => (
                <div className="rule-hit" key={r.id}>
                  <span>⚠️</span> {r.name}
                  <span className="w">+{r.weight}</span>
                </div>
              ))}

              {actionError && <div className="login-error" style={{ marginTop: 16 }}>⚠️ {actionError}</div>}
            </div>
            {canOverride && selected.verdict === 'FLAGGED' && (
              <div className="drawer-foot">
                <button className="btn btn-secondary" style={{ flex: 1, justifyContent: 'center' }} onClick={() => override(selected.id, 'FALSE_POSITIVE')}>
                  ✓ Mark False Positive
                </button>
                <button className="btn btn-danger" style={{ flex: 1, justifyContent: 'center' }} onClick={() => override(selected.id, 'CONFIRMED_FRAUD')}>
                  🚨 Confirm Fraud
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </>
  );
}

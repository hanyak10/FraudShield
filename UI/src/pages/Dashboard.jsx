import { useEffect, useState } from 'react';
import { getTransactions } from '../api.js';
import { timeAgo, fmtMoney } from '../util.js';

export default function Dashboard() {
  const [txns, setTxns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    getTransactions({})
      .then((data) => { if (active) setTxns(Array.isArray(data) ? data : []); })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  if (loading) return <div className="empty">Loading dashboard…</div>;
  if (error) return <div className="login-error">⚠️ {error}</div>;

  const total = txns.length;
  const flagged = txns.filter((t) => t.verdict === 'FLAGGED');
  const pending = flagged.filter((t) => t.status === 'PENDING');
  const fraudRate = total ? ((flagged.length / total) * 100).toFixed(1) : '0.0';
  const flaggedValue = flagged.reduce((s, t) => s + Number(t.amount), 0);

  const stats = [
    { ico: '💳', val: total, label: 'Transactions', delta: 'all verdicts', dir: 'up' },
    { ico: '🚩', val: flagged.length, label: 'Flagged', delta: fraudRate + '% fraud rate', dir: 'up' },
    { ico: '⏳', val: pending.length, label: 'Pending Review', delta: 'needs analyst', dir: 'up' },
    { ico: '💰', val: '$' + Math.round(flaggedValue).toLocaleString(), label: 'Flagged Value', delta: 'at risk', dir: 'up' },
  ];

  const recent = flagged.slice(0, 6);

  return (
    <>
      <div className="stat-grid">
        {stats.map((s) => (
          <div className="stat" key={s.label}>
            <div className="stat-ico">{s.ico}</div>
            <div className="stat-val">{s.val}</div>
            <div className="stat-label">{s.label}</div>
            <div className={'stat-delta ' + s.dir}>{s.delta}</div>
          </div>
        ))}
      </div>

      <div className="section-head">
        <h2>🚨 Recent Flagged Transactions</h2>
      </div>
      <div className="table-card">
        <table>
          <thead>
            <tr>
              <th>ID</th><th>User</th><th>Amount</th><th>Merchant</th><th>Score</th><th>Status</th><th>Time</th>
            </tr>
          </thead>
          <tbody>
            {recent.map((t) => (
              <tr key={t.id}>
                <td className="mono">{t.id}</td>
                <td className="mono">{t.userId}</td>
                <td style={{ fontWeight: 700, color: 'var(--text)' }}>{fmtMoney(Number(t.amount), t.currency)}</td>
                <td className="mono">{t.merchantId}</td>
                <td>
                  <span className="score-bar">
                    <span className="score-track"><span className="score-fill" style={{ width: Math.min(t.score, 100) + '%', background: t.score >= 50 ? 'var(--danger)' : 'var(--accent)' }} /></span>
                    <b>{t.score}</b>
                  </span>
                </td>
                <td><span className={'chip ' + t.status.toLowerCase()}>{t.status}</span></td>
                <td style={{ color: 'var(--text-muted)' }}>{timeAgo(t.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {recent.length === 0 && <div className="empty">No flagged transactions.</div>}
      </div>
    </>
  );
}

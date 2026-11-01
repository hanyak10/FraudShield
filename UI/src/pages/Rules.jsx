import { useState, useEffect, useCallback } from 'react';
import { getRules, updateRule } from '../api.js';
import { useAuth } from '../auth.jsx';

export default function Rules() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ROLE_ADMIN' || user?.role === 'ROLE_SUPER_ADMIN';
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  const load = useCallback(() => {
    let active = true;
    setLoading(true);
    setError('');
    getRules()
      .then((data) => { if (active) setRules(Array.isArray(data) ? data : []); })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => load(), [load]);

  async function toggle(r) {
    if (!isAdmin) return;
    setActionError('');
    try {
      const updated = await updateRule(r.id, { enabled: !r.enabled });
      setRules((prev) => prev.map((x) => (x.id === r.id ? updated : x)));
    } catch (err) {
      setActionError(err.message);
    }
  }

  const activeThreshold = 50;

  if (loading) return <div className="empty">Loading rules…</div>;
  if (error) return <div className="login-error">⚠️ {error}</div>;

  return (
    <>
      <div className="section-head">
        <h2>⚙️ Fraud Detection Rules</h2>
        <div className="spacer" />
        <span style={{ fontSize: 12.5, color: 'var(--text-muted)' }}>
          Flag threshold: <b style={{ color: 'var(--text)' }}>{activeThreshold}</b>
        </span>
      </div>

      {!isAdmin && (
        <div className="login-error" style={{ marginBottom: 16 }}>
          🔒 You are signed in as an Analyst. Only Admins can enable or disable rules (403 on write). Viewing is allowed.
        </div>
      )}

      {actionError && <div className="login-error" style={{ marginBottom: 16 }}>⚠️ {actionError}</div>}

      {rules.map((r) => (
        <div className="rule-card" key={r.id}>
          <div className="rule-weight">+{r.weight}</div>
          <div className="rule-info">
            <div className="rule-name">{r.name} {!r.enabled && <span className="chip pending" style={{ marginLeft: 6 }}>disabled</span>}</div>
            <div className="rule-desc">{r.desc || r.description}</div>
          </div>
          <button
            className={'toggle' + (r.enabled ? ' on' : '')}
            onClick={() => toggle(r)}
            disabled={!isAdmin}
            title={isAdmin ? 'Toggle rule' : 'Admins only'}
            style={{ opacity: isAdmin ? 1 : 0.5, cursor: isAdmin ? 'pointer' : 'not-allowed' }}
          />
        </div>
      ))}

      <div className="card" style={{ padding: 18, marginTop: 8, fontSize: 12.5, color: 'var(--text-muted)' }}>
        💡 Rules are stored in the database and reloaded by the Rules Engine every 60 seconds — changes take effect without a redeploy.
      </div>
    </>
  );
}

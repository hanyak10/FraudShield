import { useState, useEffect } from 'react';
import { getAuditLogs } from '../api.js';
import { timeAgo } from '../util.js';

export default function Audit() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    getAuditLogs()
      .then((data) => { if (active) setLogs(Array.isArray(data) ? data : []); })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return (
    <>
      <div className="section-head">
        <h2>📜 Audit Log</h2>
        <div className="spacer" />
        <span style={{ fontSize: 12.5, color: 'var(--text-muted)' }}>Every action is immutable &amp; timestamped</span>
      </div>

      {loading && <div className="empty">Loading audit log…</div>}
      {error && <div className="login-error">⚠️ {error}</div>}

      {!loading && !error && (
        <div className="card" style={{ padding: '6px 22px' }}>
          {logs.map((a) => (
            <div className="audit-item" key={a.id}>
              <div className="audit-dot">{a.ico}</div>
              <div className="audit-body">
                <div className="audit-action">{a.action}</div>
                <div className="audit-meta">
                  {a.actor} · IP {a.ip} · {timeAgo(a.at)}
                </div>
              </div>
            </div>
          ))}
          {logs.length === 0 && <div className="empty">No audit entries.</div>}
        </div>
      )}
    </>
  );
}

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

const HINTS = [
  { email: 'HKadmin@fraudshield.io', password: 'Admin@123' },
  { email: 'HKanalyst@fraudshield.io', password: 'Analyst@123' },
  { email: 'HKsuperadmin@fraudshield.io', password: 'SuperAdmin@123' },
];

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const res = await login(email, password);
    setSubmitting(false);
    if (res.ok) navigate('/');
    else setError(res.error || 'Sign in failed.');
  }

  function quickFill(u) {
    setEmail(u.email);
    setPassword(u.password);
    setError('');
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <div className="login-brand">
          <div className="login-badge">🛡️</div>
          <div>
            <div className="login-title">FraudShield</div>
          </div>
        </div>
        <div className="login-sub">Analyst Dashboard — sign in to continue</div>

        {error && <div className="login-error">{error}</div>}

        <div className="field">
          <label>Email</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="HKanalyst@fraudshield.io"
            autoComplete="username"
          />
        </div>
        <div className="field">
          <label>Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            autoComplete="current-password"
          />
        </div>
        <button
          type="submit"
          className="btn btn-primary"
          style={{ width: '100%', justifyContent: 'center', padding: '11px' }}
          disabled={submitting}
        >
          {submitting ? 'Signing in…' : 'Sign In'}
        </button>

        <div className="login-hint">
          <div style={{ marginBottom: 8, fontWeight: 700 }}>Demo accounts (click to fill):</div>
          {HINTS.map((u) => (
            <div className="row" key={u.email} onClick={() => quickFill(u)}>
              <code>{u.email}</code>
              <code>{u.password}</code>
            </div>
          ))}
        </div>
      </form>
    </div>
  );
}

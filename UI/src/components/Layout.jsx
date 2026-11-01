import { useState, useEffect } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { getTransactions } from '../api.js';

const THEME_KEY = 'fraudshield-ui-theme';

const NAV = [
  { to: '/', label: 'Dashboard', ico: '📊', end: true },
  { to: '/transactions', label: 'Transactions', ico: '💳', badgeKey: 'pending' },
  { to: '/rules', label: 'Fraud Rules', ico: '⚙️' },
  { to: '/audit', label: 'Audit Log', ico: '📜' },
];

const TITLES = {
  '/': 'Dashboard',
  '/transactions': 'Transactions',
  '/rules': 'Fraud Rules',
  '/audit': 'Audit Log',
};

export default function Layout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem(THEME_KEY) || 'light');
  const [pendingCount, setPendingCount] = useState(0);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(THEME_KEY, theme);
  }, [theme]);

  useEffect(() => { setMobileOpen(false); }, [location.pathname]);

  // Pending badge — all data comes from the backend.
  useEffect(() => {
    let active = true;
    getTransactions({ status: 'PENDING' })
      .then((data) => { if (active) setPendingCount(Array.isArray(data) ? data.length : 0); })
      .catch(() => { if (active) setPendingCount(0); });
    return () => { active = false; };
  }, [location.pathname]);

  const initials = (user?.name || 'U').split(' ').map((p) => p[0]).slice(0, 2).join('');
  const roleLabel = (user?.role || '').replace('ROLE_', '');

  return (
    <div className="app">
      <aside className={'sidebar' + (mobileOpen ? ' mobile-open' : '')}>
        <div className="sidebar-brand">
          <div className="sidebar-badge">F</div>
          <div>
            <div className="sidebar-name">FraudShield</div>
            <div className="sidebar-tag">Analyst Console</div>
          </div>
        </div>
        <nav className="nav">
          {NAV.map((n) => {
            const badge = n.badgeKey === 'pending' ? pendingCount : 0;
            return (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.end}
                className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}
              >
                <span className="ico">{n.ico}</span>
                {n.label}
                {badge > 0 && <span className="nav-count">{badge}</span>}
              </NavLink>
            );
          })}
        </nav>
        <div className="sidebar-foot">
          <button className="btn btn-secondary" style={{ width: '100%', justifyContent: 'center' }} onClick={logout}>
            ⎋ Sign out
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button className="icon-btn" style={{ display: 'none' }} onClick={() => setMobileOpen((v) => !v)}>☰</button>
          <div className="topbar-title">{TITLES[location.pathname] || 'FraudShield'}</div>
          <div className="topbar-spacer" />

          <span className="mock-badge real" title="All data is served live by the backend. Inject data via the Postman collection.">
            LIVE DATA
          </span>

          <button className="icon-btn" onClick={() => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))} title="Toggle theme">
            {theme === 'dark' ? '☀️' : '🌙'}
          </button>
          <div className="topbar-user">
            <div className="avatar">{initials}</div>
            <div className="user-meta">
              <div className="user-name">{user?.name} <span className="role-badge">{roleLabel}</span></div>
              <div className="user-email">{user?.email}</div>
            </div>
          </div>
        </header>
        <div className="page">
          <Outlet />
        </div>
      </div>
    </div>
  );
}

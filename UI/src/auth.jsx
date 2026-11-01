import { createContext, useContext, useState, useCallback } from 'react';
import * as api from './api.js';

const AuthContext = createContext(null);
const STORAGE_KEY = 'fraudshield-ui-session';

function loadSession() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  // Full session {accessToken, refreshToken, user:{name,email,role}} is persisted;
  // the app only ever sees the inner user object via `user`.
  const [session, setSession] = useState(loadSession);

  const login = useCallback(async (email, password) => {
    try {
      const res = await api.login(email, password);
      const next = {
        accessToken: res.accessToken,
        refreshToken: res.refreshToken,
        user: res.user,
      };
      setSession(next);
      try { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)); } catch {}
      return { ok: true };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }, []);

  const logout = useCallback(async () => {
    try { await api.logout(); } catch {}
    setSession(null);
    try { localStorage.removeItem(STORAGE_KEY); } catch {}
  }, []);

  const value = {
    user: session ? session.user : null,
    login,
    logout,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}

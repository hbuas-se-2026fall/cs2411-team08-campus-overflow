import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, api } from './api';
import type { Me } from './types';

interface AuthState {
  me: Me | null;
  loading: boolean;
  sessionError: string;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState('');

  const refresh = useCallback(async () => {
    try {
      setMe(await api.me());
      setSessionError('');
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setMe(null);
        setSessionError('');
      } else {
        setSessionError((error as Error).message);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    api.csrf().catch(() => undefined).then(refresh);
  }, [refresh]);

  useEffect(() => {
    const clear = () => setMe(null);
    window.addEventListener('auth:unauthorized', clear);
    return () => window.removeEventListener('auth:unauthorized', clear);
  }, []);

  const value = useMemo<AuthState>(() => ({
    me,
    loading,
    sessionError,
    login: async (username, password) => {
      setMe(await api.login(username, password));
      setSessionError('');
    },
    logout: async () => {
      try {
        await api.logout();
      } finally {
        setMe(null);
      }
    },
    refresh,
  }), [me, loading, refresh]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return ctx;
}

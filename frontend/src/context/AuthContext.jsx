/**
 * Authentication context — stores JWT token and user info in React state + localStorage.
 *
 * localStorage provides persistence across page refreshes; React state drives re-renders.
 * On mount, rehydrates from localStorage so the user stays logged in after a refresh.
 * The user object carries the organization (tenant) the account belongs to.
 */
import { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token'));

  useEffect(() => {
    if (token) {
      const stored = localStorage.getItem('user');
      if (stored) {
        try {
          const parsed = JSON.parse(stored);
          // Sessions created before organizations existed carry no organization: sign in again.
          if (!parsed.organizationId) {
            logout();
          } else {
            setUser(parsed);
          }
        } catch {
          logout();
        }
      }
    }
  }, []);

  function login(authResponse) {
    setToken(authResponse.token);
    const userData = {
      username: authResponse.username,
      role: authResponse.role,
      organizationId: authResponse.organizationId,
      organizationName: authResponse.organizationName,
    };
    setUser(userData);
    localStorage.setItem('token', authResponse.token);
    localStorage.setItem('user', JSON.stringify(userData));
  }

  function logout() {
    setToken(null);
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }

  const canModify = user?.role === 'ENGINEER' || user?.role === 'ADMIN';

  return (
    <AuthContext.Provider value={{ user, token, login, logout, isAuthenticated: !!token, canModify }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}

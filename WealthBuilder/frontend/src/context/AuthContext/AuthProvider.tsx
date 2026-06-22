import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { AuthContext } from './AuthContext';
import { fetchCurrentUser, loginUser, logoutUser, registerUser } from '../../services/authService';
import { setUnauthorizedHandler } from '../../services/apiClient';
import type { AuthContextValue, AuthStatus, Credentials, CurrentUser } from '../../types/auth';


interface AuthProviderProps {
    children: ReactNode;
}


/**
 * Owns the auth session. The token itself lives in an httpOnly cookie the browser manages, so
 * there's nothing to store here — only the current user. On load it asks the server who we are
 * (the cookie rides along); a 401 anywhere clears the session.
 */
export const AuthProvider = ({ children }: AuthProviderProps) => {
    const [user, setUser] = useState<CurrentUser | null>(null);
    // Starts 'loading': we don't know if the cookie is valid until the rehydrate call answers.
    const [status, setStatus] = useState<AuthStatus>('loading');
    const [justAuthenticated, setJustAuthenticated] = useState(false);

    const clearJustAuthenticated = useCallback(() => {
        setJustAuthenticated(false);
    }, []);

    // Drops the in-memory session. Used both by the explicit logout and by the global 401 handler
    // (where the cookie is already invalid, so there's nothing more to clear server-side).
    const clearSession = useCallback(() => {
        setUser(null);
        setStatus('unauthenticated');
        setJustAuthenticated(false);
    }, []);

    // Populate the session from a register/login response. justAuthenticated is set only here
    // (interactive sign-in), never on rehydrate, so the home screen plays its entrance once.
    const establishSession = useCallback((currentUser: CurrentUser) => {
        setUser(currentUser);
        setStatus('authenticated');
        setJustAuthenticated(true);
    }, []);

    const login = useCallback(async (credentials: Credentials) => {
        establishSession(await loginUser(credentials));
    }, [establishSession]);

    const register = useCallback(async (credentials: Credentials) => {
        establishSession(await registerUser(credentials));
    }, [establishSession]);

    const logout = useCallback(() => {
        // Ask the server to clear the cookie, then drop local state regardless of the outcome.
        logoutUser()
            .catch(() => undefined)
            .finally(clearSession);
    }, [clearSession]);

    // Re-fetch the current user on demand. A 401 is handled globally (session clear); any other
    // failure is swallowed so a transient error just leaves the previous user in place.
    const refreshUser = useCallback(async () => {
        try {
            setUser(await fetchCurrentUser());
        } catch {
            // Keep the existing user; a 401 already cleared the session via the global handler.
        }
    }, []);

    // A 401 on any request clears the session (apiClient calls this).
    useEffect(() => {
        setUnauthorizedHandler(clearSession);
    }, [clearSession]);

    // On first load, ask the server who we are. The httpOnly cookie (if any) rides along; a
    // success authenticates us, anything else leaves us unauthenticated.
    useEffect(() => {
        let active = true;

        fetchCurrentUser()
            .then((currentUser) => {
                if (active) {
                    setUser(currentUser);
                    setStatus('authenticated');
                }
            })
            .catch(() => {
                if (active) {
                    setStatus('unauthenticated');
                }
            });

        return () => {
            active = false;
        };
    }, []);

    const value = useMemo<AuthContextValue>(() => ({
        user,
        status,
        isAuthenticated: status === 'authenticated',
        justAuthenticated,
        login,
        register,
        logout,
        clearJustAuthenticated,
        refreshUser,
    }), [user, status, justAuthenticated, login, register, logout, clearJustAuthenticated, refreshUser]);

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { AuthContext } from './AuthContext';
import { fetchCurrentUser, loginUser, registerUser } from '../../services/authService';
import { setAuthToken, setUnauthorizedHandler } from '../../services/apiClient';
import { STORAGE_KEYS } from '../../constants/storage';
import type { AuthContextValue, AuthStatus, Credentials, CurrentUser } from '../../types/auth';


interface AuthProviderProps {
    children: ReactNode;
}


/**
 * Owns the auth session: the bearer token (mirrored to localStorage so a refresh stays
 * logged in) and the current user. Rehydrates on load and clears itself on any 401.
 */
const readStoredToken = (): string | null => {
    return localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
};


export const AuthProvider = ({ children }: AuthProviderProps) => {
    // Seed from localStorage so a page refresh keeps the session: a stored token starts
    // us in 'loading' (verified by the rehydrate effect), no token means 'unauthenticated'.
    const [token, setToken] = useState<string | null>(readStoredToken);
    const [user, setUser] = useState<CurrentUser | null>(null);
    const [status, setStatus] = useState<AuthStatus>(
        () => (readStoredToken() === null ? 'unauthenticated' : 'loading'),
    );
    const [justAuthenticated, setJustAuthenticated] = useState(false);

    const clearJustAuthenticated = useCallback(() => {
        setJustAuthenticated(false);
    }, []);

    const logout = useCallback(() => {
        localStorage.removeItem(STORAGE_KEYS.AUTH_TOKEN);
        setAuthToken(null);
        setToken(null);
        setUser(null);
        setStatus('unauthenticated');
        setJustAuthenticated(false);
    }, []);

    // Exchange a freshly issued token for a populated, authenticated session. The
    // justAuthenticated flag is set only here (interactive sign-in), never on rehydrate.
    const establishSession = useCallback(async (issuedToken: string) => {
        localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, issuedToken);
        setAuthToken(issuedToken);
        setToken(issuedToken);

        const currentUser = await fetchCurrentUser();

        setUser(currentUser);
        setStatus('authenticated');
        setJustAuthenticated(true);
    }, []);

    const login = useCallback(async (credentials: Credentials) => {
        const { token: issuedToken } = await loginUser(credentials);

        await establishSession(issuedToken);
    }, [establishSession]);

    const register = useCallback(async (credentials: Credentials) => {
        const { token: issuedToken } = await registerUser(credentials);

        await establishSession(issuedToken);
    }, [establishSession]);

    // A 401 on any authenticated request clears the session (apiClient calls this).
    useEffect(() => {
        setUnauthorizedHandler(logout);
    }, [logout]);

    // On first load, verify the seeded token by fetching the user. State was already
    // initialized from localStorage above, so the effect only does the async work.
    useEffect(() => {
        const storedToken = readStoredToken();

        if (storedToken === null) {
            return;
        }

        setAuthToken(storedToken);

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
                    logout();
                }
            });

        return () => {
            active = false;
        };
    }, [logout]);

    const value = useMemo<AuthContextValue>(() => ({
        user,
        token,
        status,
        isAuthenticated: status === 'authenticated',
        justAuthenticated,
        login,
        register,
        logout,
        clearJustAuthenticated,
    }), [user, token, status, justAuthenticated, login, register, logout, clearJustAuthenticated]);

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};

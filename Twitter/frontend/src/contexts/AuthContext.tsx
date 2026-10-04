import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { me } from '../api/auth';
import type { AuthUser } from '../api/auth';
import { setUnauthorizedHandler } from '../api/http';


export type SessionStatus = 'loading' | 'authenticated' | 'anonymous';

interface Session {
    status: SessionStatus;
    user: AuthUser | null;
}

interface AuthContextValue extends Session {
    signIn: (user: AuthUser) => void;
    signOut: () => void;
}

interface AuthProviderProps {
    children: ReactNode;
}

const LOADING_SESSION: Session = {
    status: 'loading',
    user: null,
};

const ANONYMOUS_SESSION: Session = {
    status: 'anonymous',
    user: null,
};

function authenticatedSession(authUser: AuthUser): Session {
    return {
        status: 'authenticated',
        user: authUser,
    };
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: AuthProviderProps) {
    const [session, setSession] = useState<Session>(LOADING_SESSION);

    useEffect(() => {
        let isCancelled = false;

        function showSession(nextSession: Session) {
            if (!isCancelled) {
                setSession(nextSession);
            }
        }

        me()
            .then((authUser) => showSession(authenticatedSession(authUser)))
            .catch(() => showSession(ANONYMOUS_SESSION));

        return () => {
            isCancelled = true;
        };
    }, []);

    // An expired session on any non-auth call ends the session; ProtectedRoute then sends the user to the login page.
    useEffect(() => {
        setUnauthorizedHandler(() => setSession(ANONYMOUS_SESSION));

        return () => setUnauthorizedHandler(null);
    }, []);

    function signIn(authUser: AuthUser) {
        setSession(authenticatedSession(authUser));
    }

    function signOut() {
        setSession(ANONYMOUS_SESSION);
    }

    const value: AuthContextValue = {
        ...session,
        signIn,
        signOut,
    };

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}

// The context convention keeps the provider and its hook in one file, so fast refresh reloads the page here.
// oxlint-disable-next-line react/only-export-components
export function useAuth(): AuthContextValue {
    const context = useContext(AuthContext);

    if (!context) {
        throw new Error('useAuth must be used inside AuthProvider.');
    }

    return context;
}

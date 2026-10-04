import { createContext, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { me } from '../api/auth';
import type { AuthUser } from '../api/auth';
import { setUnauthorizedHandler } from '../api/http';
import { prefersReducedMotion } from '../utils/motion';


export type SessionStatus = 'loading' | 'authenticated' | 'anonymous';

interface Session {
    status: SessionStatus;
    user: AuthUser | null;
}

interface AuthContextValue extends Session {
    isSigningOut: boolean;
    hasSignedOut: boolean;
    signIn: (user: AuthUser) => void;
    signOut: () => void;
}

interface AuthProviderProps {
    children: ReactNode;
}

// How long the feed plays out before the session ends; the longest leave animation in Header.css and PostList.css
// is 1200ms.
const SIGN_OUT_LEAVE_MS = 1200;

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
    const [isSigningOut, setIsSigningOut] = useState(false);
    const [hasSignedOut, setHasSignedOut] = useState(false);
    const signOutTimerIdRef = useRef<number | undefined>(undefined);

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

    useEffect(() => () => window.clearTimeout(signOutTimerIdRef.current), []);

    function signIn(authUser: AuthUser) {
        setSession(authenticatedSession(authUser));
        setHasSignedOut(false);
    }

    function endSession() {
        setSession(ANONYMOUS_SESSION);
        setIsSigningOut(false);
        setHasSignedOut(true);
    }

    // The session ends after the feed has played out, so the leave animation runs while the feed is still mounted.
    function signOut() {
        if (isSigningOut) {
            return;
        }

        setIsSigningOut(true);
        signOutTimerIdRef.current = window.setTimeout(
            endSession,
            prefersReducedMotion() ? 0 : SIGN_OUT_LEAVE_MS,
        );
    }

    const value: AuthContextValue = {
        ...session,
        isSigningOut,
        hasSignedOut,
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

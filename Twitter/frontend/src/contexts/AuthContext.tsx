import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { me } from '../api/auth';
import type { AuthUser } from '../api/auth';


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

const LOADING_SESSION: Session = { status: 'loading', user: null };

const ANONYMOUS_SESSION: Session = { status: 'anonymous', user: null };

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
            .then((authUser) => showSession({ status: 'authenticated', user: authUser }))
            .catch(() => showSession(ANONYMOUS_SESSION));

        return () => {
            isCancelled = true;
        };
    }, []);

    function signIn(authUser: AuthUser) {
        setSession({ status: 'authenticated', user: authUser });
    }

    function signOut() {
        setSession(ANONYMOUS_SESSION);
    }

    return (
        <AuthContext.Provider value={{ ...session, signIn, signOut }}>
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

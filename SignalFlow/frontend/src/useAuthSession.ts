import { useEffect, useState } from 'react';
import { me } from './api/auth';
import type { AuthUser } from './api/auth';

export type SessionStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface AuthSession {
    status: SessionStatus;
    user: AuthUser | null;
    startSession: (authUser: AuthUser) => void;
    endSession: () => void;
}

export function useAuthSession(): AuthSession {
    const [status, setStatus] = useState<SessionStatus>('loading');
    const [user, setUser] = useState<AuthUser | null>(null);

    useEffect(() => {
        let isCancelled = false;

        me()
            .then((authUser) => {
                if (isCancelled) {
                    return;
                }

                setUser(authUser);
                setStatus('authenticated');
            })
            .catch(() => {
                if (isCancelled) {
                    return;
                }

                setStatus('unauthenticated');
            });

        return () => {
            isCancelled = true;
        };
    }, []);

    function startSession(authUser: AuthUser) {
        setUser(authUser);
        setStatus('authenticated');
    }

    function endSession() {
        setUser(null);
        setStatus('unauthenticated');
    }

    return {
        status,
        user,
        startSession,
        endSession,
    };
}

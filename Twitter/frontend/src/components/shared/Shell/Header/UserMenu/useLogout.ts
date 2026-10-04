import { useState } from 'react';
import { logout } from '../../../../../api/auth';
import { useAuth } from '../../../../../contexts/AuthContext';


interface Logout {
    isLoggingOut: boolean;
    hasFailed: boolean;
    logOut: () => void;
}

// Signing out makes ProtectedRoute send the user to the login page.
export function useLogout(): Logout {
    const { signOut } = useAuth();
    const [isLoggingOut, setIsLoggingOut] = useState(false);
    const [hasFailed, setHasFailed] = useState(false);

    async function logOut() {
        if (isLoggingOut) {
            return;
        }

        setIsLoggingOut(true);
        setHasFailed(false);

        try {
            await logout();

            signOut();

        } catch {
            setHasFailed(true);
            setIsLoggingOut(false);
        }
    }

    return {
        isLoggingOut,
        hasFailed,
        logOut,
    };
}

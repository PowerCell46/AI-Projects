import { useEffect, useRef, useState } from 'react';
import { AuthApiError, confirm } from '../../api/auth';


export type ConfirmationStatus = 'pending' | 'confirmed' | 'expired' | 'failed';

export interface Confirmation {
    status: ConfirmationStatus;
    retry: () => void;
}

const STATUS_BAD_REQUEST = 400;

async function requestConfirmation(token: string, onStatus: (status: ConfirmationStatus) => void) {
    try {
        await confirm(token);

        onStatus('confirmed');

    } catch (failure) {
        const isRejected = failure instanceof AuthApiError && failure.status === STATUS_BAD_REQUEST;

        onStatus(isRejected ? 'expired' : 'failed');
    }
}

export function useConfirmation(token: string | null): Confirmation {
    const [status, setStatus] = useState<ConfirmationStatus>(token ? 'pending' : 'expired');
    const hasStartedRef = useRef(false);

    // The token is single-use and StrictMode runs effects twice in development, so the ref lets only the first
    // run send it. That run's result must not be dropped on cleanup, because the second run never repeats it.
    useEffect(() => {
        if (!token || hasStartedRef.current) {
            return;
        }

        hasStartedRef.current = true;
        requestConfirmation(token, setStatus);
    }, [token]);

    function retry() {
        if (token) {
            setStatus('pending');
            requestConfirmation(token, setStatus);
        }
    }

    return { status, retry };
}

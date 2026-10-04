import { useEffect, useState } from 'react';
import { resendConfirmation } from '../../../api/auth';
import { SIGNAL_LOST_MESSAGE } from '../../../utils/authErrors';
import './ResendButton.css';


type ResendStatus = 'idle' | 'sending' | 'cooling' | 'failed';

// Matches the gateway's CONFIRMATION_RESEND_COOLDOWN; it throttles silently, so the button explains the wait.
export const RESEND_COOLDOWN_MS = 60_000;

const RESEND_LABEL = 'RESEND';

const SENT_LABEL = 'LINK SENT';

interface ResendButtonProps {
    email: string;
    startsInCooldown?: boolean;
}

function labelFor(status: ResendStatus): string {
    if (status === 'cooling') {
        return SENT_LABEL;
    }

    return status === 'failed' ? SIGNAL_LOST_MESSAGE : RESEND_LABEL;
}

function ResendButton({ email, startsInCooldown = false }: ResendButtonProps) {
    const [status, setStatus] = useState<ResendStatus>(startsInCooldown ? 'cooling' : 'idle');

    useEffect(() => {
        if (status !== 'cooling') {
            return;
        }

        const cooldownTimerId = window.setTimeout(
            () => setStatus('idle'),
            RESEND_COOLDOWN_MS,
        );

        return () => window.clearTimeout(cooldownTimerId);
    }, [status]);

    async function handleResend() {
        if (status === 'sending' || status === 'cooling') {
            return;
        }

        setStatus('sending');

        try {
            await resendConfirmation(email);

            setStatus('cooling');

        } catch {
            setStatus('failed');
        }
    }

    return (
        <button
            type="button"
            className="resend-button"
            aria-disabled={status === 'sending' || status === 'cooling'}
            onClick={handleResend}
        >
            {labelFor(status)}
        </button>
    );
}

export default ResendButton;

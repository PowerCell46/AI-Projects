export interface ResendLocationState {
    email: string;
}

const EMAIL_MARKER = '@';

// Login accepts an email or a username; only an email can prefill the resend form.
export function buildResendState(identifier: string): ResendLocationState | undefined {
    const trimmedIdentifier = identifier.trim();

    return trimmedIdentifier.includes(EMAIL_MARKER) ? { email: trimmedIdentifier } : undefined;
}

export function readPrefilledEmail(locationState: unknown): string {
    if (typeof locationState === 'object'
        && locationState !== null
        && 'email' in locationState
        && typeof locationState.email === 'string') {
        return locationState.email;
    }

    return '';
}

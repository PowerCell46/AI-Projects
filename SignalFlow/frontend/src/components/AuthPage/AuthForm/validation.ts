// Mirrors RegisterRequestDTO/LoginRequestDTO's @Pattern rules on the gateway — kept in sync with
// signal_flow_api_gateway so obviously-invalid input never reaches the network.
export const EMAIL_MAX_LENGTH = 254;

const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/;

const PASSWORD_MIN_LENGTH = 8;

export const PASSWORD_MAX_LENGTH = 72;

const PASSWORD_COMPLEXITY_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;

export function validateEmail(value: string): string | null {
    const trimmed = value.trim();

    if (!trimmed) {
        return 'Enter your email.';
    }

    if (trimmed.length > EMAIL_MAX_LENGTH) {
        return `Email must be ${EMAIL_MAX_LENGTH} characters or fewer.`;
    }

    if (!EMAIL_PATTERN.test(trimmed)) {
        return 'Enter a valid email address.';
    }

    return null;
}

export function validatePassword(value: string): string | null {
    if (!value) {
        return 'Enter your password.';
    }

    if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
        return `Password must be ${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH} characters.`;
    }

    if (!PASSWORD_COMPLEXITY_PATTERN.test(value)) {
        return 'Password needs an uppercase letter, a lowercase letter, and a number.';
    }

    return null;
}

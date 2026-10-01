import type { FlowField, FlowName } from '../flows';


export const EMPTY_FIELD_MESSAGE = 'NO VALUE ENTERED — HOLDING AT DEPTH';

export const EMAIL_MESSAGE = 'EMAIL MUST BE A VALID EMAIL ADDRESS';

export const USERNAME_MESSAGE = 'USERNAME MUST BE 3 TO 15 LETTERS, DIGITS OR UNDERSCORES';

export const PASSWORD_LENGTH_MESSAGE = 'PASSWORD MUST BE 8 TO 72 CHARACTERS';

export const PASSWORD_COMPOSITION_MESSAGE = 'PASSWORD MUST CONTAIN A LOWERCASE LETTER, AN UPPERCASE LETTER AND A DIGIT';

// These rules mirror RegisterRequestDTO in the gateway; change both sides together.
const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/;

const EMAIL_MAX_LENGTH = 254;

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,15}$/;

const PASSWORD_MIN_LENGTH = 8;

const PASSWORD_MAX_LENGTH = 72;

const PASSWORD_MAX_BYTES = 72;

const LOWERCASE_PATTERN = /[a-z]/;

const UPPERCASE_PATTERN = /[A-Z]/;

const DIGIT_PATTERN = /\d/;

function validateEmail(value: string): string | null {
    const isValid = value.length <= EMAIL_MAX_LENGTH && EMAIL_PATTERN.test(value);

    return isValid ? null : EMAIL_MESSAGE;
}

function validateUsername(value: string): string | null {
    return USERNAME_PATTERN.test(value) ? null : USERNAME_MESSAGE;
}

function utf8ByteLength(value: string): number {
    return new TextEncoder().encode(value).length;
}

function validateNewPassword(value: string): string | null {
    // value.length counts UTF-16 units, the same as Java's String.length() behind @Size.
    const hasValidLength = value.length >= PASSWORD_MIN_LENGTH
        && value.length <= PASSWORD_MAX_LENGTH
        && utf8ByteLength(value) <= PASSWORD_MAX_BYTES;

    if (!hasValidLength) {
        return PASSWORD_LENGTH_MESSAGE;
    }

    const hasAllCharacterKinds = LOWERCASE_PATTERN.test(value)
        && UPPERCASE_PATTERN.test(value)
        && DIGIT_PATTERN.test(value);

    return hasAllCharacterKinds ? null : PASSWORD_COMPOSITION_MESSAGE;
}

function validateFormat(flowName: FlowName, field: FlowField, value: string): string | null {
    if (field === 'email') {
        return validateEmail(value);
    }

    if (flowName === 'register' && field === 'username') {
        return validateUsername(value);
    }

    if (flowName === 'register' && field === 'password') {
        return validateNewPassword(value);
    }

    return null;
}

export function validateField(flowName: FlowName, field: FlowField, value: string): string | null {
    if (value.trim() === '') {
        return EMPTY_FIELD_MESSAGE;
    }

    return validateFormat(flowName, field, value);
}

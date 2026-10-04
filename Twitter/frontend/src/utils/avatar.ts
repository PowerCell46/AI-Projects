export const AVATAR_TINT_COUNT = 8;

const LETTER_OR_DIGIT_PATTERN = /[\p{L}\p{N}]/gu;

const INITIAL_COUNT = 2;

const NO_INITIALS = '?';

const HASH_OFFSET_BASIS = 2166136261;

const HASH_PRIME = 16777619;

export function initialsOf(username: string): string {
    const lettersAndDigits = username.match(LETTER_OR_DIGIT_PATTERN) ?? [];

    const initials = lettersAndDigits
        .slice(0, INITIAL_COUNT)
        .join('')
        .toUpperCase();

    return initials || NO_INITIALS;
}

// FNV-1a: the same id always lands on the same tint, on every device and every load.
export function avatarTintOf(userId: string): number {
    let hash = HASH_OFFSET_BASIS;

    for (const character of userId) {
        hash ^= character.codePointAt(0) ?? 0;
        hash = Math.imul(hash, HASH_PRIME);
    }

    return (hash >>> 0) % AVATAR_TINT_COUNT;
}

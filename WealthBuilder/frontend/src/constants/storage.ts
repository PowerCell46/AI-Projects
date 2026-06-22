// localStorage keys. Namespaced so they never collide with other apps on the same origin.
// The auth token is intentionally absent: it lives in an httpOnly cookie the browser manages,
// out of reach of JavaScript (and therefore of XSS).

export const STORAGE_KEYS = {
    THEME: 'wealthbuilder.theme',
} as const;

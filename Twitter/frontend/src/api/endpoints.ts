const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? '';

const API_V1 = `${BASE_URL}/api/v1`;

export const ENDPOINTS = {
    auth: {
        register: `${API_V1}/auth/register`,
        login: `${API_V1}/auth/login`,
        logout: `${API_V1}/auth/logout`,
        me: `${API_V1}/auth/me`,
        confirm: `${API_V1}/auth/confirm`,
        resendConfirmation: `${API_V1}/auth/confirm/resend`,
    },
} as const;

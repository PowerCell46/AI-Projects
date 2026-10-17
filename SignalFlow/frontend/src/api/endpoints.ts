const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? '';

const API_V1 = `${BASE_URL}/api/v1`;

function segment(value: string): string {
    return encodeURIComponent(value);
}

export const ENDPOINTS = {
    feed: `${API_V1}/feed`,
    subscriptions: `${API_V1}/subscriptions`,
    subscription: (interestTopicId: string) => `${API_V1}/subscriptions/${segment(interestTopicId)}`,
    auth: {
        login: `${API_V1}/auth/login`,
        register: `${API_V1}/auth/register`,
        logout: `${API_V1}/auth/logout`,
        me: `${API_V1}/auth/me`,
    },
} as const;

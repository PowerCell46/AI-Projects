const API_V1 = '/api/v1';

function segment(value: string): string {
    return encodeURIComponent(value);
}

export const ENDPOINTS = {
    feed: `${API_V1}/feed`,
    categories: `${API_V1}/categories`,
    interestTopics: `${API_V1}/interest-topics`,
    subscriptions: `${API_V1}/subscriptions`,
    subscription: (interestTopicId: string) => `${API_V1}/subscriptions/${segment(interestTopicId)}`,
    auth: {
        login: `${API_V1}/auth/login`,
        register: `${API_V1}/auth/register`,
        me: `${API_V1}/auth/me`,
    },
} as const;

export const MAILPIT_ENDPOINTS = {
    messages: `${API_V1}/messages`,
    info: `${API_V1}/info`,
} as const;

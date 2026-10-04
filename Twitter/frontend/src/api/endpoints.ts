const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? '';

const API_V1 = `${BASE_URL}/api/v1`;

export const ENDPOINTS = {
    feed: `${API_V1}/feed`,
    savedTweets: `${API_V1}/saved-tweets`,
    savedTweet: (tweetId: string) => `${API_V1}/saved-tweets/${tweetId}`,
    like: (tweetId: string) => `${API_V1}/likes/${tweetId}`,
    views: `${API_V1}/views`,
    tweets: `${API_V1}/tweets`,
    tweetImage: (tweetId: string, imageId: string) => `${API_V1}/tweets/${tweetId}/images/${imageId}`,
    users: `${API_V1}/users`,
    user: (username: string) => `${API_V1}/users/${encodeURIComponent(username)}`,
    follow: (username: string) => `${API_V1}/users/${encodeURIComponent(username)}/follow`,
    // Served from the frontend's own public folder, not by the API.
    defaultProfilePicture: '/Default-Profile-Picture.png',
    // The backend hands out paths like /api/v1/files/{id}; the browser needs them on the API's own origin.
    backendPath: (path: string) => `${BASE_URL}${path}`,
    auth: {
        register: `${API_V1}/auth/register`,
        login: `${API_V1}/auth/login`,
        logout: `${API_V1}/auth/logout`,
        me: `${API_V1}/auth/me`,
        confirm: `${API_V1}/auth/confirm`,
        resendConfirmation: `${API_V1}/auth/confirm/resend`,
    },
} as const;

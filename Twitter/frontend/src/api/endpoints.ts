const BASE_URL = import.meta.env.VITE_BASE_API_URL ?? '';

const API_V1 = `${BASE_URL}/api/v1`;

// An id is one path segment, whatever it holds. A bare `..` still climbs out once the browser normalises the URL (also
// as `%2E%2E`), so ids that come from the address bar are checked before they get here (`useOpenTweetId`).
function segment(value: string): string {
    return encodeURIComponent(value);
}

export const ENDPOINTS = {
    feed: `${API_V1}/feed`,
    savedTweets: `${API_V1}/saved-tweets`,
    savedTweet: (tweetId: string) => `${API_V1}/saved-tweets/${segment(tweetId)}`,
    likes: `${API_V1}/likes`,
    like: (tweetId: string) => `${API_V1}/likes/${segment(tweetId)}`,
    views: `${API_V1}/views`,
    tweets: `${API_V1}/tweets`,
    tweetImage: (tweetId: string, imageId: string) => `${API_V1}/tweets/${segment(tweetId)}/images/${segment(imageId)}`,
    replies: (tweetId: string) => `${API_V1}/tweets/${segment(tweetId)}/replies`,
    reply: (tweetId: string, replyId: string) => `${API_V1}/tweets/${segment(tweetId)}/replies/${segment(replyId)}`,
    tweetDetails: (tweetId: string) => `${API_V1}/tweet-details/${segment(tweetId)}`,
    tweetCount: `${API_V1}/tweets/count`,
    authorTweets: (authorId: string) => `${API_V1}/author-tweets/${segment(authorId)}`,
    users: `${API_V1}/users`,
    user: (username: string) => `${API_V1}/users/${segment(username)}`,
    me: `${API_V1}/users/me`,
    profilePicture: `${API_V1}/users/me/profile-picture`,
    follow: (username: string) => `${API_V1}/users/${segment(username)}/follow`,
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

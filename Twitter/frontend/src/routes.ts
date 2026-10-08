export const ROUTES = {
    login: '/login',
    register: '/register',
    confirm: '/confirm',
    resend: '/resend',
    feed: '/feed',
    people: '/users',
    saved: '/saved',
    liked: '/liked',
    tweet: '/tweets/:tweetId',
    profile: '/users/:username',
} as const;

export function tweetPath(tweetId: string): string {
    return `/tweets/${tweetId}`;
}

export function profilePath(username: string): string {
    return `/users/${encodeURIComponent(username)}`;
}

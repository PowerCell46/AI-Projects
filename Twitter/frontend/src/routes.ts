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
} as const;

export function tweetPath(tweetId: string): string {
    return `/tweets/${tweetId}`;
}

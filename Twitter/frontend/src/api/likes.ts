import { ENDPOINTS } from './endpoints';
import { sendAuthenticated } from './http';


export async function likeTweet(tweetId: string): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.like(tweetId),
        { method: 'PUT' },
    );
}

export async function unlikeTweet(tweetId: string): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.like(tweetId),
        { method: 'DELETE' },
    );
}

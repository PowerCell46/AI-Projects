import { ENDPOINTS } from './endpoints';
import { sendAuthenticated } from './http';
import { pageUrl } from './paging';
import type { PageRequest } from './paging';
import { readTweetPage } from './tweetPage';
import type { TweetPage } from './tweetPage';


export async function fetchLikedTweets(request: PageRequest): Promise<TweetPage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.likes, request),
        { method: 'GET' },
    );

    return readTweetPage(response);
}

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

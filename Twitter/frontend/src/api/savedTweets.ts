import { ENDPOINTS } from './endpoints';
import { sendAuthenticated } from './http';
import { pageUrl, readTweetPage } from './tweetPage';
import type { PageRequest, TweetPage } from './tweetPage';


export async function fetchSavedTweets(request: PageRequest): Promise<TweetPage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.savedTweets, request),
        { method: 'GET' },
    );

    return readTweetPage(response);
}

export async function saveTweet(tweetId: string): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.savedTweet(tweetId),
        { method: 'PUT' },
    );
}

export async function unsaveTweet(tweetId: string): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.savedTweet(tweetId),
        { method: 'DELETE' },
    );
}

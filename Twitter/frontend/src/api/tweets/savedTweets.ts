import { ENDPOINTS } from '../core/endpoints';
import { sendAuthenticated } from '../core/http';
import { pageUrl } from '../core/paging';
import type { PageRequest } from '../core/paging';
import { readTweetPage } from './tweetPage';
import type { TweetPage } from './tweetPage';


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

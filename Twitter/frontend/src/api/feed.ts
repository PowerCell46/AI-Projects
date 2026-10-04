import { ENDPOINTS } from './endpoints';
import { sendAuthenticated } from './http';
import { pageUrl, readTweetPage } from './tweetPage';
import type { PageRequest, TweetPage } from './tweetPage';


export async function fetchFeed(request: PageRequest): Promise<TweetPage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.feed, request),
        { method: 'GET' },
    );

    return readTweetPage(response);
}

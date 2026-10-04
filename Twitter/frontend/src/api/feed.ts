import { ENDPOINTS } from './endpoints';
import { sendAuthenticated } from './http';
import { pageUrl } from './paging';
import type { PageRequest } from './paging';
import { readTweetPage } from './tweetPage';
import type { TweetPage } from './tweetPage';


export async function fetchFeed(request: PageRequest): Promise<TweetPage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.feed, request),
        { method: 'GET' },
    );

    return readTweetPage(response);
}

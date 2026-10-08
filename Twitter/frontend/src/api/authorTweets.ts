import { ENDPOINTS } from './endpoints';
import { sendAuthenticated } from './http';
import { pageUrl } from './paging';
import type { PageRequest } from './paging';
import { readTweetPage } from './tweetPage';
import type { TweetPage } from './tweetPage';


export async function fetchAuthorTweets(authorId: string, request: PageRequest): Promise<TweetPage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.authorTweets(authorId), request),
        { method: 'GET' },
    );

    return readTweetPage(response);
}

import { ENDPOINTS } from '../core/endpoints';
import { sendAuthenticated } from '../core/http';
import { pageUrl } from '../core/paging';
import type { PageRequest } from '../core/paging';
import { readTweetPage } from './tweetPage';
import type { TweetPage } from './tweetPage';


export async function fetchAuthorTweets(authorId: string, request: PageRequest): Promise<TweetPage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.authorTweets(authorId), request),
        { method: 'GET' },
    );

    return readTweetPage(response);
}

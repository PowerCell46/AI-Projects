import { ENDPOINTS } from './endpoints';
import { readJson, sendAuthenticated } from './http';
import { withPictureUrl } from './tweetPage';
import type { TweetItem } from './tweetPage';


export async function fetchTweetDetails(tweetId: string): Promise<TweetItem> {
    const response = await sendAuthenticated(
        ENDPOINTS.tweetDetails(tweetId),
        { method: 'GET' },
    );

    return withPictureUrl(await readJson<TweetItem>(response));
}

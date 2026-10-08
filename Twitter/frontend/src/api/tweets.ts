import { ENDPOINTS } from './endpoints';
import { readJson, sendAuthenticated } from './http';
import type { TweetImage } from './tweetPage';


export interface PublishRequest {
    content: string;
    images: File[];
}

export interface TweetCount {
    count: number;
}

export interface PublishedTweet {
    id: string;
    authorId: string;
    content: string;
    createdAt: string;
    updatedAt: string;
    images: TweetImage[];
}

export async function publishTweet(request: PublishRequest): Promise<PublishedTweet> {
    const form = new FormData();

    form.append('content', request.content);
    request.images.forEach((image) => form.append('images', image));

    // No Content-Type header: the browser adds it with the multipart boundary.
    const response = await sendAuthenticated(ENDPOINTS.tweets, {
        method: 'POST',
        body: form,
    });

    return readJson<PublishedTweet>(response);
}

export async function fetchTweetCount(authorId: string): Promise<number> {
    const params = new URLSearchParams({ authorId });
    const response = await sendAuthenticated(
        `${ENDPOINTS.tweetCount}?${params.toString()}`,
        { method: 'GET' },
    );
    const tweetCount = await readJson<TweetCount>(response);

    return tweetCount.count;
}

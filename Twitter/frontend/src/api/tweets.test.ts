import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { fetchTweetCount, publishTweet } from './tweets';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const PUBLISHED = {
    id: 'tweet-1',
    authorId: 'user-1',
    content: 'hello',
    createdAt: '2026-10-04T10:00:00.123Z',
    updatedAt: '2026-10-04T10:00:00.123Z',
    images: [
        {
            id: 'image-1',
            sizeBytes: 3,
            contentType: 'image/png',
        },
    ],
};

installFetchStub();

function sentForm(): FormData {
    const init = fetchMock.mock.calls[0][1];

    if (!(init?.body instanceof FormData)) {
        throw new Error('Expected the request body to be FormData.');
    }

    return init.body;
}

describe('publishTweet', () => {
    it('should_post_the_content_and_each_image_as_multipart_with_credentials', async () => {
        respondWith(201, PUBLISHED);
        const first = new File(
            ['abc'],
            'first.png',
            { type: 'image/png' },
        );
        const second = new File(
            ['def'],
            'second.png',
            { type: 'image/png' },
        );

        await publishTweet({
            content: 'hello',
            images: [first, second],
        });

        expect(fetchMock.mock.calls[0][0]).toBe(ENDPOINTS.tweets);
        expect(fetchMock.mock.calls[0][1]).toMatchObject({
            method: 'POST',
            credentials: 'include',
            signal: expect.any(AbortSignal),
        });
        expect(sentForm().get('content')).toBe('hello');
        expect(sentForm().getAll('images')).toEqual([first, second]);
    });

    it('should_not_set_a_content_type_header_so_the_browser_adds_the_boundary', async () => {
        respondWith(201, PUBLISHED);

        await publishTweet({
            content: 'hello',
            images: [],
        });

        expect(fetchMock.mock.calls[0][1]?.headers).toBeUndefined();
    });

    it('should_send_no_image_parts_when_there_are_no_images', async () => {
        respondWith(201, PUBLISHED);

        await publishTweet({
            content: 'hello',
            images: [],
        });

        expect(sentForm().getAll('images')).toEqual([]);
    });

    it('should_return_the_published_tweet', async () => {
        respondWith(201, PUBLISHED);

        const tweet = await publishTweet({
            content: 'hello',
            images: [],
        });

        expect(tweet).toEqual(PUBLISHED);
    });

    it('should_reject_with_the_server_messages_when_the_tweet_is_refused', async () => {
        respondWith(
            400,
            { messages: ['A tweet can have at most 4 images.'] },
        );

        const publishing = publishTweet({
            content: 'hello',
            images: [],
        });

        await expect(publishing).rejects.toMatchObject({
            status: 400,
            messages: ['A tweet can have at most 4 images.'],
        });
    });
});

describe('fetchTweetCount', () => {
    it('should_get_the_count_of_the_author_with_the_id_as_a_query_parameter', async () => {
        respondWith(200, { count: 25 });

        const count = await fetchTweetCount('user-1');

        expect(count).toBe(25);
        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.tweetCount}?authorId=user-1`,
            {
                method: 'GET',
                credentials: 'include',
                signal: expect.any(AbortSignal),
            },
        );
    });

    it('should_encode_the_author_id_in_the_query', async () => {
        respondWith(200, { count: 0 });

        await fetchTweetCount('a&b=c');

        expect(fetchMock.mock.calls[0][0]).toBe(`${ENDPOINTS.tweetCount}?authorId=a%26b%3Dc`);
    });

    it('should_reject_with_400_when_the_id_is_refused', async () => {
        respondWith(400);

        await expect(fetchTweetCount('nope')).rejects.toMatchObject({ status: 400 });
    });
});

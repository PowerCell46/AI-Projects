import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { fetchSavedTweets, saveTweet, unsaveTweet } from './savedTweets';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

installFetchStub();

describe('fetchSavedTweets', () => {
    it('should_get_the_saved_list_with_the_size_and_the_cursor', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchSavedTweets({
            cursor: 'abc',
            size: 20,
        });

        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.savedTweets}?size=20&cursor=abc`,
            {
                method: 'GET',
                credentials: 'include',
            },
        );
    });

    it('should_return_the_items_and_the_next_cursor_with_the_picture_on_the_api_origin', async () => {
        respondWith(200, {
            items: [{
                id: TWEET_ID,
                views: 0,
                savedByMe: true,
                content: 'hi',
                createdAt: '2026-10-04T10:00:00Z',
                updatedAt: '2026-10-04T10:00:00Z',
                author: {
                    id: 'user-1',
                    username: 'ana',
                    profilePictureUrl: '/api/v1/files/pic-1',
                },
                images: [],
            }],
            nextCursor: 'next',
        });

        const page = await fetchSavedTweets({
            cursor: null,
            size: 20,
        });

        expect(page.nextCursor).toBe('next');
        expect(page.items[0].author.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-1'));
    });
});

describe('saveTweet', () => {
    it('should_put_to_the_saved_tweet_url', async () => {
        respondWith(204);

        await saveTweet(TWEET_ID);

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.savedTweet(TWEET_ID),
            {
                method: 'PUT',
                credentials: 'include',
            },
        );
    });

    it('should_reject_with_the_status_when_the_tweet_is_unknown', async () => {
        respondWith(
            404,
            { messages: ['Tweet not found.'] },
        );

        await expect(saveTweet(TWEET_ID)).rejects.toMatchObject({ status: 404 });
    });
});

describe('unsaveTweet', () => {
    it('should_delete_the_saved_tweet_url', async () => {
        respondWith(204);

        await unsaveTweet(TWEET_ID);

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.savedTweet(TWEET_ID),
            {
                method: 'DELETE',
                credentials: 'include',
            },
        );
    });
});

import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { fetchLikedTweets, likeTweet, unlikeTweet } from './likes';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

installFetchStub();

describe('likeTweet', () => {
    it('should_put_to_the_like_url', async () => {
        respondWith(204);

        await likeTweet(TWEET_ID);

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.like(TWEET_ID),
            {
                method: 'PUT',
                credentials: 'include',
            },
        );
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(500);

        await expect(likeTweet(TWEET_ID)).rejects.toMatchObject({ status: 500 });
    });
});

describe('unlikeTweet', () => {
    it('should_delete_the_like_url', async () => {
        respondWith(204);

        await unlikeTweet(TWEET_ID);

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.like(TWEET_ID),
            {
                method: 'DELETE',
                credentials: 'include',
            },
        );
    });
});
describe('fetchLikedTweets', () => {
    it('should_get_the_liked_list_with_the_cursor_and_size_and_credentials', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchLikedTweets({
            cursor: 'abc',
            size: 20,
        });

        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.likes}?size=20&cursor=abc`,
            {
                method: 'GET',
                credentials: 'include',
            },
        );
    });

    it('should_map_the_author_pictures_as_the_feed_does', async () => {
        respondWith(200, {
            items: [{
                id: TWEET_ID,
                views: 0,
                savedByMe: false,
                likes: 1,
                likedByMe: true,
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

        const page = await fetchLikedTweets({
            cursor: null,
            size: 20,
        });

        expect(page.nextCursor).toBe('next');
        expect(page.items[0].author.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-1'));
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(500);

        await expect(fetchLikedTweets({
            cursor: null,
            size: 20,
        })).rejects.toMatchObject({ status: 500 });
    });
});

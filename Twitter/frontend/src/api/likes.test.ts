import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { likeTweet, unlikeTweet } from './likes';
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

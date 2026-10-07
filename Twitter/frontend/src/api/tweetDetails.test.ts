import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { fetchTweetDetails } from './tweetDetails';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

const ITEM = {
    id: TWEET_ID,
    views: 3,
    savedByMe: false,
    likes: 1,
    likedByMe: true,
    replyCount: 2,
    content: 'hello',
    createdAt: '2026-10-07T10:00:00.123Z',
    updatedAt: '2026-10-07T10:00:00.123Z',
    author: {
        id: 'user-1',
        username: 'ana',
        profilePictureUrl: '/api/v1/files/pic-1',
    },
    images: [],
};

installFetchStub();

describe('fetchTweetDetails', () => {
    it('should_get_the_tweet_details_url_with_credentials', async () => {
        respondWith(200, ITEM);

        await fetchTweetDetails(TWEET_ID);

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.tweetDetails(TWEET_ID),
            {
                method: 'GET',
                credentials: 'include',
            },
        );
    });

    it('should_map_the_author_picture_when_the_tweet_arrives', async () => {
        respondWith(200, ITEM);

        const tweet = await fetchTweetDetails(TWEET_ID);

        expect(tweet.replyCount).toBe(2);
        expect(tweet.author.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-1'));
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(404);

        await expect(fetchTweetDetails(TWEET_ID)).rejects.toMatchObject({ status: 404 });
    });
});

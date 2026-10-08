import { describe, expect, it } from 'vitest';
import { fetchAuthorTweets } from './authorTweets';
import { ENDPOINTS } from './endpoints';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const ITEM = {
    id: 'tweet-1',
    views: 3,
    savedByMe: true,
    likes: 2,
    likedByMe: false,
    replyCount: 1,
    content: 'hello',
    createdAt: '2026-10-04T10:00:00.123Z',
    updatedAt: '2026-10-04T10:00:00.123Z',
    author: {
        id: 'user-1',
        username: 'ana',
        profilePictureUrl: '/api/v1/files/pic-1',
    },
    images: [],
};

installFetchStub();

describe('fetchAuthorTweets', () => {
    it('should_get_the_first_page_of_the_author_with_only_the_size_when_there_is_no_cursor', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchAuthorTweets(
            'user-1',
            {
                cursor: null,
                size: 20,
            },
        );

        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.authorTweets('user-1')}?size=20`,
            {
                method: 'GET',
                credentials: 'include',
            },
        );
    });

    it('should_send_the_cursor_encoded_when_a_cursor_is_given', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchAuthorTweets(
            'user-1',
            {
                cursor: 'a b+c',
                size: 20,
            },
        );

        expect(fetchMock.mock.calls[0][0]).toBe(`${ENDPOINTS.authorTweets('user-1')}?size=20&cursor=a+b%2Bc`);
    });

    it('should_return_the_items_with_the_author_picture_on_the_api_origin', async () => {
        respondWith(
            200,
            {
                items: [ITEM],
                nextCursor: 'next',
            },
        );

        const page = await fetchAuthorTweets(
            'user-1',
            {
                cursor: null,
                size: 20,
            },
        );

        expect(page.nextCursor).toBe('next');
        expect(page.items[0].author.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-1'));
    });

    it('should_reject_with_404_when_the_author_is_unknown', async () => {
        respondWith(
            404,
            { messages: ['Author not found.'] },
        );

        await expect(fetchAuthorTweets(
            'ghost',
            {
                cursor: null,
                size: 20,
            },
        )).rejects.toMatchObject({ status: 404 });
    });
});

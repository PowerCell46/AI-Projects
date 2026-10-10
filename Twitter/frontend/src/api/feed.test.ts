import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { fetchFeed } from './feed';
import { ApiError } from './http';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const ITEM = {
    id: 'tweet-1',
    views: 3,
    savedByMe: true,
    likes: 0,
    likedByMe: false,
    replyCount: 0,
    content: 'hello',
    createdAt: '2026-10-04T10:00:00.123Z',
    updatedAt: '2026-10-04T10:00:00.123Z',
    author: {
        id: 'user-1',
        username: 'ana',
        profilePictureUrl: '/api/v1/files/pic-1',
    },
    images: [
        {
            id: 'image-1',
            sizeBytes: 1234,
            contentType: 'image/png',
        },
    ],
};

installFetchStub();

describe('fetchFeed', () => {
    it('should_get_the_first_page_with_only_the_size_when_there_is_no_cursor', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchFeed({
            cursor: null,
            size: 20,
        });

        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.feed}?size=20`,
            {
                method: 'GET',
                credentials: 'include',
                signal: expect.any(AbortSignal),
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

        await fetchFeed({
            cursor: 'a+b/c=',
            size: 5,
        });

        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.feed}?size=5&cursor=a%2Bb%2Fc%3D`,
            expect.anything(),
        );
    });

    it('should_return_the_items_and_the_next_cursor_when_the_page_has_more', async () => {
        respondWith(
            200,
            {
                items: [ITEM],
                nextCursor: 'next',
            },
        );

        const page = await fetchFeed({
            cursor: null,
            size: 20,
        });

        expect(page.nextCursor).toBe('next');
        expect(page.items).toHaveLength(1);
        expect(page.items[0]).toMatchObject({
            id: 'tweet-1',
            savedByMe: true,
            likes: 0,
            likedByMe: false,
            views: 3,
        });
    });

    it('should_put_the_api_origin_before_the_picture_path_when_the_author_has_a_picture', async () => {
        respondWith(
            200,
            {
                items: [ITEM],
                nextCursor: null,
            },
        );

        const page = await fetchFeed({
            cursor: null,
            size: 20,
        });

        expect(page.items[0].author.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-1'));
    });

    it('should_keep_a_null_picture_url_when_the_author_has_no_picture', async () => {
        const tweet = {
            ...ITEM,
            author: {
                ...ITEM.author,
                profilePictureUrl: null,
            },
        };
        respondWith(
            200,
            {
                items: [tweet],
                nextCursor: null,
            },
        );

        const page = await fetchFeed({
            cursor: null,
            size: 20,
        });

        expect(page.items[0].author.profilePictureUrl).toBeNull();
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(
            502,
            { messages: ['Upstream service unavailable.'] },
        );

        const fetching = fetchFeed({
            cursor: null,
            size: 20,
        });

        await expect(fetching).rejects.toMatchObject({
            status: 502,
            messages: ['Upstream service unavailable.'],
        });
    });

    it('should_reject_with_an_api_error_when_the_network_fails', async () => {
        fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

        const fetching = fetchFeed({
            cursor: null,
            size: 20,
        });

        await expect(fetching).rejects.toBeInstanceOf(ApiError);
    });
});

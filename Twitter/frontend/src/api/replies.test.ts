import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { createReply, deleteReply, fetchReplies, updateReply } from './replies';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const TWEET_ID = '6f1c2a3e-0000-4000-8000-000000000001';

const REPLY_ID = '6f1c2a3e-0000-4000-8000-000000000002';

const REPLY = {
    id: REPLY_ID,
    tweetId: TWEET_ID,
    content: 'hello',
    edited: false,
    createdAt: '2026-10-07T10:00:00.123Z',
    updatedAt: '2026-10-07T10:00:00.123Z',
    author: {
        id: 'user-1',
        username: 'ana',
        profilePictureUrl: '/api/v1/files/pic-1',
    },
};

const JSON_HEADERS = {
    'Content-Type': 'application/json',
};

installFetchStub();

describe('fetchReplies', () => {
    it('should_get_the_replies_url_with_the_size_cursor_and_credentials', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchReplies(
            TWEET_ID,
            {
                cursor: 'abc',
                size: 20,
            },
        );

        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.replies(TWEET_ID)}?size=20&cursor=abc`,
            {
                method: 'GET',
                credentials: 'include',
                signal: expect.any(AbortSignal),
            },
        );
    });

    it('should_map_the_author_picture_when_a_page_arrives', async () => {
        respondWith(
            200,
            {
                items: [REPLY],
                nextCursor: 'next',
            },
        );

        const page = await fetchReplies(
            TWEET_ID,
            {
                cursor: null,
                size: 20,
            },
        );

        expect(page.nextCursor).toBe('next');
        expect(page.items[0].author.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-1'));
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(404);

        await expect(fetchReplies(
            TWEET_ID,
            {
                cursor: null,
                size: 20,
            },
        )).rejects.toMatchObject({ status: 404 });
    });
});

describe('createReply', () => {
    it('should_post_the_content_as_json_with_credentials', async () => {
        respondWith(201, REPLY);

        await createReply(TWEET_ID, 'hello');

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.replies(TWEET_ID),
            {
                method: 'POST',
                headers: JSON_HEADERS,
                body: JSON.stringify({ content: 'hello' }),
                credentials: 'include',
                signal: expect.any(AbortSignal),
            },
        );
    });

    it('should_map_the_author_picture_when_the_reply_is_created', async () => {
        respondWith(201, REPLY);

        const reply = await createReply(TWEET_ID, 'hello');

        expect(reply.author.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-1'));
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(503);

        await expect(createReply(TWEET_ID, 'hello')).rejects.toMatchObject({ status: 503 });
    });
});

describe('updateReply', () => {
    it('should_put_the_content_to_the_reply_url_with_credentials', async () => {
        respondWith(
            200,
            {
                ...REPLY,
                edited: true,
            },
        );

        const reply = await updateReply(TWEET_ID, REPLY_ID, 'changed');

        expect(reply.edited).toBe(true);
        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.reply(TWEET_ID, REPLY_ID),
            {
                method: 'PUT',
                headers: JSON_HEADERS,
                body: JSON.stringify({ content: 'changed' }),
                credentials: 'include',
                signal: expect.any(AbortSignal),
            },
        );
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(404);

        await expect(updateReply(TWEET_ID, REPLY_ID, 'changed')).rejects.toMatchObject({ status: 404 });
    });
});

describe('deleteReply', () => {
    it('should_delete_the_reply_url_with_credentials', async () => {
        respondWith(204);

        await deleteReply(TWEET_ID, REPLY_ID);

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.reply(TWEET_ID, REPLY_ID),
            {
                method: 'DELETE',
                credentials: 'include',
                signal: expect.any(AbortSignal),
            },
        );
    });

    it('should_reject_with_the_status_when_the_server_answers_an_error', async () => {
        respondWith(404);

        await expect(deleteReply(TWEET_ID, REPLY_ID)).rejects.toMatchObject({ status: 404 });
    });
});

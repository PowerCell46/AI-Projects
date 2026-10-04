import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { reportViews } from './views';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


installFetchStub();

describe('reportViews', () => {
    it('should_post_the_tweet_ids_as_json', async () => {
        respondWith(204);

        await reportViews(['tweet-1', 'tweet-2']);

        expect(fetchMock).toHaveBeenCalledWith(ENDPOINTS.views, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ tweetIds: ['tweet-1', 'tweet-2'] }),
            keepalive: false,
            credentials: 'include',
        });
    });

    it('should_ask_the_browser_to_keep_the_request_alive_when_the_page_is_closing', async () => {
        respondWith(204);

        await reportViews(
            ['tweet-1'],
            { isKeepalive: true },
        );

        expect(fetchMock).toHaveBeenCalledWith(ENDPOINTS.views, expect.objectContaining({ keepalive: true }));
    });

    it('should_reject_with_the_status_when_the_batch_is_rejected', async () => {
        respondWith(
            400,
            { messages: ['tweetIds must hold 1 to 50 ids.'] },
        );

        await expect(reportViews([])).rejects.toMatchObject({ status: 400 });
    });
});

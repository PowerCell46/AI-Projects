import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, jsonRequest, send, sendAuthenticated, setUnauthorizedHandler } from './http';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


const REQUEST_URL = 'http://localhost/api/v1/anything';

installFetchStub();

afterEach(() => {
    setUnauthorizedHandler(null);
    vi.restoreAllMocks();
});

async function catchApiError(call: () => Promise<unknown>): Promise<ApiError> {
    try {
        await call();

    } catch (error) {
        if (error instanceof ApiError) {
            return error;
        }
    }

    throw new Error('Expected the call to reject with an ApiError.');
}

describe('send', () => {
    it('should_send_the_request_with_credentials_and_return_the_response_when_it_succeeds', async () => {
        respondWith(204);

        const response = await send(
            REQUEST_URL,
            { method: 'PUT' },
        );

        expect(response.status).toBe(204);
        expect(fetchMock).toHaveBeenCalledWith(
            REQUEST_URL,
            {
                method: 'PUT',
                credentials: 'include',
                signal: expect.any(AbortSignal),
            },
        );
    });

    it('should_reject_with_the_status_and_the_server_messages_when_the_response_is_an_error', async () => {
        respondWith(
            400,
            { messages: ['Page size must be between 1 and 100.', 7] },
        );

        const error = await catchApiError(() => send(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(error.status).toBe(400);
        expect(error.messages).toEqual(['Page size must be between 1 and 100.']);
    });

    it('should_reject_with_no_messages_when_the_error_body_is_not_json', async () => {
        fetchMock.mockResolvedValueOnce(new Response(
            '<html>',
            { status: 502 },
        ));

        const error = await catchApiError(() => send(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(error.status).toBe(502);
        expect(error.messages).toEqual([]);
    });

    it('should_reject_with_status_0_when_the_network_fails', async () => {
        fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

        const error = await catchApiError(() => send(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(error.status).toBe(0);
        expect(error.messages).toEqual([]);
    });

    it('should_give_a_normal_request_30_seconds_before_giving_up', async () => {
        const timeoutSpy = vi.spyOn(AbortSignal, 'timeout');
        respondWith(204);

        await send(
            REQUEST_URL,
            { method: 'GET' },
        );

        expect(timeoutSpy).toHaveBeenCalledWith(30_000);
    });

    it('should_give_an_upload_120_seconds_before_giving_up', async () => {
        const timeoutSpy = vi.spyOn(AbortSignal, 'timeout');
        respondWith(204);

        await send(
            REQUEST_URL,
            {
                method: 'POST',
                body: new FormData(),
            },
        );

        expect(timeoutSpy).toHaveBeenCalledWith(120_000);
    });

    it('should_reject_with_status_0_when_the_request_times_out', async () => {
        fetchMock.mockRejectedValueOnce(new DOMException('The operation timed out.', 'TimeoutError'));

        const error = await catchApiError(() => send(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(error.status).toBe(0);
    });

    it('should_not_call_the_unauthorized_handler_when_the_response_is_401', async () => {
        const handler = vi.fn();
        setUnauthorizedHandler(handler);
        respondWith(401);

        await catchApiError(() => send(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(handler).not.toHaveBeenCalled();
    });
});

describe('sendAuthenticated', () => {
    it('should_call_the_unauthorized_handler_once_and_still_reject_when_the_response_is_401', async () => {
        const handler = vi.fn();
        setUnauthorizedHandler(handler);
        respondWith(
            401,
            { messages: ['Unauthorized.'] },
        );

        const error = await catchApiError(() => sendAuthenticated(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(error.status).toBe(401);
        expect(handler).toHaveBeenCalledTimes(1);
    });

    it.each([403, 404, 500])('should_not_call_the_unauthorized_handler_when_the_response_is_%i', async (status) => {
        const handler = vi.fn();
        setUnauthorizedHandler(handler);
        respondWith(status);

        await catchApiError(() => sendAuthenticated(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(handler).not.toHaveBeenCalled();
    });

    it('should_not_call_the_unauthorized_handler_when_the_network_fails', async () => {
        const handler = vi.fn();
        setUnauthorizedHandler(handler);
        fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

        await catchApiError(() => sendAuthenticated(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(handler).not.toHaveBeenCalled();
    });

    it('should_reject_with_the_401_when_no_handler_is_registered', async () => {
        respondWith(401);

        const error = await catchApiError(() => sendAuthenticated(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(error.status).toBe(401);
    });

    it('should_not_call_a_handler_that_was_unregistered_when_the_response_is_401', async () => {
        const handler = vi.fn();
        setUnauthorizedHandler(handler);
        setUnauthorizedHandler(null);
        respondWith(401);

        await catchApiError(() => sendAuthenticated(
            REQUEST_URL,
            { method: 'GET' },
        ));

        expect(handler).not.toHaveBeenCalled();
    });

    it('should_return_the_response_without_calling_the_handler_when_it_succeeds', async () => {
        const handler = vi.fn();
        setUnauthorizedHandler(handler);
        respondWith(200);

        const response = await sendAuthenticated(
            REQUEST_URL,
            { method: 'GET' },
        );

        expect(response.status).toBe(200);
        expect(handler).not.toHaveBeenCalled();
    });
});

describe('jsonRequest', () => {
    it('should_build_a_request_with_the_json_header_and_the_serialised_body', () => {
        const request = jsonRequest('POST', { tweetIds: ['a'] });

        expect(request).toEqual({
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: '{"tweetIds":["a"]}',
        });
    });
});

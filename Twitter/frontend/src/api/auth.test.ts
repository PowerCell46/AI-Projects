import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { confirm, login, logout, me, register, resendConfirmation } from './auth';
import { ApiError } from './http';
import { ENDPOINTS } from './endpoints';


const USER = {
    id: '6f1c2a3e-0000-4000-8000-000000000001',
    username: 'peter_g',
    email: 'peter@example.com',
};

const fetchMock = vi.fn<typeof fetch>();

function respondWith(status: number, body?: object): void {
    fetchMock.mockResolvedValueOnce(new Response(
        body ? JSON.stringify(body) : null,
        { status },
    ));
}

async function catchAuthError(call: () => Promise<unknown>): Promise<ApiError> {
    try {
        await call();

    } catch (error) {
        if (error instanceof ApiError) {
            return error;
        }
    }

    throw new Error('Expected the call to reject with an ApiError.');
}

beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
});

describe('successful calls', () => {
    it('should_post_the_registration_and_return_the_user', async () => {
        respondWith(201, USER);
        const request = {
            email: USER.email,
            username: USER.username,
            password: 'Abcdefg1',
        };

        const user = await register(request);

        expect(user).toEqual(USER);
        expect(fetchMock).toHaveBeenCalledWith(ENDPOINTS.auth.register, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request),
            credentials: 'include',
        });
    });

    it('should_post_the_identifier_and_password_and_return_the_user_when_logging_in', async () => {
        respondWith(200, USER);

        const user = await login({
            identifier: 'peter_g',
            password: 'Abcdefg1',
        });

        expect(user).toEqual(USER);
        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.auth.login,
            expect.objectContaining({
                body: JSON.stringify({
                    identifier: 'peter_g',
                    password: 'Abcdefg1',
                }),
                credentials: 'include',
            }),
        );
    });

    it('should_post_to_logout_with_credentials', async () => {
        respondWith(204);

        await logout();

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.auth.logout,
            {
                method: 'POST',
                credentials: 'include',
            },
        );
    });

    it('should_get_the_current_user', async () => {
        respondWith(200, USER);

        const user = await me();

        expect(user).toEqual(USER);
        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.auth.me,
            {
                method: 'GET',
                credentials: 'include',
            },
        );
    });

    it('should_post_the_token_in_a_json_body_when_confirming', async () => {
        respondWith(204);

        await confirm('token-value');

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.auth.confirm,
            expect.objectContaining({ body: JSON.stringify({ token: 'token-value' }) }),
        );
    });

    it('should_post_the_email_in_a_json_body_when_resending', async () => {
        respondWith(202);

        await resendConfirmation('peter@example.com');

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.auth.resendConfirmation,
            expect.objectContaining({ body: JSON.stringify({ email: 'peter@example.com' }) }),
        );
    });
});

describe('failed calls', () => {
    it('should_carry_the_status_and_the_gateway_messages_when_the_response_is_an_error', async () => {
        respondWith(409, {
            status: 409,
            messages: ['Email already registered.'],
            timestamp: 1,
        });

        const error = await catchAuthError(() => login({
            identifier: 'x',
            password: 'y',
        }));

        expect(error.status).toBe(409);
        expect(error.messages).toEqual(['Email already registered.']);
    });

    it('should_carry_an_empty_message_list_when_the_error_body_is_not_json', async () => {
        fetchMock.mockResolvedValueOnce(new Response(
            '<html>Bad Gateway</html>',
            { status: 502 },
        ));

        const error = await catchAuthError(me);

        expect(error.status).toBe(502);
        expect(error.messages).toEqual([]);
    });

    it('should_ignore_messages_that_are_not_strings', async () => {
        respondWith(
            400,
            { messages: ['username must be valid', 7, null] },
        );

        const error = await catchAuthError(me);

        expect(error.messages).toEqual(['username must be valid']);
    });

    it('should_use_status_zero_when_the_request_never_reaches_the_server', async () => {
        fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

        const error = await catchAuthError(me);

        expect(error.status).toBe(0);
        expect(error.messages).toEqual([]);
    });
});

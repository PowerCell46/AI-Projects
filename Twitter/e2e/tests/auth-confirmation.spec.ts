import { expect, type APIResponse } from '@playwright/test';
import {
    API_PATHS,
    HTTP_STATUS,
    latestConfirmationUrl,
    newUser,
    registerViaApi,
    test,
    TEST_TIMEOUT_MS,
} from './fixtures';
import { expireConfirmationLinks } from './database-helpers';

const EXPIRED_MESSAGE = 'Invalid or expired confirmation token.';

const EMAILS_AFTER_ONE_RESEND = 2;

interface ErrorBody {
    status: number;
    messages: string[];
}

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function messagesOf(response: APIResponse): Promise<string[]> {
    const errorBody: ErrorBody = await response.json();

    return errorBody.messages;
}

function tokenIn(confirmationUrl: string): string | null {
    return new URL(confirmationUrl).searchParams.get('token');
}

test(
    'should_refuse_an_expired_link_with_400_and_accept_the_link_of_a_resend_when_the_first_one_has_expired',
    async ({ request }) => {
        const user = newUser();
        await registerViaApi(request, user);
        const expiredToken = tokenIn(await latestConfirmationUrl(user.email));
        expireConfirmationLinks(user.email);

        const refused = await request.post(
            API_PATHS.confirm,
            { data: { token: expiredToken } },
        );

        expect(refused.status()).toBe(HTTP_STATUS.badRequest);

        expect(await messagesOf(refused)).toEqual([EXPIRED_MESSAGE]);

        const stillUnconfirmed = await request.post(
            API_PATHS.login,
            {
                data: {
                    identifier: user.username,
                    password: user.password,
                },
            },
        );

        expect(stillUnconfirmed.status()).toBe(HTTP_STATUS.forbidden);

        const resent = await request.post(
            API_PATHS.resendConfirmation,
            { data: { email: user.email } },
        );

        expect(resent.status()).toBe(HTTP_STATUS.accepted);

        const freshToken = tokenIn(await latestConfirmationUrl(user.email, EMAILS_AFTER_ONE_RESEND));
        const accepted = await request.post(
            API_PATHS.confirm,
            { data: { token: freshToken } },
        );

        expect(accepted.status()).toBe(HTTP_STATUS.noContent);
    },
);

test(
    'should_refuse_a_token_of_the_right_shape_that_was_never_issued_with_400',
    async ({ request }) => {
        const response = await request.post(
            API_PATHS.confirm,
            { data: { token: 'a'.repeat(43) } },
        );

        expect(response.status()).toBe(HTTP_STATUS.badRequest);
        expect(await messagesOf(response)).toEqual([EXPIRED_MESSAGE]);
    },
);

test(
    'should_refuse_a_token_of_the_wrong_shape_with_400',
    async ({ request }) => {
        const response = await request.post(
            API_PATHS.confirm,
            { data: { token: 'too-short' } },
        );

        expect(response.status()).toBe(HTTP_STATUS.badRequest);
        expect(await messagesOf(response)).toEqual(['token must be a 43-character confirmation token']);
    },
);

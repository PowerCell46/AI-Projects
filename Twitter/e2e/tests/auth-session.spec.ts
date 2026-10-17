import { expect, type APIResponse } from '@playwright/test';
import { API_PATHS, HTTP_STATUS, test, TEST_TIMEOUT_MS } from './fixtures';
import { claimsOf, cookieHeader, E2E_JWT_SECRET, expiredTokenOf, HOUR_IN_SECONDS, signAccessToken } from './session-helpers';

const AUTHENTICATION_REQUIRED_BODY = {
    status: HTTP_STATUS.unauthorized,
    messages: ['Authentication is required.'],
};

const CHALLENGE_HEADER = 'www-authenticate';

const OTHER_SECRET = 'a-different-secret-that-is-also-long-enough-for-hs256-0123456789';

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

// A bad token gets the same answer as a request with no cookie: the app's JSON 401 and no WWW-Authenticate challenge.
async function expectRejectedToken(response: APIResponse) {
    expect(response.status()).toBe(HTTP_STATUS.unauthorized);
    expect(await response.json()).toMatchObject(AUTHENTICATION_REQUIRED_BODY);
    expect(response.headers()[CHALLENGE_HEADER]).toBeUndefined();
}

test.describe(
    'session cookie',
    () => {
        test(
            'should_answer_200_when_a_hand_signed_token_has_not_expired',
            async ({ createAccount, request }) => {
                const ana = await createAccount();
                const claims = await claimsOf(ana);
                const token = signAccessToken(claims, E2E_JWT_SECRET, HOUR_IN_SECONDS);

                const response = await request.get(
                    API_PATHS.me,
                    { headers: cookieHeader(token) },
                );

                expect(response.status()).toBe(HTTP_STATUS.ok);
                expect(await response.json()).toMatchObject({ username: ana.user.username });
            },
        );

        test(
            'should_answer_401_on_me_and_on_a_proxied_route_when_the_token_has_expired',
            async ({ createAccount, request }) => {
                const ana = await createAccount();
                const expiredToken = await expiredTokenOf(ana);

                await expectRejectedToken(await request.get(
                    API_PATHS.me,
                    { headers: cookieHeader(expiredToken) },
                ));
                await expectRejectedToken(await request.get(
                    API_PATHS.feed,
                    { headers: cookieHeader(expiredToken) },
                ));
            },
        );

        test(
            'should_answer_401_when_the_token_is_signed_with_another_secret',
            async ({ createAccount, request }) => {
                const ana = await createAccount();
                const claims = await claimsOf(ana);
                const forgedToken = signAccessToken(claims, OTHER_SECRET, HOUR_IN_SECONDS);

                const response = await request.get(
                    API_PATHS.me,
                    { headers: cookieHeader(forgedToken) },
                );

                await expectRejectedToken(response);
            },
        );

        test(
            'should_answer_401_when_the_cookie_is_not_a_token',
            async ({ request }) => {
                const response = await request.get(
                    API_PATHS.me,
                    { headers: cookieHeader('not-a-token') },
                );

                await expectRejectedToken(response);
            },
        );

        test(
            'should_answer_401_when_the_token_subject_is_not_a_user_id',
            async ({ createAccount, request }) => {
                const ana = await createAccount();
                const claims = await claimsOf(ana);
                const token = signAccessToken(
                    {
                        ...claims,
                        userId: 'not-a-uuid',
                    },
                    E2E_JWT_SECRET,
                    HOUR_IN_SECONDS,
                );

                const response = await request.get(
                    API_PATHS.me,
                    { headers: cookieHeader(token) },
                );

                await expectRejectedToken(response);
            },
        );

        test(
            'should_answer_401_when_a_write_is_sent_with_an_expired_token',
            async ({ createAccount, request }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const expiredToken = await expiredTokenOf(ana);

                const response = await request.put(
                    API_PATHS.follow(bob.user.username),
                    { headers: cookieHeader(expiredToken) },
                );

                await expectRejectedToken(response);
            },
        );
    },
);

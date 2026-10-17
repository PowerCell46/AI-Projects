import { expect, type Page } from '@playwright/test';
import { APP_PATHS, expectFeedToContain, openAs, postTweet, test, TEST_TIMEOUT_MS, type Account } from './fixtures';
import { postWithText } from './page-helpers';
import { ACCESS_TOKEN_COOKIE, expiredTokenOf } from './session-helpers';

const LOGIN_URL_PATTERN = /\/login$/;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function replaceCookieWithExpiredToken(page: Page, account: Account, baseURL: string | undefined) {
    await page
        .context()
        .addCookies([{
            name: ACCESS_TOKEN_COOKIE,
            value: await expiredTokenOf(account),
            url: baseURL,
        }]);
}

test('should_send_a_visitor_with_an_expired_cookie_from_the_feed_to_login', async ({ page, baseURL, createAccount }) => {
    const ana = await createAccount();
    await replaceCookieWithExpiredToken(page, ana, baseURL);

    await page.goto(APP_PATHS.feed);

    await expect(page).toHaveURL(LOGIN_URL_PATTERN);
});

test('should_land_on_the_login_page_when_the_session_expires_and_an_action_runs', async ({ page, baseURL, createAccount }) => {
    const ana = await createAccount();
    const content = `still signed in from ${ana.user.username}`;
    await expectFeedToContain(ana, await postTweet(ana, content));
    await openAs(page, ana, APP_PATHS.feed);
    const like = postWithText(page, content).getByRole('button', { name: /^Like/ });
    await expect(like).toBeVisible();

    await replaceCookieWithExpiredToken(page, ana, baseURL);
    await like.click();

    await expect(page).toHaveURL(LOGIN_URL_PATTERN);
});

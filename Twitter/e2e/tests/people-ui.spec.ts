import { expect, type Locator, type Page, type Response } from '@playwright/test';
import {
    expectFeedToContain,
    FEED_POLL_TIMEOUT_MS,
    feedTweetIds,
    follow,
    MAX_PAGE_SIZE,
    openAs,
    postTweet,
    test,
} from './fixtures';


// Registrations each wait for a confirmation email, and a follow crosses the outbox and Kafka before the feed fills.
const TEST_TIMEOUT_MS = 120_000;

// How long one check that a card is on the page may take before the list is scrolled further.
const PERSON_CHECK_MS = 1_000;

const BULK_POST_COUNT = 25;

const SCROLL_DOWN_PX = 1_500;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

function tab(page: Page, name: 'TWEETS' | 'PEOPLE'): Locator {
    return page.getByRole('tab', { name });
}

function waitForCall(page: Page, method: string, urlPart: string): Promise<Response> {
    return page.waitForResponse(
        (response) => response.request().method() === method && response.url().includes(urlPart),
    );
}

function postWithText(page: Page, text: string): Locator {
    return page
        .getByRole('article')
        .filter({ hasText: text });
}

function personCard(page: Page, username: string): Locator {
    return page
        .getByRole('listitem')
        .filter({ has: page.getByText(username, { exact: true }) });
}

// The database is shared with the specs running in parallel, so the card can sit below the first page.
async function findPersonCard(page: Page, username: string): Promise<Locator> {
    const card = personCard(page, username);

    await expect(async () => {
        await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
        await expect(card).toBeVisible({ timeout: PERSON_CHECK_MS });
    }).toPass({ timeout: FEED_POLL_TIMEOUT_MS });

    return card;
}

function followButton(card: Locator, username: string): Locator {
    return card.getByRole('button', { name: `Follow ${username}` });
}

function followerCount(card: Locator): Locator {
    return card.getByText(/FOLLOWERS?$/);
}

function currentScrollY(page: Page): Promise<number> {
    return page.evaluate(() => window.scrollY);
}

test('should_offer_find_people_on_an_empty_feed_and_open_the_people_tab_when_it_is_pressed', async ({ page, createAccount }) => {
    const ana = await createAccount();
    await openAs(page, ana, '/feed');
    await expect(page.getByText('NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS')).toBeVisible();

    await page
        .getByRole('button', { name: 'FIND PEOPLE' })
        .click();

    await expect(page).toHaveURL(/\/users$/);
    await expect(tab(page, 'PEOPLE')).toHaveAttribute('aria-selected', 'true');
});

test('should_show_following_and_one_more_follower_and_keep_them_after_a_reload_when_follow_is_pressed', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await openAs(page, ana, '/users');
    const card = await findPersonCard(page, bob.user.username);
    const button = followButton(card, bob.user.username);
    await expect(button).toHaveText('FOLLOW');
    await expect(followerCount(card)).toHaveText('0 FOLLOWERS');
    const followRequest = waitForCall(page, 'PUT', `/users/${bob.user.username}/follow`);

    await button.click();

    await expect(button).toHaveText('FOLLOWING');
    await expect(followerCount(card)).toHaveText('1 FOLLOWER');
    expect((await followRequest).status()).toBe(204);

    await page.reload();

    const reloadedCard = await findPersonCard(page, bob.user.username);
    await expect(followButton(reloadedCard, bob.user.username)).toHaveText('FOLLOWING');
    await expect(followerCount(reloadedCard)).toHaveText('1 FOLLOWER');
});

test('should_show_the_post_of_the_person_just_followed_when_the_user_goes_back_to_an_empty_tweets_tab', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const content = `a post by ${bob.user.username}`;
    const tweetId = await postTweet(bob, content);
    await openAs(page, ana, '/feed');
    await page
        .getByRole('button', { name: 'FIND PEOPLE' })
        .click();
    const card = await findPersonCard(page, bob.user.username);

    await followButton(card, bob.user.username).click();
    await expectFeedToContain(ana, tweetId);
    await tab(page, 'TWEETS').click();

    await expect(postWithText(page, content)).toBeVisible();
});

test('should_ask_twice_before_unfollowing_and_keep_the_unfollow_after_a_reload', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await follow(ana, bob);
    await openAs(page, ana, '/users');
    const card = await findPersonCard(page, bob.user.username);
    const button = followButton(card, bob.user.username);
    await expect(button).toHaveText('FOLLOWING');
    await expect(followerCount(card)).toHaveText('1 FOLLOWER');

    await button.click();

    await expect(button).toHaveText('UNFOLLOW?');

    const unfollowRequest = waitForCall(page, 'DELETE', `/users/${bob.user.username}/follow`);

    await button.click();

    await expect(button).toHaveText('FOLLOW');
    await expect(followerCount(card)).toHaveText('0 FOLLOWERS');
    expect((await unfollowRequest).status()).toBe(204);

    await page.reload();

    const reloadedCard = await findPersonCard(page, bob.user.username);
    await expect(followButton(reloadedCard, bob.user.username)).toHaveText('FOLLOW');
});

test('should_show_try_again_and_then_follow_when_the_follow_request_fails', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await page.route(
        `**/api/v1/users/${bob.user.username}/follow`,
        (route) => route.fulfill({ status: 500 }),
    );
    await openAs(page, ana, '/users');
    const card = await findPersonCard(page, bob.user.username);
    const button = followButton(card, bob.user.username);

    await button.click();

    await expect(button).toHaveText('TRY AGAIN');
    await expect(followerCount(card)).toHaveText('0 FOLLOWERS');
    await expect(button).toHaveText('FOLLOW');
});

test('should_switch_tabs_with_the_arrow_keys_go_back_with_the_browser_and_stay_on_people_after_a_reload', async ({ page, createAccount }) => {
    const ana = await createAccount();
    await openAs(page, ana, '/feed');
    await tab(page, 'TWEETS').focus();

    await page.keyboard.press('ArrowRight');

    await expect(tab(page, 'PEOPLE')).toHaveAttribute('aria-selected', 'true');
    await expect(page).toHaveURL(/\/users$/);

    await page.keyboard.press('ArrowRight');

    await expect(tab(page, 'TWEETS')).toHaveAttribute('aria-selected', 'true');
    await expect(page).toHaveURL(/\/feed$/);

    await tab(page, 'PEOPLE').click();
    await expect(page).toHaveURL(/\/users$/);

    await page.goBack();

    await expect(page).toHaveURL(/\/feed$/);
    await expect(tab(page, 'TWEETS')).toHaveAttribute('aria-selected', 'true');

    await tab(page, 'PEOPLE').click();
    await page.reload();

    await expect(page).toHaveURL(/\/users$/);
    await expect(tab(page, 'PEOPLE')).toHaveAttribute('aria-selected', 'true');
});

test('should_bring_the_tweets_tab_back_at_the_same_scroll_position_after_a_visit_to_people', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();

    for (let index = 1; index <= BULK_POST_COUNT; index++) {
        await postTweet(bob, `bulk post ${index} by ${bob.user.username}`);
    }

    await follow(ana, bob);
    await expect
        .poll(
            async () => (await feedTweetIds(ana, MAX_PAGE_SIZE)).length,
            { timeout: FEED_POLL_TIMEOUT_MS },
        )
        .toBe(BULK_POST_COUNT);
    await openAs(page, ana, '/feed');
    await expect(page.getByRole('article').first()).toBeVisible();
    await page.evaluate((top) => window.scrollTo(0, top), SCROLL_DOWN_PX);
    const scrollBefore = await currentScrollY(page);
    expect(scrollBefore).toBeGreaterThan(0);

    await tab(page, 'PEOPLE').click();
    await tab(page, 'TWEETS').click();

    await expect
        .poll(() => currentScrollY(page))
        .toBe(scrollBefore);
});

test('should_show_no_tab_row_on_the_saved_page', async ({ page, createAccount }) => {
    const ana = await createAccount();

    await openAs(page, ana, '/saved');

    await expect(page.getByRole('heading', { name: 'SAVED TWEETS' })).toBeVisible();
    await expect(page.getByRole('tablist')).toHaveCount(0);
});

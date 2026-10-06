import { expect, type Locator, type Page, type Response } from '@playwright/test';
import {
    expectFeedToContain,
    FEED_POLL_TIMEOUT_MS,
    feedTweetIds,
    FIRST_PAGE_SIZE,
    follow,
    MAX_PAGE_SIZE,
    openAs,
    postTweet,
    test,
    TINY_PNG,
    type Account,
} from './fixtures';


// Registrations each wait for a confirmation email, and every feed change crosses Kafka and the outbox.
const TEST_TIMEOUT_MS = 120_000;

// How long one check that the end of the feed is visible may take before the scroll is retried.
const END_OF_FEED_CHECK_MS = 1_000;

const BULK_POST_COUNT = 25;

const NEW_POSTS_CHECK_MS = 60_000;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

function postWithText(page: Page, text: string): Locator {
    return page
        .getByRole('article')
        .filter({ hasText: text });
}

function waitForCall(page: Page, method: string, urlPart: string): Promise<Response> {
    return page.waitForResponse(
        (response) => response.request().method() === method && response.url().includes(urlPart),
    );
}

function likeButton(post: Locator): Locator {
    return post.getByRole('button', { name: /^Like/ });
}

function saveButton(post: Locator): Locator {
    return post.getByRole('button', { name: /^Save/ });
}

async function openAccountMenu(page: Page) {
    await page
        .getByRole('button', { name: 'Account menu' })
        .click();
}

async function expectImageToLoad(image: Locator) {
    await expect
        .poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth))
        .toBeGreaterThan(0);
}

async function saveViaApi(reader: Account, tweetId: string) {
    const response = await reader.api.put(`/api/v1/saved-tweets/${tweetId}`);

    expect(response.status()).toBe(204);
}

async function likeViaApi(liker: Account, tweetId: string) {
    const response = await liker.api.put(`/api/v1/likes/${tweetId}`);

    expect(response.status()).toBe(204);
}

async function readViews(reader: Account, tweetId: string): Promise<number> {
    const response = await reader.api.get(`/api/v1/views?tweetIds=${tweetId}`);

    expect(response.ok()).toBe(true);

    const counts: Record<string, number> = await response.json();

    return counts[tweetId];
}

test('should_show_a_published_post_with_its_image_at_the_top_of_the_feed_at_once', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `fresh from ${ana.user.username}`;
    await openAs(page, ana, '/feed');
    await expect(page.getByText('NOTHING HERE YET')).toBeVisible();

    await page
        .getByRole('button', { name: 'Post' })
        .click();
    await page
        .getByRole('textbox', { name: "What's worth sending up?" })
        .fill(content);
    await page
        .getByLabel('ATTACH IMAGE')
        .setInputFiles(TINY_PNG);
    await page
        .getByRole('button', { name: /^PUBLISH/ })
        .click();

    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(page.getByRole('article').first()).toContainText(content);
    await expectImageToLoad(postWithText(page, content).getByRole('img', { name: 'Image 1 of 1' }));
    await expect(page.getByRole('button', { name: 'Post' })).toBeFocused();
});

test('should_show_the_post_of_a_followed_user_with_its_image_in_the_feed', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await follow(ana, bob);
    const content = `hello from ${bob.user.username}`;
    const tweetId = await postTweet(bob, content, TINY_PNG);
    await expectFeedToContain(ana, tweetId);

    await openAs(page, ana, '/feed');

    const post = postWithText(page, content);
    await expect(post).toBeVisible();
    await expect(post).toContainText(bob.user.username);
    await expectImageToLoad(post.getByRole('img', { name: 'Image 1 of 1' }));
});

test('should_keep_a_saved_post_saved_after_a_reload_and_list_it_on_the_saved_page', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `worth keeping from ${ana.user.username}`;
    await expectFeedToContain(ana, await postTweet(ana, content));
    await openAs(page, ana, '/feed');
    const saved = waitForCall(page, 'PUT', '/saved-tweets/');

    await saveButton(postWithText(page, content))
        .click();
    expect((await saved).status()).toBe(204);
    await page.reload();

    await expect(saveButton(postWithText(page, content))).toHaveAttribute('aria-pressed', 'true');

    await openAccountMenu(page);
    await page
        .getByRole('menuitem', { name: 'SAVED TWEETS' })
        .click();

    await expect(page).toHaveURL(/\/saved$/);
    await expect(saveButton(postWithText(page, content))).toHaveAttribute('aria-pressed', 'true');
});

test('should_keep_an_unsaved_post_on_saved_page_with_empty_bookmark_until_reload', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `saved then dropped by ${ana.user.username}`;
    await saveViaApi(ana, await postTweet(ana, content));
    await openAs(page, ana, '/saved');
    const post = postWithText(page, content);
    await expect(saveButton(post)).toHaveAttribute('aria-pressed', 'true');
    const unsaved = waitForCall(page, 'DELETE', '/saved-tweets/');

    await saveButton(post)
        .click();
    expect((await unsaved).status()).toBe(204);

    await expect(post).toBeVisible();
    await expect(saveButton(post)).toHaveAttribute('aria-pressed', 'false');

    await page.reload();

    await expect(page.getByText('NO SAVED TWEETS YET')).toBeVisible();
    await expect(postWithText(page, content)).toHaveCount(0);
});

test('should_fill_the_heart_and_move_the_count_from_0_to_1_when_a_post_is_liked', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `likeable from ${ana.user.username}`;
    await expectFeedToContain(ana, await postTweet(ana, content));
    await openAs(page, ana, '/feed');
    const like = likeButton(postWithText(page, content));
    await expect(like).toHaveAttribute('aria-pressed', 'false');
    await expect(like).toContainText('0');
    const liked = waitForCall(page, 'PUT', '/likes/');

    await like.click();

    await expect(like).toHaveAttribute('aria-pressed', 'true');
    await expect(like).toContainText('1');
    expect((await liked).status()).toBe(204);
});

test('should_load_the_second_page_when_scrolling_and_end_at_end_of_feed', async ({ page, createAccount }) => {
    const ana = await createAccount();

    for (let index = 1; index <= BULK_POST_COUNT; index++) {
        await postTweet(ana, `bulk post ${String(index).padStart(2, '0')}`);
    }

    await expect
        .poll(
            async () => (await feedTweetIds(ana, MAX_PAGE_SIZE)).length,
            { timeout: FEED_POLL_TIMEOUT_MS },
        )
        .toBe(BULK_POST_COUNT);

    await openAs(page, ana, '/feed');

    await expect(page.getByRole('article')).toHaveCount(FIRST_PAGE_SIZE);
    await expect(page.getByRole('article').first()).toContainText(`bulk post ${BULK_POST_COUNT}`);

    await expect(async () => {
        await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
        await expect(page.getByText('END OF FEED')).toBeVisible({ timeout: END_OF_FEED_CHECK_MS });
    }).toPass({ timeout: FEED_POLL_TIMEOUT_MS });

    await expect(page.getByRole('article')).toHaveCount(BULK_POST_COUNT);
});

test('should_offer_new_posts_after_a_minute_and_show_them_when_the_button_is_used', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await follow(ana, bob);
    await page.clock.install();
    await openAs(page, ana, '/feed');
    await expect(page.getByText('NOTHING HERE YET')).toBeVisible();
    const content = `posted while reading by ${bob.user.username}`;
    await expectFeedToContain(ana, await postTweet(bob, content));

    await page.clock.fastForward(NEW_POSTS_CHECK_MS);

    const button = page.getByRole('button', { name: 'NEW POSTS' });
    await expect(button).toBeVisible();

    await button.click();

    await expect(page.getByRole('article').first()).toContainText(content);
    await expect(button).toBeHidden();
});

test('should_count_a_post_that_stays_on_screen_as_viewed', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await follow(ana, bob);
    const content = `look at this from ${bob.user.username}`;
    const tweetId = await postTweet(bob, content);
    await expectFeedToContain(ana, tweetId);

    await openAs(page, ana, '/feed');
    await expect(postWithText(page, content)).toBeVisible();

    await expect
        .poll(
            () => readViews(ana, tweetId),
            { timeout: FEED_POLL_TIMEOUT_MS },
        )
        .toBe(1);
});

test('should_ask_whether_to_discard_the_post_when_escape_is_pressed_with_a_draft', async ({ page, createAccount }) => {
    const ana = await createAccount();
    await openAs(page, ana, '/feed');
    await page
        .getByRole('button', { name: 'Post' })
        .click();
    await page
        .getByRole('textbox', { name: "What's worth sending up?" })
        .fill('half a thought');

    await page.keyboard.press('Escape');

    await expect(page.getByText('DISCARD POST?')).toBeVisible();
    await expect(page.getByRole('dialog')).toBeVisible();

    await page.keyboard.press('Escape');

    await expect(page.getByText('DISCARD POST?')).toBeHidden();
    await expect(page.getByRole('textbox', { name: "What's worth sending up?" })).toHaveValue('half a thought');
});

test('should_open_the_saved_page_from_the_menu_and_log_out_to_the_login_page', async ({ page, createAccount }) => {
    const ana = await createAccount();
    await openAs(page, ana, '/feed');

    await openAccountMenu(page);
    await page
        .getByRole('menuitem', { name: 'SAVED TWEETS' })
        .click();

    await expect(page).toHaveURL(/\/saved$/);
    await expect(page.getByRole('heading', { name: 'SAVED TWEETS' })).toBeVisible();

    await openAccountMenu(page);
    await page
        .getByRole('menuitem', { name: 'LOG OUT' })
        .click();

    await expect(page).toHaveURL(/\/login$/);
});

test('should_land_on_the_login_page_when_the_cookie_is_gone_and_an_action_runs', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `still signed in from ${ana.user.username}`;
    await expectFeedToContain(ana, await postTweet(ana, content));
    await openAs(page, ana, '/feed');
    const like = likeButton(postWithText(page, content));
    await expect(like).toBeVisible();

    await page
        .context()
        .clearCookies();
    await like.click();

    await expect(page).toHaveURL(/\/login$/);
});

test('should_keep_the_heart_filled_and_the_count_at_1_after_a_reload_when_a_post_is_liked', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `liked and kept from ${ana.user.username}`;
    await expectFeedToContain(ana, await postTweet(ana, content));
    await openAs(page, ana, '/feed');
    const liked = waitForCall(page, 'PUT', '/likes/');

    await likeButton(postWithText(page, content))
        .click();
    expect((await liked).status()).toBe(204);
    await page.reload();

    const like = likeButton(postWithText(page, content));
    await expect(like).toHaveAttribute('aria-pressed', 'true');
    await expect(like).toContainText('1');
});

test('should_show_a_like_from_another_user_as_1_with_an_empty_heart_and_move_to_2_when_tapped', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const content = `liked by bob from ${ana.user.username}`;
    const tweetId = await postTweet(ana, content);
    await expectFeedToContain(ana, tweetId);
    await likeViaApi(bob, tweetId);
    await openAs(page, ana, '/feed');
    const like = likeButton(postWithText(page, content));
    await expect(like).toHaveAttribute('aria-pressed', 'false');
    await expect(like).toContainText('1');

    await like.click();

    await expect(like).toHaveAttribute('aria-pressed', 'true');
    await expect(like).toContainText('2');
});

test('should_list_a_liked_post_on_the_liked_page_when_liked_tweets_is_chosen_from_the_menu', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `on the liked page from ${ana.user.username}`;
    const tweetId = await postTweet(ana, content);
    await expectFeedToContain(ana, tweetId);
    await likeViaApi(ana, tweetId);
    await openAs(page, ana, '/feed');

    await openAccountMenu(page);
    await page
        .getByRole('menuitem', { name: 'LIKED TWEETS' })
        .click();

    await expect(page).toHaveURL(/\/liked$/);
    await expect(likeButton(postWithText(page, content))).toHaveAttribute('aria-pressed', 'true');
});

test('should_keep_an_unliked_post_on_the_liked_page_with_an_empty_heart_and_0_until_a_reload_shows_no_liked_tweets_yet', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const content = `liked then dropped by ${ana.user.username}`;
    await likeViaApi(ana, await postTweet(ana, content));
    await openAs(page, ana, '/liked');
    const post = postWithText(page, content);
    await expect(likeButton(post)).toHaveAttribute('aria-pressed', 'true');
    const unliked = waitForCall(page, 'DELETE', '/likes/');

    await likeButton(post)
        .click();
    expect((await unliked).status()).toBe(204);

    await expect(post).toBeVisible();
    await expect(likeButton(post)).toHaveAttribute('aria-pressed', 'false');
    await expect(likeButton(post)).toContainText('0');

    await page.reload();

    await expect(page.getByText('NO LIKED TWEETS YET')).toBeVisible();
    await expect(postWithText(page, content)).toHaveCount(0);
});

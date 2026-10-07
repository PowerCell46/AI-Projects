import { expect, type Locator, type Page } from '@playwright/test';
import {
    expectFeedToContain,
    FEED_POLL_TIMEOUT_MS,
    feedTweetIds,
    follow,
    MAX_PAGE_SIZE,
    openAs,
    postTweet,
    test,
    type Account,
} from './fixtures';


// Registrations each wait for a confirmation email, and a follow crosses the outbox and Kafka before the feed fills.
const TEST_TIMEOUT_MS = 120_000;

const BULK_POST_COUNT = 25;

// A post well down the feed, so that opening it and coming back has a scroll position to keep.
const OPENED_POST_INDEX = 12;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

function postWithText(page: Page, text: string): Locator {
    return page
        .getByRole('article', { name: /^Post by/ })
        .filter({ hasText: text });
}

function replyWithText(page: Page, text: string): Locator {
    return page
        .getByRole('article', { name: /^Reply by/ })
        .filter({ hasText: text });
}

function replyLink(post: Locator): Locator {
    return post.getByRole('link', { name: /^Replies/ });
}

// On the details page the count is plain text, not a link.
function replyCount(post: Locator): Locator {
    return post.locator('[data-action="reply"]');
}

function currentScrollY(page: Page): Promise<number> {
    return page.evaluate(() => window.scrollY);
}

async function createReply(author: Account, tweetId: string, content: string) {
    const response = await author.api.post(
        `/api/v1/tweets/${tweetId}/replies`,
        { data: { content } },
    );

    expect(response.status()).toBe(201);
}

test('should_open_details_reply_and_return_to_the_feed_spot_when_a_post_is_clicked', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetIds: string[] = [];

    for (let index = 1; index <= BULK_POST_COUNT; index++) {
        tweetIds.push(await postTweet(bob, `bulk post ${index} by ${bob.user.username}`));
    }

    await follow(ana, bob);
    await expect
        .poll(
            async () => (await feedTweetIds(ana, MAX_PAGE_SIZE)).length,
            { timeout: FEED_POLL_TIMEOUT_MS },
        )
        .toBe(BULK_POST_COUNT);
    await openAs(page, ana, '/feed');
    const openedContent = `bulk post ${OPENED_POST_INDEX} by ${bob.user.username}`;
    const openedPost = postWithText(page, openedContent);
    await openedPost.scrollIntoViewIfNeeded();
    const scrollBefore = await currentScrollY(page);
    expect(scrollBefore).toBeGreaterThan(0);

    // Sent as an event, not a Playwright click, which may scroll first and so move the position being measured.
    await openedPost.getByText(openedContent).dispatchEvent('click');

    await expect(page).toHaveURL(new RegExp(`/tweets/${tweetIds[OPENED_POST_INDEX - 1]}$`));
    await expect(postWithText(page, openedContent)).toBeVisible();
    await expect(page.getByRole('tablist')).toHaveCount(0);
    await page
        .getByRole('button', { name: 'Write a reply…' })
        .click();
    await page
        .getByLabel('YOUR REPLY')
        .fill('a reply from ana');
    await page
        .getByRole('button', {
            name: 'REPLY',
            exact: true,
        })
        .click();

    await expect(replyWithText(page, 'a reply from ana')).toBeVisible();
    await expect(replyCount(postWithText(page, openedContent))).toHaveText(/1$/);

    await page
        .getByRole('link', { name: 'BACK' })
        .click();

    await expect(page).toHaveURL(/\/feed$/);
    await expect
        .poll(() => currentScrollY(page))
        .toBe(scrollBefore);
});

test('should_show_edited_after_a_reload_when_the_author_edits_a_reply', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `a post by ${bob.user.username}`);
    await createReply(ana, tweetId, 'a reply to be edited');
    await openAs(page, ana, `/tweets/${tweetId}`);
    const reply = replyWithText(page, 'a reply to be edited');
    await expect(reply).toBeVisible();
    await expect(reply.getByText('EDITED', { exact: true })).toHaveCount(0);

    await reply
        .getByRole('button', { name: 'EDIT' })
        .click();
    await page
        .getByLabel('EDIT YOUR REPLY')
        .fill('a reply that was edited');
    await page
        .getByRole('button', { name: 'SAVE', exact: true })
        .click();

    await expect(replyWithText(page, 'a reply that was edited').getByText('EDITED', { exact: true })).toBeVisible();

    await page.reload();

    await expect(replyWithText(page, 'a reply that was edited').getByText('EDITED', { exact: true })).toBeVisible();
});

test('should_remove_the_reply_and_drop_the_count_when_the_post_author_deletes_it', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `a post by ${bob.user.username}`);
    await createReply(ana, tweetId, 'a reply bob will delete');
    await openAs(page, bob, `/tweets/${tweetId}`);
    const reply = replyWithText(page, 'a reply bob will delete');
    await expect(reply).toBeVisible();
    await expect(reply.getByRole('button', { name: 'EDIT' })).toHaveCount(0);
    await expect(replyCount(postWithText(page, 'a post by'))).toHaveText(/1$/);

    await reply
        .getByRole('button', { name: 'DELETE' })
        .click();
    await reply
        .getByRole('button', { name: 'YES' })
        .click();

    await expect(reply).toHaveCount(0);
    await expect(replyCount(postWithText(page, 'a post by'))).toHaveText(/0$/);

    await page.reload();

    await expect(page.getByText('NO REPLIES YET')).toBeVisible();
    await expect(replyCount(postWithText(page, 'a post by'))).toHaveText(/0$/);
});

test('should_open_details_when_the_reply_link_is_activated_with_the_keyboard', async ({ page, createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const content = `a post by ${bob.user.username}`;
    const tweetId = await postTweet(bob, content);
    await follow(ana, bob);
    await expectFeedToContain(ana, tweetId);
    await openAs(page, ana, '/feed');
    const link = replyLink(postWithText(page, content));
    await expect(link).toBeVisible();

    // Shift+Tab then Tab proves the link is a stop in the tab order, not only focusable by script.
    await link.focus();
    await page.keyboard.press('Shift+Tab');
    await page.keyboard.press('Tab');
    await expect(link).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page).toHaveURL(new RegExp(`/tweets/${tweetId}$`));
    await expect(postWithText(page, content)).toBeVisible();
});

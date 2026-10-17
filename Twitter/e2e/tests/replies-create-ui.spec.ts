import { expect, type Locator, type Page } from '@playwright/test';
import {
    API_PATHS,
    APP_PATHS,
    deleteTweet,
    MAX_TWEET_CHARACTERS,
    openAs,
    postTweet,
    test,
    TEST_TIMEOUT_MS,
    type Account,
} from './fixtures';

const EMOJI = '\u{1F600}';

const INTERNAL_SERVER_ERROR_STATUS = 500;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

interface ReplyPage {
    items: unknown[];
}

function replyWithText(page: Page, text: string): Locator {
    return page
        .getByRole('article', { name: /^Reply by/ })
        .filter({ hasText: text });
}

function replyField(page: Page): Locator {
    return page.getByLabel('YOUR REPLY');
}

function submitButton(page: Page): Locator {
    return page.getByRole('button', {
        name: 'REPLY',
        exact: true,
    });
}

async function openComposer(page: Page) {
    await page
        .getByRole('button', { name: 'Write a reply…' })
        .click();

    await expect(replyField(page)).toBeFocused();
}

async function sendReply(page: Page, text: string) {
    await openComposer(page);
    await replyField(page).fill(text);
    await submitButton(page).click();
}

async function storedReplyCount(reader: Account, tweetId: string): Promise<number> {
    const response = await reader.api.get(API_PATHS.replies(tweetId));

    expect(response.ok()).toBe(true);

    const replyPage: ReplyPage = await response.json();

    return replyPage.items.length;
}

// Records the browser's reply POSTs, so a test can show that a refused reply never reached the server.
function recordReplyPosts(page: Page): string[] {
    const posts: string[] = [];

    page.on(
        'request',
        (request) => {
            if (request.method() === 'POST' && request.url().includes('/replies')) {
                posts.push(request.url());
            }
        },
    );

    return posts;
}

async function openTweetOfAnotherUser(page: Page, reader: Account, author: Account): Promise<string> {
    const tweetId = await postTweet(author, `a post by ${author.user.username}`);

    await openAs(page, reader, APP_PATHS.tweet(tweetId));
    await expect(page.getByText(`a post by ${author.user.username}`)).toBeVisible();

    return tweetId;
}

test(
    'should_show_the_reply_with_its_author_and_close_the_composer_when_a_reply_is_sent',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);

        await sendReply(page, '  a reply from ana  ');

        const reply = replyWithText(page, 'a reply from ana');

        await expect(reply).toBeVisible();
        await expect(reply).toContainText(ana.user.username);
        await expect(page.getByRole('button', { name: 'Write a reply…' })).toBeVisible();
        expect(await storedReplyCount(ana, tweetId)).toBe(1);
    },
);

test(
    'should_keep_the_reply_after_a_reload_when_it_was_sent',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await openTweetOfAnotherUser(page, ana, bob);
        await sendReply(page, 'a reply that stays');
        await expect(replyWithText(page, 'a reply that stays')).toBeVisible();

        await page.reload();

        await expect(replyWithText(page, 'a reply that stays')).toBeVisible();
    },
);

test(
    'should_accept_a_reply_of_exactly_280_characters_when_it_is_sent',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);
        const content = 'a'.repeat(MAX_TWEET_CHARACTERS);

        await openComposer(page);
        await replyField(page).fill(content);

        await expect(page.getByText(`${MAX_TWEET_CHARACTERS} / ${MAX_TWEET_CHARACTERS}`)).toBeVisible();

        await submitButton(page).click();

        await expect(replyWithText(page, content)).toBeVisible();
        expect(await storedReplyCount(ana, tweetId)).toBe(1);
    },
);

test(
    'should_count_an_emoji_as_one_character_when_a_reply_of_280_emoji_is_sent',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);

        await openComposer(page);
        await replyField(page).fill(EMOJI.repeat(MAX_TWEET_CHARACTERS));

        await expect(page.getByText(`${MAX_TWEET_CHARACTERS} / ${MAX_TWEET_CHARACTERS}`)).toBeVisible();

        await submitButton(page).click();

        await expect
            .poll(() => storedReplyCount(ana, tweetId))
            .toBe(1);
    },
);

test(
    'should_not_send_and_fold_the_composer_back_when_the_empty_send_button_is_pressed',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);
        const replyPosts = recordReplyPosts(page);
        await openComposer(page);

        await expect(submitButton(page)).toHaveAttribute('aria-disabled', 'true');

        // The button is aria-disabled, which Playwright treats as not clickable, so the press is forced.
        // Pressing it blurs the empty field, which folds the composer back into its row.
        await submitButton(page).click({ force: true });

        await expect(page.getByRole('button', { name: 'Write a reply…' })).toBeVisible();
        await expect(page.getByRole('article', { name: /^Reply by/ })).toHaveCount(0);
        expect(replyPosts).toEqual([]);
        expect(await storedReplyCount(ana, tweetId)).toBe(0);
    },
);

test(
    'should_not_send_and_keep_the_composer_open_when_the_reply_is_only_spaces',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);
        const replyPosts = recordReplyPosts(page);
        await openComposer(page);
        await replyField(page).fill('     ');

        await expect(submitButton(page)).toHaveAttribute('aria-disabled', 'true');

        await submitButton(page).click({ force: true });

        await expect(replyField(page)).toBeVisible();
        await expect(page.getByRole('article', { name: /^Reply by/ })).toHaveCount(0);
        expect(replyPosts).toEqual([]);
        expect(await storedReplyCount(ana, tweetId)).toBe(0);
    },
);

test(
    'should_mark_the_counter_and_not_send_when_the_reply_has_281_characters',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);
        const replyPosts = recordReplyPosts(page);
        await openComposer(page);

        await replyField(page).fill('a'.repeat(MAX_TWEET_CHARACTERS + 1));

        const counter = page.getByText(`${MAX_TWEET_CHARACTERS + 1} / ${MAX_TWEET_CHARACTERS}`);

        await expect(counter).toHaveAttribute('data-over-limit', 'true');
        await expect(submitButton(page)).toHaveAttribute('aria-disabled', 'true');

        await submitButton(page).click({ force: true });

        await expect(page.getByRole('article', { name: /^Reply by/ })).toHaveCount(0);
        expect(replyPosts).toEqual([]);
        expect(await storedReplyCount(ana, tweetId)).toBe(0);

        await replyField(page).fill('a'.repeat(MAX_TWEET_CHARACTERS));

        await expect(page.getByText(`${MAX_TWEET_CHARACTERS} / ${MAX_TWEET_CHARACTERS}`)).toHaveAttribute('data-over-limit', 'false');
        await expect(submitButton(page)).toHaveAttribute('aria-disabled', 'false');
    },
);

test(
    'should_discard_the_text_and_close_the_composer_when_cancel_is_pressed',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);
        await openComposer(page);
        await replyField(page).fill('a reply that will be dropped');

        await page
            .getByRole('button', { name: 'CANCEL' })
            .click();

        await expect(page.getByRole('button', { name: 'Write a reply…' })).toBeVisible();

        await openComposer(page);

        await expect(replyField(page)).toHaveValue('');
        expect(await storedReplyCount(ana, tweetId)).toBe(0);
    },
);

test(
    'should_close_the_composer_when_escape_is_pressed_in_the_field',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        await openTweetOfAnotherUser(page, ana, bob);
        await openComposer(page);

        await page.keyboard.press('Escape');

        await expect(page.getByRole('button', { name: 'Write a reply…' })).toBeVisible();
        await expect(page.getByLabel('YOUR REPLY')).toHaveCount(0);
    },
);

test(
    'should_show_a_message_and_keep_the_text_when_the_server_fails_to_save_the_reply',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);
        await page.route(
            `**${API_PATHS.replies(tweetId)}`,
            (route) => (route.request().method() === 'POST'
                ? route.fulfill({ status: INTERNAL_SERVER_ERROR_STATUS })
                : route.fallback()),
        );

        await sendReply(page, 'a reply the server drops');

        await expect(page.getByRole('alert')).toHaveText("Couldn't send. Try again.");
        await expect(replyField(page)).toHaveValue('a reply the server drops');
        expect(await storedReplyCount(ana, tweetId)).toBe(0);
    },
);

test(
    'should_say_the_post_was_deleted_when_a_reply_is_sent_to_a_post_deleted_meanwhile',
    async ({ page, createAccount }) => {
        const ana = await createAccount();
        const bob = await createAccount();
        const tweetId = await openTweetOfAnotherUser(page, ana, bob);
        await openComposer(page);
        await replyField(page).fill('a reply to a vanished post');
        await deleteTweet(bob, tweetId);

        await submitButton(page).click();

        await expect(page.getByRole('alert')).toHaveText('This post was deleted.');
    },
);

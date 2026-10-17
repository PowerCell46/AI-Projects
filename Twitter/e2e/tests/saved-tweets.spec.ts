import { expect } from '@playwright/test';
import { deleteTweet, postTweet, test, TEST_TIMEOUT_MS, type Account } from './fixtures';

const ENDPOINTS = {
    savedTweets: '/api/v1/saved-tweets',
    savedTweet: (tweetId: string) => `/api/v1/saved-tweets/${tweetId}`,
} as const;

const SAVED_LIST_POLL_TIMEOUT_MS = 30_000;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

interface SavedTweetAuthor {
    username: string;
}

interface SavedTweetItem {
    id: string;
    content: string;
    author: SavedTweetAuthor;
}

interface SavedTweetsPage {
    items: SavedTweetItem[];
    nextCursor: string | null;
}

async function saveTweet(reader: Account, tweetId: string): Promise<void> {
    const response = await reader.api.put(ENDPOINTS.savedTweet(tweetId));

    expect(response.status()).toBe(204);
}

async function unsaveTweet(reader: Account, tweetId: string): Promise<void> {
    const response = await reader.api.delete(ENDPOINTS.savedTweet(tweetId));

    expect(response.status()).toBe(204);
}

async function readSavedTweets(reader: Account): Promise<SavedTweetsPage> {
    const response = await reader.api.get(ENDPOINTS.savedTweets);

    expect(response.ok()).toBe(true);

    return response.json();
}

async function savedTweetIds(reader: Account): Promise<string[]> {
    const savedTweets = await readSavedTweets(reader);

    return savedTweets.items.map((item) => item.id);
}

test('should_list_the_tweet_once_with_its_author_when_it_is_saved_twice', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const content = `worth saving from ${bob.user.username}`;
    const tweetId = await postTweet(bob, content);

    await saveTweet(ana, tweetId);
    await saveTweet(ana, tweetId);

    const savedTweets = await readSavedTweets(ana);

    expect(savedTweets.items).toHaveLength(1);
    expect(savedTweets.items[0].id).toBe(tweetId);
    expect(savedTweets.items[0].content).toBe(content);
    expect(savedTweets.items[0].author.username).toBe(bob.user.username);
    expect(savedTweets.nextCursor).toBeNull();
});

test('should_empty_the_list_when_the_tweet_is_unsaved_twice', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `saved then unsaved ${bob.user.username}`);
    await saveTweet(ana, tweetId);

    await unsaveTweet(ana, tweetId);
    await unsaveTweet(ana, tweetId);

    expect(await savedTweetIds(ana)).toEqual([]);
});

test('should_remove_the_tweet_from_the_list_when_the_author_deletes_it', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `to be deleted by ${bob.user.username}`);
    await saveTweet(ana, tweetId);

    expect(await savedTweetIds(ana)).toEqual([tweetId]);

    await deleteTweet(bob, tweetId);

    await expect
        .poll(() => savedTweetIds(ana), { timeout: SAVED_LIST_POLL_TIMEOUT_MS })
        .toEqual([]);
});

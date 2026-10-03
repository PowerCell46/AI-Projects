import { expect } from '@playwright/test';
import { deleteTweet, postTweet, test, type Account } from './fixtures';


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

// Two registrations each wait for a confirmation email, and a delete crosses the outbox and Kafka.
const TEST_TIMEOUT_MS = 120_000;

const SAVED_LIST_POLL_TIMEOUT_MS = 30_000;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function saveTweet(reader: Account, tweetId: string) {
    const response = await reader.api.put(`/api/v1/saved-tweets/${tweetId}`);

    expect(response.status()).toBe(204);
}

async function unsaveTweet(reader: Account, tweetId: string) {
    const response = await reader.api.delete(`/api/v1/saved-tweets/${tweetId}`);

    expect(response.status()).toBe(204);
}

async function readSavedTweets(reader: Account): Promise<SavedTweetsPage> {
    const response = await reader.api.get('/api/v1/saved-tweets');

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

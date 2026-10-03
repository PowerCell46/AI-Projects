import { expect } from '@playwright/test';
import { deleteTweet, follow, postTweet, test, type Account } from './fixtures';


interface FeedAuthor {
    username: string;
}

interface FeedItem {
    id: string;
    content: string;
    author: FeedAuthor;
}

interface FeedPage {
    items: FeedItem[];
    nextCursor: string | null;
}

// Three registrations each wait for a confirmation email, and every feed change crosses Kafka and the outbox.
const TEST_TIMEOUT_MS = 120_000;

const FEED_POLL_TIMEOUT_MS = 30_000;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function unfollow(follower: Account, followee: Account) {
    const response = await follower.api.delete(`/api/v1/users/${followee.user.username}/follow`);

    expect(response.status()).toBe(204);
}

async function readFeed(reader: Account): Promise<FeedPage> {
    const response = await reader.api.get('/api/v1/feed');

    expect(response.ok()).toBe(true);

    return response.json();
}

async function feedTweetIds(reader: Account): Promise<string[]> {
    const feed = await readFeed(reader);

    return feed.items.map((item) => item.id);
}

async function expectFeedToContain(reader: Account, tweetId: string) {
    await expect
        .poll(() => feedTweetIds(reader), { timeout: FEED_POLL_TIMEOUT_MS })
        .toContain(tweetId);
}

async function expectFeedToBeEmpty(reader: Account) {
    await expect
        .poll(() => feedTweetIds(reader), { timeout: FEED_POLL_TIMEOUT_MS })
        .toEqual([]);
}

test('should_show_the_tweet_with_its_author_in_the_followers_and_the_authors_feeds_and_not_in_a_strangers', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const carol = await createAccount();
    await follow(ana, bob);

    const content = `hello from ${bob.user.username}`;
    const tweetId = await postTweet(bob, content);

    await expectFeedToContain(ana, tweetId);
    await expectFeedToContain(bob, tweetId);

    const anasFeed = await readFeed(ana);

    expect(anasFeed.items).toHaveLength(1);
    expect(anasFeed.items[0].content).toBe(content);
    expect(anasFeed.items[0].author.username).toBe(bob.user.username);

    // Bob's entry is written first and the followers' in one page, so Ana's entry means the fan-out is done.
    expect(await feedTweetIds(carol)).toEqual([]);
});

test('should_remove_the_tweet_from_the_followers_feed_when_the_author_deletes_it', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await follow(ana, bob);
    const tweetId = await postTweet(bob, `to be deleted by ${bob.user.username}`);
    await expectFeedToContain(ana, tweetId);

    await deleteTweet(bob, tweetId);

    await expectFeedToBeEmpty(ana);
});

test('should_drop_the_authors_tweets_on_unfollow_and_show_only_his_next_tweet_after_a_refollow', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    await follow(ana, bob);
    const tweetBeforeUnfollow = await postTweet(bob, `before the unfollow ${bob.user.username}`);
    await expectFeedToContain(ana, tweetBeforeUnfollow);

    await unfollow(ana, bob);

    await expectFeedToBeEmpty(ana);

    await follow(ana, bob);
    const tweetAfterRefollow = await postTweet(bob, `after the refollow ${bob.user.username}`);

    await expectFeedToContain(ana, tweetAfterRefollow);
    expect(await feedTweetIds(ana)).toEqual([tweetAfterRefollow]);
});

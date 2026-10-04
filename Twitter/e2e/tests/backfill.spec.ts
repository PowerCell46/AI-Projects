import { expect } from '@playwright/test';
import { FEED_POLL_TIMEOUT_MS, feedTweetIds, follow, postTweet, test, unfollow } from './fixtures';


// Three registrations each wait for a confirmation email, and every back-fill crosses the outbox and Kafka.
const TEST_TIMEOUT_MS = 120_000;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

test('should_fill_the_followers_feed_with_the_authors_older_tweets_newest_first_when_the_user_follows', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const olderTweet = await postTweet(bob, `older ${bob.user.username}`);
    const newerTweet = await postTweet(bob, `newer ${bob.user.username}`);

    await follow(ana, bob);

    await expect
        .poll(() => feedTweetIds(ana), { timeout: FEED_POLL_TIMEOUT_MS })
        .toEqual([newerTweet, olderTweet]);
});

// Ana's and Cy's follow events carry the same key (Bob), so they sit on one partition and Cy's is handled after
// Ana's. Cy's feed filling therefore proves Ana's back-fill has already run and been undone, with no sleep.
test('should_leave_none_of_the_authors_tweets_in_the_feed_when_the_user_follows_and_unfollows_at_once', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const cy = await createAccount();
    const olderTweet = await postTweet(bob, `older ${bob.user.username}`);
    const newerTweet = await postTweet(bob, `newer ${bob.user.username}`);

    await follow(ana, bob);
    await unfollow(ana, bob);
    await follow(cy, bob);

    await expect
        .poll(() => feedTweetIds(cy), { timeout: FEED_POLL_TIMEOUT_MS })
        .toEqual([newerTweet, olderTweet]);
    expect(await feedTweetIds(ana)).toEqual([]);
});

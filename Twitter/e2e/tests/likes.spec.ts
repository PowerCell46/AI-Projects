import { randomUUID } from 'node:crypto';
import { expect } from '@playwright/test';
import { deleteTweet, postTweet, test, type Account } from './fixtures';


interface LikedTweetItem {
    id: string;
    likes: number;
    likedByMe: boolean;
    author: {
        username: string;
    };
}

interface LikedTweetsPage {
    items: LikedTweetItem[];
    nextCursor: string | null;
}

interface FeedItemWithLikes {
    id: string;
    likes: number;
    likedByMe: boolean;
}

interface FeedPageWithLikes {
    items: FeedItemWithLikes[];
}

interface CurrentUser {
    id: string;
}

// Two registrations each wait for a confirmation email, and the feed entry and the delete cross Kafka.
const TEST_TIMEOUT_MS = 120_000;

const POLL_TIMEOUT_MS = 30_000;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function likeTweet(liker: Account, tweetId: string) {
    const response = await liker.api.put(`/api/v1/likes/${tweetId}`);

    expect(response.status()).toBe(204);
}

async function unlikeTweet(liker: Account, tweetId: string) {
    const response = await liker.api.delete(`/api/v1/likes/${tweetId}`);

    expect(response.status()).toBe(204);
}

async function readLikedTweets(reader: Account): Promise<LikedTweetsPage> {
    const response = await reader.api.get('/api/v1/likes');

    expect(response.ok()).toBe(true);

    return response.json();
}

async function likedTweetIds(reader: Account): Promise<string[]> {
    const likedTweets = await readLikedTweets(reader);

    return likedTweets.items.map((item) => item.id);
}

async function feedItemOf(reader: Account, tweetId: string): Promise<FeedItemWithLikes | undefined> {
    const response = await reader.api.get('/api/v1/feed');

    expect(response.ok()).toBe(true);

    const feed: FeedPageWithLikes = await response.json();

    return feed.items.find((item) => item.id === tweetId);
}

async function currentUserId(account: Account): Promise<string> {
    const response = await account.api.get('/api/v1/auth/me');

    expect(response.ok()).toBe(true);

    const me: CurrentUser = await response.json();

    return me.id;
}

test('should_list_the_post_once_and_count_one_like_when_it_is_liked_twice', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `worth liking from ${bob.user.username}`);

    await likeTweet(ana, tweetId);
    await likeTweet(ana, tweetId);

    const likedTweets = await readLikedTweets(ana);

    expect(likedTweets.items).toHaveLength(1);
    expect(likedTweets.items[0].id).toBe(tweetId);
    expect(likedTweets.items[0].author.username).toBe(bob.user.username);
    expect(likedTweets.items[0].likes).toBe(1);
    expect(likedTweets.items[0].likedByMe).toBe(true);
    expect(likedTweets.nextCursor).toBeNull();

    await expect
        .poll(() => feedItemOf(bob, tweetId), { timeout: POLL_TIMEOUT_MS })
        .toMatchObject({ likes: 1, likedByMe: false });
});

test('should_count_two_likes_and_empty_the_list_when_both_like_and_one_unlikes_twice', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `liked by two from ${bob.user.username}`);
    await likeTweet(ana, tweetId);
    await likeTweet(bob, tweetId);

    expect((await readLikedTweets(ana)).items[0].likes).toBe(2);

    await unlikeTweet(ana, tweetId);
    await unlikeTweet(ana, tweetId);

    expect(await likedTweetIds(ana)).toEqual([]);

    const bobsLikedTweets = await readLikedTweets(bob);

    expect(bobsLikedTweets.items).toHaveLength(1);
    expect(bobsLikedTweets.items[0].likes).toBe(1);
});

test('should_remove_the_post_from_the_liked_list_when_the_author_deletes_it', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `to be deleted by ${bob.user.username}`);
    await likeTweet(ana, tweetId);
    expect(await likedTweetIds(ana)).toEqual([tweetId]);

    await deleteTweet(bob, tweetId);

    await expect
        .poll(() => likedTweetIds(ana), { timeout: POLL_TIMEOUT_MS })
        .toEqual([]);
});

test('should_return_404_and_keep_the_list_empty_when_the_post_is_unknown', async ({ createAccount }) => {
    const ana = await createAccount();

    const response = await ana.api.put(`/api/v1/likes/${randomUUID()}`);

    expect(response.status()).toBe(404);
    expect(await likedTweetIds(ana)).toEqual([]);
});

test('should_store_the_like_for_the_caller_when_a_forged_user_id_header_is_sent', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const tweetId = await postTweet(bob, `forged like target ${bob.user.username}`);
    const bobId = await currentUserId(bob);

    const response = await ana.api.put(`/api/v1/likes/${tweetId}`, { headers: { 'X-User-Id': bobId } });

    expect(response.status()).toBe(204);
    expect(await likedTweetIds(ana)).toEqual([tweetId]);
    expect(await likedTweetIds(bob)).toEqual([]);
});

import { expect } from '@playwright/test';
import { deleteTweet, follow, postTweet, test, type Account } from './fixtures';


interface FeedItemWithViews {
    id: string;
    views: number;
}

interface FeedPageWithViews {
    items: FeedItemWithViews[];
}

type ViewCounts = Record<string, number>;

// Three registrations each wait for a confirmation email, and the feed entry and the delete cross Kafka.
const TEST_TIMEOUT_MS = 120_000;

const POLL_TIMEOUT_MS = 30_000;

const REPORTS_PER_VIEWER = 2;

const UNIQUE_VIEWERS = 2;

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function reportView(viewer: Account, tweetId: string) {
    const response = await viewer.api.post('/api/v1/views', { data: { tweetIds: [tweetId] } });

    expect(response.status()).toBe(204);
}

async function readViews(reader: Account, tweetId: string): Promise<number> {
    const response = await reader.api.get(`/api/v1/views?tweetIds=${tweetId}`);

    expect(response.ok()).toBe(true);

    const counts: ViewCounts = await response.json();

    return counts[tweetId];
}

async function viewsInFeed(reader: Account, tweetId: string): Promise<number | undefined> {
    const response = await reader.api.get('/api/v1/feed');

    expect(response.ok()).toBe(true);

    const feed: FeedPageWithViews = await response.json();

    return feed.items.find((item) => item.id === tweetId)?.views;
}

test('should_count_each_viewer_once_and_drop_the_count_when_the_author_deletes_the_tweet', async ({ createAccount }) => {
    const ana = await createAccount();
    const bob = await createAccount();
    const carol = await createAccount();
    await follow(ana, bob);
    const tweetId = await postTweet(bob, `seen by two from ${bob.user.username}`);

    for (const viewer of [ana, carol]) {
        for (let report = 0; report < REPORTS_PER_VIEWER; report++) {
            await reportView(viewer, tweetId);
        }
    }

    expect(await readViews(ana, tweetId)).toBe(UNIQUE_VIEWERS);
    await expect
        .poll(() => viewsInFeed(ana, tweetId), { timeout: POLL_TIMEOUT_MS })
        .toBe(UNIQUE_VIEWERS);

    await deleteTweet(bob, tweetId);

    await expect
        .poll(() => readViews(ana, tweetId), { timeout: POLL_TIMEOUT_MS })
        .toBe(0);
});

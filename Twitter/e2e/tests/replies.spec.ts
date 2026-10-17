import { expect } from '@playwright/test';
import {
    API_PATHS,
    deleteTweet,
    FEED_POLL_TIMEOUT_MS,
    HTTP_STATUS,
    postTweet,
    test,
    TEST_TIMEOUT_MS,
    type Account,
} from './fixtures';

interface ReplyAuthor {
    username: string;
}

interface Reply {
    id: string;
    content: string;
    edited: boolean;
    author: ReplyAuthor;
}

interface ReplyPage {
    items: Reply[];
    nextCursor: string | null;
}

interface TweetWithReplyCount {
    id: string;
    replyCount: number;
}

interface FeedPageWithReplyCounts {
    items: TweetWithReplyCount[];
}

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function createReply(author: Account, tweetId: string, content: string): Promise<Reply> {
    const response = await author.api.post(
        API_PATHS.replies(tweetId),
        { data: { content } },
    );

    expect(response.status()).toBe(HTTP_STATUS.created);

    return response.json();
}

async function readReplies(reader: Account, tweetId: string): Promise<ReplyPage> {
    const response = await reader.api.get(API_PATHS.replies(tweetId));

    expect(response.ok()).toBe(true);

    return response.json();
}

async function readTweetDetails(reader: Account, tweetId: string): Promise<TweetWithReplyCount> {
    const response = await reader.api.get(API_PATHS.tweetDetails(tweetId));

    expect(response.ok()).toBe(true);

    return response.json();
}

async function feedItemOf(reader: Account, tweetId: string): Promise<TweetWithReplyCount | undefined> {
    const response = await reader.api.get(API_PATHS.feed);

    expect(response.ok()).toBe(true);

    const feed: FeedPageWithReplyCounts = await response.json();

    return feed.items.find((item) => item.id === tweetId);
}

test.describe(
    'replies',
    () => {
        test(
            'should_list_bobs_reply_with_his_username_and_count_one_in_the_feed_and_the_details_read_when_bob_replies_to_anas_tweet',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const tweetId = await postTweet(ana, `reply to me from ${ana.user.username}`);

                await createReply(bob, tweetId, 'a reply from bob');

                const replies = await readReplies(ana, tweetId);

                expect(replies.items).toHaveLength(1);
                expect(replies.items[0].content).toBe('a reply from bob');
                expect(replies.items[0].author.username).toBe(bob.user.username);
                expect(replies.items[0].edited).toBe(false);
                expect(replies.nextCursor).toBeNull();
                expect((await readTweetDetails(ana, tweetId)).replyCount).toBe(1);

                await expect
                    .poll(
                        () => feedItemOf(ana, tweetId),
                        { timeout: FEED_POLL_TIMEOUT_MS },
                    )
                    .toMatchObject({ replyCount: 1 });
            },
        );

        test(
            'should_mark_the_reply_edited_when_bob_edits_it',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const tweetId = await postTweet(ana, `edit a reply to ${ana.user.username}`);
                const reply = await createReply(bob, tweetId, 'before');

                const response = await bob.api.put(
                    API_PATHS.reply(tweetId, reply.id),
                    { data: { content: 'after' } },
                );

                expect(response.status()).toBe(HTTP_STATUS.ok);

                const editedReply: Reply = await response.json();

                expect(editedReply.content).toBe('after');
                expect(editedReply.edited).toBe(true);
                expect((await readReplies(ana, tweetId)).items[0]).toMatchObject({
                    content: 'after',
                    edited: true,
                });
            },
        );

        test(
            'should_answer_404_when_carol_edits_or_deletes_bobs_reply',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const carol = await createAccount();
                const tweetId = await postTweet(ana, `protect replies on ${ana.user.username}`);
                const reply = await createReply(bob, tweetId, 'bobs words');

                const editResponse = await carol.api.put(
                    API_PATHS.reply(tweetId, reply.id),
                    { data: { content: 'carols words' } },
                );
                const removalResponse = await carol.api.delete(API_PATHS.reply(tweetId, reply.id));

                expect(editResponse.status()).toBe(HTTP_STATUS.notFound);
                expect(removalResponse.status()).toBe(HTTP_STATUS.notFound);
                expect((await readReplies(ana, tweetId)).items[0]).toMatchObject({
                    content: 'bobs words',
                    edited: false,
                });
                expect((await readTweetDetails(ana, tweetId)).replyCount).toBe(1);
            },
        );

        test(
            'should_remove_the_reply_and_return_the_count_to_zero_when_ana_deletes_it',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const tweetId = await postTweet(ana, `clean up replies on ${ana.user.username}`);
                const reply = await createReply(bob, tweetId, 'to be removed');

                const removalResponse = await ana.api.delete(API_PATHS.reply(tweetId, reply.id));

                expect(removalResponse.status()).toBe(HTTP_STATUS.noContent);
                expect((await readReplies(ana, tweetId)).items).toHaveLength(0);
                expect((await readTweetDetails(ana, tweetId)).replyCount).toBe(0);

                await expect
                    .poll(
                        () => feedItemOf(ana, tweetId),
                        { timeout: FEED_POLL_TIMEOUT_MS },
                    )
                    .toMatchObject({ replyCount: 0 });
            },
        );

        test(
            'should_answer_404_on_the_replies_list_and_the_details_read_when_ana_deletes_the_tweet',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const tweetId = await postTweet(ana, `delete me with my replies ${ana.user.username}`);
                await createReply(bob, tweetId, 'will go with the tweet');

                await deleteTweet(ana, tweetId);

                const repliesResponse = await ana.api.get(API_PATHS.replies(tweetId));
                const detailsResponse = await ana.api.get(API_PATHS.tweetDetails(tweetId));

                expect(repliesResponse.status()).toBe(HTTP_STATUS.notFound);
                expect(detailsResponse.status()).toBe(HTTP_STATUS.notFound);
            },
        );
    },
);

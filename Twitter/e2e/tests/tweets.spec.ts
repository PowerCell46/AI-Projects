import { randomUUID } from 'node:crypto';
import { expect, type APIResponse } from '@playwright/test';
import {
    API_PATHS,
    expectFeedToContain,
    FEED_POLL_TIMEOUT_MS,
    feedTweetIds,
    HTTP_STATUS,
    MAX_TWEET_CHARACTERS,
    MAX_TWEET_IMAGES,
    postTweet,
    test,
    TEST_TIMEOUT_MS,
    TEXT_DECLARED_AS_PNG,
    TINY_PNG,
    type Account,
    type UploadedImage,
} from './fixtures';

const USER_ID_HEADER = 'X-User-Id';

// The tweet service refuses a single image above this many bytes.
const MAX_IMAGE_BYTES = 5_242_880;

const EMOJI = '\u{1F600}';

const UNAUTHENTICATED_MESSAGE = 'Authentication is required.';

interface TweetImage {
    id: string;
    sizeBytes: number;
    contentType: string;
}

interface Tweet {
    id: string;
    authorId: string;
    content: string;
    createdAt: string;
    updatedAt: string;
    images: TweetImage[];
}

interface TweetDetails {
    id: string;
    content: string;
}

interface TweetCount {
    count: number;
}

interface ErrorBody {
    status: number;
    messages: string[];
}

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

// A valid PNG header followed by zero bytes, so the type check passes at any size.
function pngOfSize(sizeBytes: number): UploadedImage {
    const padding = Buffer.alloc(sizeBytes - TINY_PNG.buffer.length);

    return {
        name: `padded-${sizeBytes}.png`,
        mimeType: TINY_PNG.mimeType,
        buffer: Buffer.concat([TINY_PNG.buffer, padding]),
    };
}

// The same tiny PNG under a different name each time, as a user attaching several pictures would.
function severalPngs(count: number): UploadedImage[] {
    return Array.from(
        { length: count },
        (_, index) => ({
            ...TINY_PNG,
            name: `dot-${index + 1}.png`,
        }),
    );
}

// The images field repeats once per file, which Playwright's plain multipart object cannot express.
function multipartOf(content: string, images: UploadedImage[]): FormData {
    const form = new FormData();

    form.append('content', content);
    images.forEach((image) => form.append(
        'images',
        new Blob([new Uint8Array(image.buffer)], { type: image.mimeType }),
        image.name,
    ));

    return form;
}

async function createTweetResponse(author: Account, content: string, images: UploadedImage[] = []): Promise<APIResponse> {
    return author.api.post(
        API_PATHS.tweets,
        { multipart: multipartOf(content, images) },
    );
}

async function createTweet(author: Account, content: string, images: UploadedImage[] = []): Promise<Tweet> {
    const response = await createTweetResponse(author, content, images);

    expect(response.status()).toBe(HTTP_STATUS.created);

    return response.json();
}

async function readTweet(reader: Account, tweetId: string): Promise<Tweet> {
    const response = await reader.api.get(API_PATHS.tweet(tweetId));

    expect(response.status()).toBe(HTTP_STATUS.ok);

    return response.json();
}

async function readTweetDetails(reader: Account, tweetId: string): Promise<TweetDetails> {
    const response = await reader.api.get(API_PATHS.tweetDetails(tweetId));

    expect(response.status()).toBe(HTTP_STATUS.ok);

    return response.json();
}

async function readTweetCount(reader: Account, authorId: string): Promise<number> {
    const response = await reader.api.get(`${API_PATHS.tweetCount}?authorId=${authorId}`);

    expect(response.status()).toBe(HTTP_STATUS.ok);

    const tweetCount: TweetCount = await response.json();

    return tweetCount.count;
}

async function editTweet(editor: Account, tweetId: string, content: string, headers: Record<string, string> = {}): Promise<APIResponse> {
    return editor.api.put(
        API_PATHS.tweet(tweetId),
        {
            data: { content },
            headers,
        },
    );
}

async function expectRefusal(response: APIResponse, status: number, message: string) {
    expect(response.status()).toBe(status);

    const errorBody: ErrorBody = await response.json();

    expect(errorBody.status).toBe(status);
    expect(errorBody.messages).toEqual([message]);
}

test.describe(
    'tweets',
    () => {
        test.describe(
            'edit and delete by the author',
            () => {
                test(
                    'should_change_the_content_and_the_update_time_and_keep_the_image_when_the_author_edits_the_tweet',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const created = await createTweet(ana, 'before the edit', [TINY_PNG]);

                        const response = await editTweet(ana, created.id, 'after the edit');

                        expect(response.status()).toBe(HTTP_STATUS.ok);

                        const edited: Tweet = await response.json();

                        expect(edited.id).toBe(created.id);
                        expect(edited.content).toBe('after the edit');
                        expect(edited.createdAt).toBe(created.createdAt);
                        expect(Date.parse(edited.updatedAt)).toBeGreaterThan(Date.parse(created.updatedAt));
                        expect(edited.images).toEqual(created.images);
                        expect((await readTweet(ana, created.id)).content).toBe('after the edit');
                        expect((await readTweetDetails(ana, created.id)).content).toBe('after the edit');
                    },
                );

                test(
                    'should_answer_400_and_keep_the_text_when_the_author_edits_it_to_blank_or_over_the_limit',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const created = await createTweet(ana, 'keep this text');

                        await expectRefusal(
                            await editTweet(ana, created.id, '   '),
                            HTTP_STATUS.badRequest,
                            'A tweet needs text or an image.',
                        );
                        await expectRefusal(
                            await editTweet(ana, created.id, 'a'.repeat(MAX_TWEET_CHARACTERS + 1)),
                            HTTP_STATUS.badRequest,
                            `A tweet can be at most ${MAX_TWEET_CHARACTERS} characters.`,
                        );

                        expect((await readTweet(ana, created.id)).content).toBe('keep this text');
                    },
                );

                test(
                    'should_remove_the_tweet_from_the_read_the_feed_and_the_count_when_the_author_deletes_it',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const created = await createTweet(ana, `to be deleted by ${ana.user.username}`);
                        await expectFeedToContain(ana, created.id);
                        expect(await readTweetCount(ana, created.authorId)).toBe(1);

                        const response = await ana.api.delete(API_PATHS.tweet(created.id));

                        expect(response.status()).toBe(HTTP_STATUS.noContent);
                        expect((await ana.api.get(API_PATHS.tweet(created.id))).status()).toBe(HTTP_STATUS.notFound);
                        expect(await readTweetCount(ana, created.authorId)).toBe(0);

                        await expect
                            .poll(
                                () => feedTweetIds(ana),
                                { timeout: FEED_POLL_TIMEOUT_MS },
                            )
                            .not.toContain(created.id);
                    },
                );

                test(
                    'should_answer_404_when_the_author_deletes_or_edits_the_tweet_again',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const created = await createTweet(ana, 'deleted twice');
                        await ana.api.delete(API_PATHS.tweet(created.id));

                        const secondRemoval = await ana.api.delete(API_PATHS.tweet(created.id));
                        const editAfterRemoval = await editTweet(ana, created.id, 'too late');

                        expect(secondRemoval.status()).toBe(HTTP_STATUS.notFound);
                        expect(editAfterRemoval.status()).toBe(HTTP_STATUS.notFound);
                    },
                );
            },
        );

        test.describe(
            'content length',
            () => {
                test(
                    'should_accept_the_tweet_when_the_content_has_exactly_the_maximum_characters',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const content = 'a'.repeat(MAX_TWEET_CHARACTERS);

                        const created = await createTweet(ana, content);

                        expect(created.content).toBe(content);
                    },
                );

                test(
                    'should_accept_the_tweet_when_the_maximum_characters_are_emoji_that_take_two_code_units_each',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const content = EMOJI.repeat(MAX_TWEET_CHARACTERS);

                        const created = await createTweet(ana, content);

                        expect(created.content).toBe(content);
                    },
                );

                test(
                    'should_answer_400_and_create_nothing_when_the_content_has_one_character_too_many',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const anaId = (await createTweet(ana, 'the first and only tweet')).authorId;

                        const response = await createTweetResponse(ana, 'a'.repeat(MAX_TWEET_CHARACTERS + 1));

                        await expectRefusal(
                            response,
                            HTTP_STATUS.badRequest,
                            `A tweet can be at most ${MAX_TWEET_CHARACTERS} characters.`,
                        );
                        expect(await readTweetCount(ana, anaId)).toBe(1);
                    },
                );

                test(
                    'should_answer_400_when_the_tweet_has_neither_text_nor_an_image',
                    async ({ createAccount }) => {
                        const ana = await createAccount();

                        const response = await createTweetResponse(ana, '   ');

                        await expectRefusal(
                            response,
                            HTTP_STATUS.badRequest,
                            'A tweet needs text or an image.',
                        );
                    },
                );
            },
        );

        test.describe(
            'images',
            () => {
                test(
                    'should_answer_415_and_create_nothing_when_a_text_file_is_declared_as_a_png',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const anaId = (await createTweet(ana, 'the first and only tweet')).authorId;

                        const response = await createTweetResponse(ana, 'with a fake image', [TEXT_DECLARED_AS_PNG]);

                        await expectRefusal(
                            response,
                            HTTP_STATUS.unsupportedMediaType,
                            'Unsupported image type.',
                        );
                        expect(await readTweetCount(ana, anaId)).toBe(1);
                    },
                );

                test(
                    'should_answer_400_when_the_uploaded_image_is_empty',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const emptyImage: UploadedImage = {
                            ...TINY_PNG,
                            name: 'empty.png',
                            buffer: Buffer.alloc(0),
                        };

                        const response = await createTweetResponse(ana, 'with an empty image', [emptyImage]);

                        await expectRefusal(
                            response,
                            HTTP_STATUS.badRequest,
                            'The uploaded file is empty.',
                        );
                    },
                );

                test(
                    'should_accept_the_image_when_it_is_exactly_the_maximum_size',
                    async ({ createAccount }) => {
                        const ana = await createAccount();

                        const created = await createTweet(ana, 'a big picture', [pngOfSize(MAX_IMAGE_BYTES)]);

                        expect(created.images).toHaveLength(1);
                        expect(created.images[0].sizeBytes).toBe(MAX_IMAGE_BYTES);
                        expect(created.images[0].contentType).toBe('image/png');
                    },
                );

                test(
                    'should_answer_413_when_the_image_is_one_byte_over_the_maximum_size',
                    async ({ createAccount }) => {
                        const ana = await createAccount();

                        const response = await createTweetResponse(ana, 'a too big picture', [pngOfSize(MAX_IMAGE_BYTES + 1)]);

                        await expectRefusal(
                            response,
                            HTTP_STATUS.contentTooLarge,
                            'The uploaded file is too large.',
                        );
                    },
                );

                test(
                    'should_store_all_images_when_the_tweet_has_the_maximum_number_of_them',
                    async ({ createAccount }) => {
                        const ana = await createAccount();

                        const created = await createTweet(ana, 'four pictures', severalPngs(MAX_TWEET_IMAGES));

                        expect(created.images).toHaveLength(MAX_TWEET_IMAGES);
                        expect(new Set(created.images.map((image) => image.id)).size).toBe(MAX_TWEET_IMAGES);
                    },
                );

                test(
                    'should_answer_400_and_create_nothing_when_the_tweet_has_one_image_too_many',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const anaId = (await createTweet(ana, 'the first and only tweet')).authorId;

                        const response = await createTweetResponse(ana, 'five pictures', severalPngs(MAX_TWEET_IMAGES + 1));

                        await expectRefusal(
                            response,
                            HTTP_STATUS.badRequest,
                            `A tweet can have at most ${MAX_TWEET_IMAGES} images.`,
                        );
                        expect(await readTweetCount(ana, anaId)).toBe(1);
                    },
                );
            },
        );

        test.describe(
            'authorization',
            () => {
                test(
                    'should_answer_403_and_leave_the_tweet_unchanged_when_bob_edits_or_deletes_anas_tweet',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const bob = await createAccount();
                        const created = await createTweet(ana, 'anas words');

                        const editResponse = await editTweet(bob, created.id, 'bobs words');
                        const removalResponse = await bob.api.delete(API_PATHS.tweet(created.id));

                        await expectRefusal(editResponse, HTTP_STATUS.forbidden, 'You can only edit your own tweets.');
                        await expectRefusal(removalResponse, HTTP_STATUS.forbidden, 'You can only delete your own tweets.');

                        const unchanged = await readTweet(ana, created.id);

                        expect(unchanged.content).toBe('anas words');
                        expect(unchanged.updatedAt).toBe(created.updatedAt);
                    },
                );

                test(
                    'should_answer_404_when_the_tweet_to_edit_or_delete_does_not_exist',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const unknownTweetId = randomUUID();

                        const editResponse = await editTweet(ana, unknownTweetId, 'nobody home');
                        const removalResponse = await ana.api.delete(API_PATHS.tweet(unknownTweetId));

                        await expectRefusal(editResponse, HTTP_STATUS.notFound, 'Tweet not found.');
                        await expectRefusal(removalResponse, HTTP_STATUS.notFound, 'Tweet not found.');
                    },
                );

                test(
                    'should_answer_401_on_create_edit_and_delete_when_the_caller_is_not_logged_in',
                    async ({ createAccount, playwright, baseURL }) => {
                        const ana = await createAccount();
                        const created = await createTweet(ana, 'protected from strangers');
                        const anonymous = await playwright.request.newContext({ baseURL });

                        try {
                            const createResponse = await anonymous.post(
                                API_PATHS.tweets,
                                { multipart: multipartOf('anonymous words', []) },
                            );
                            const editResponse = await anonymous.put(
                                API_PATHS.tweet(created.id),
                                { data: { content: 'anonymous edit' } },
                            );
                            const removalResponse = await anonymous.delete(API_PATHS.tweet(created.id));

                            await expectRefusal(createResponse, HTTP_STATUS.unauthorized, UNAUTHENTICATED_MESSAGE);
                            await expectRefusal(editResponse, HTTP_STATUS.unauthorized, UNAUTHENTICATED_MESSAGE);
                            await expectRefusal(removalResponse, HTTP_STATUS.unauthorized, UNAUTHENTICATED_MESSAGE);

                        } finally {
                            await anonymous.dispose();
                        }

                        expect((await readTweet(ana, created.id)).content).toBe('protected from strangers');
                    },
                );

                test(
                    'should_create_the_tweet_for_the_caller_when_a_forged_user_id_header_is_sent',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const bob = await createAccount();
                        const anaId = (await createTweet(ana, 'to learn anas id')).authorId;
                        const bobId = (await createTweet(bob, 'to learn bobs id')).authorId;

                        const response = await ana.api.post(
                            API_PATHS.tweets,
                            {
                                multipart: multipartOf('written by ana', []),
                                headers: { [USER_ID_HEADER]: bobId },
                            },
                        );

                        expect(response.status()).toBe(HTTP_STATUS.created);

                        const created: Tweet = await response.json();

                        expect(created.authorId).toBe(anaId);
                        expect(await readTweetCount(ana, anaId)).toBe(2);
                        expect(await readTweetCount(bob, bobId)).toBe(1);
                    },
                );

                test(
                    'should_answer_403_and_leave_the_tweet_unchanged_when_ana_edits_it_with_a_forged_user_id_header',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const bob = await createAccount();
                        const created = await createTweet(bob, 'bobs words');

                        const response = await editTweet(
                            ana,
                            created.id,
                            'anas forgery',
                            { [USER_ID_HEADER]: created.authorId },
                        );

                        await expectRefusal(response, HTTP_STATUS.forbidden, 'You can only edit your own tweets.');
                        expect((await readTweet(bob, created.id)).content).toBe('bobs words');
                    },
                );

                test(
                    'should_answer_403_and_keep_the_tweet_when_ana_deletes_it_with_a_forged_user_id_header',
                    async ({ createAccount }) => {
                        const ana = await createAccount();
                        const bob = await createAccount();
                        const tweetId = await postTweet(bob, 'bobs words');
                        const bobId = (await readTweet(bob, tweetId)).authorId;

                        const response = await ana.api.delete(
                            API_PATHS.tweet(tweetId),
                            { headers: { [USER_ID_HEADER]: bobId } },
                        );

                        await expectRefusal(response, HTTP_STATUS.forbidden, 'You can only delete your own tweets.');
                        expect((await readTweet(bob, tweetId)).content).toBe('bobs words');
                    },
                );
            },
        );
    },
);

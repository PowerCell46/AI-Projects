import { expect } from '@playwright/test';
import { API_PATHS, deleteTweet, HTTP_STATUS, postTweet, test, TEST_TIMEOUT_MS, type Account } from './fixtures';

const BIRTHDATE = '1990-05-17';

const PAGE_SIZE = 20;

const TWEETS_TO_POST = 25;

const MAX_BIO_CODE_POINTS = 160;

const MAX_LOCATION_CODE_POINTS = 60;

const EMOJI = '\u{1F600}';

const UNKNOWN_USERNAME = 'nobody_here_e2e';

const UNKNOWN_AUTHOR_ID = '00000000-0000-4000-8000-000000000000';

interface Profile {
    id: string;
    bio: string | null;
    location: string | null;
    birthdate: string | null;
}

interface ProfileChange {
    bio?: string | null;
    location?: string | null;
    birthdate?: string | null;
}

interface AuthorTweet {
    id: string;
    createdAt: string;
    likes: number;
    likedByMe: boolean;
}

interface AuthorTweetsPage {
    items: AuthorTweet[];
    nextCursor: string | null;
}

interface TweetCount {
    count: number;
}

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function patchProfile(account: Account, change: ProfileChange) {
    return account.api.patch(
        API_PATHS.currentUser,
        { data: change },
    );
}

async function readProfile(reader: Account, username: string): Promise<Profile> {
    const response = await reader.api.get(API_PATHS.user(username));

    expect(response.ok()).toBe(true);

    return response.json();
}

async function readAuthorTweets(reader: Account, authorId: string, cursor?: string): Promise<AuthorTweetsPage> {
    const query = cursor === undefined ? '' : `?cursor=${encodeURIComponent(cursor)}`;
    const response = await reader.api.get(`${API_PATHS.authorTweets(authorId)}${query}`);

    expect(response.ok()).toBe(true);

    return response.json();
}

async function countTweets(reader: Account, authorId: string): Promise<number> {
    const response = await reader.api.get(`${API_PATHS.tweetCount}?authorId=${authorId}`);

    expect(response.status()).toBe(HTTP_STATUS.ok);

    const tweetCount: TweetCount = await response.json();

    return tweetCount.count;
}

test.describe(
    'profile api',
    () => {
        test(
            'should_keep_the_bio_and_the_birthdate_when_only_the_location_is_patched',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const fullPatchResponse = await patchProfile(ana, {
                    bio: 'hello',
                    location: 'Sofia',
                    birthdate: BIRTHDATE,
                });
                expect(fullPatchResponse.status()).toBe(HTTP_STATUS.ok);

                const response = await patchProfile(ana, { location: 'Plovdiv' });

                expect(response.status()).toBe(HTTP_STATUS.ok);

                const profile = await readProfile(ana, ana.user.username);

                expect(profile.location).toBe('Plovdiv');
                expect(profile.bio).toBe('hello');
                expect(profile.birthdate).toBe(BIRTHDATE);
            },
        );

        test(
            'should_clear_a_field_with_an_empty_string_and_keep_it_with_null_when_the_profile_is_patched',
            async ({ createAccount }) => {
                const ana = await createAccount();
                await patchProfile(ana, {
                    bio: 'hello',
                    location: 'Sofia',
                });

                await patchProfile(ana, {
                    bio: '',
                    location: null,
                });

                const profile = await readProfile(ana, ana.user.username);

                expect(profile.bio).toBeNull();
                expect(profile.location).toBe('Sofia');
            },
        );

        test(
            'should_accept_160_emoji_in_the_bio_and_refuse_161_when_the_profile_is_patched',
            async ({ createAccount }) => {
                const ana = await createAccount();

                const acceptedResponse = await patchProfile(ana, { bio: EMOJI.repeat(MAX_BIO_CODE_POINTS) });
                const refusedResponse = await patchProfile(ana, { bio: EMOJI.repeat(MAX_BIO_CODE_POINTS + 1) });

                expect(acceptedResponse.status()).toBe(HTTP_STATUS.ok);
                expect(refusedResponse.status()).toBe(HTTP_STATUS.badRequest);
                expect((await readProfile(ana, ana.user.username)).bio).toBe(EMOJI.repeat(MAX_BIO_CODE_POINTS));
            },
        );

        test(
            'should_refuse_a_61_character_location_and_leave_the_old_one_when_the_profile_is_patched',
            async ({ createAccount }) => {
                const ana = await createAccount();
                await patchProfile(ana, { location: 'a'.repeat(MAX_LOCATION_CODE_POINTS) });

                const refusedResponse = await patchProfile(ana, { location: 'b'.repeat(MAX_LOCATION_CODE_POINTS + 1) });

                expect(refusedResponse.status()).toBe(HTTP_STATUS.badRequest);
                expect((await readProfile(ana, ana.user.username)).location).toBe('a'.repeat(MAX_LOCATION_CODE_POINTS));
            },
        );

        test(
            'should_refuse_a_put_to_the_profile_with_405',
            async ({ createAccount }) => {
                const ana = await createAccount();

                const response = await ana.api.put(
                    API_PATHS.currentUser,
                    { data: { bio: 'x' } },
                );

                expect(response.status()).toBe(HTTP_STATUS.methodNotAllowed);
            },
        );

        test(
            'should_page_the_authors_tweets_newest_first_and_end_without_a_cursor_when_there_are_25',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const postedIds: string[] = [];

                for (let index = 0; index < TWEETS_TO_POST; index++) {
                    postedIds.push(await postTweet(ana, `tweet ${index} from ${ana.user.username}`));
                }

                const authorId = (await readProfile(bob, ana.user.username)).id;
                const firstPage = await readAuthorTweets(bob, authorId);

                expect(firstPage.items).toHaveLength(PAGE_SIZE);
                expect(firstPage.nextCursor).not.toBeNull();

                const secondPage = await readAuthorTweets(bob, authorId, firstPage.nextCursor ?? '');
                const fetchedIds = [...firstPage.items, ...secondPage.items].map((tweet) => tweet.id);
                const createdTimes = [...firstPage.items, ...secondPage.items].map((tweet) => Date.parse(tweet.createdAt));

                expect(secondPage.items).toHaveLength(TWEETS_TO_POST - PAGE_SIZE);
                expect(secondPage.nextCursor).toBeNull();
                expect([...fetchedIds].sort()).toEqual([...postedIds].sort());
                expect(createdTimes).toEqual([...createdTimes].sort((first, second) => second - first));
            },
        );

        test(
            'should_mark_liked_by_me_only_for_the_viewer_who_liked_when_the_authors_tweets_are_read',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const cleo = await createAccount();
                const tweetId = await postTweet(ana, `like me from ${ana.user.username}`);
                const authorId = (await readProfile(bob, ana.user.username)).id;

                const likeResponse = await bob.api.put(API_PATHS.like(tweetId));

                expect(likeResponse.status()).toBe(HTTP_STATUS.noContent);

                const forBob = (await readAuthorTweets(bob, authorId)).items[0];
                const forCleo = (await readAuthorTweets(cleo, authorId)).items[0];

                expect(forBob.likedByMe).toBe(true);
                expect(forBob.likes).toBe(1);
                expect(forCleo.likedByMe).toBe(false);
                expect(forCleo.likes).toBe(1);
            },
        );

        test(
            'should_count_the_authors_tweets_after_a_create_and_after_a_delete',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const authorId = (await readProfile(bob, ana.user.username)).id;

                expect(await countTweets(bob, authorId)).toBe(0);

                const firstTweetId = await postTweet(ana, `one from ${ana.user.username}`);
                await postTweet(ana, `two from ${ana.user.username}`);

                expect(await countTweets(bob, authorId)).toBe(2);

                await deleteTweet(ana, firstTweetId);

                expect(await countTweets(bob, authorId)).toBe(1);
            },
        );

        test(
            'should_answer_404_for_an_unknown_user_and_an_unknown_author_when_a_profile_is_opened',
            async ({ createAccount }) => {
                const ana = await createAccount();

                const profileResponse = await ana.api.get(API_PATHS.user(UNKNOWN_USERNAME));
                const tweetsResponse = await ana.api.get(API_PATHS.authorTweets(UNKNOWN_AUTHOR_ID));

                expect(profileResponse.status()).toBe(HTTP_STATUS.notFound);
                expect(tweetsResponse.status()).toBe(HTTP_STATUS.notFound);
            },
        );
    },
);

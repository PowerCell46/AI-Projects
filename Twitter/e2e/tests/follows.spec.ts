import { expect, type APIResponse } from '@playwright/test';
import { API_PATHS, follow, HTTP_STATUS, test, TEST_TIMEOUT_MS, type Account } from './fixtures';

const SELF_FOLLOW_MESSAGE = 'You cannot follow yourself.';

const USER_NOT_FOUND_MESSAGE = 'User not found.';

const INVALID_CURSOR_MESSAGE = 'Invalid cursor.';

const INVALID_PAGE_SIZE_MESSAGE = 'Page size must be between 1 and 100.';

const UNAUTHENTICATED_MESSAGE = 'Authentication is required.';

const UNKNOWN_USERNAME = 'nobody_here_e2e';

const ONE_PER_PAGE = 1;

interface Profile {
    followersCount: number;
    followingCount: number;
    followedByMe: boolean;
}

interface Member {
    username: string;
    followersCount: number;
    followedByMe: boolean;
}

interface MemberPage {
    items: Member[];
    nextCursor: string | null;
}

interface ErrorBody {
    status: number;
    messages: string[];
}

interface FollowCounts {
    followers: number;
    following: number;
}

test.describe.configure({ timeout: TEST_TIMEOUT_MS });

async function readProfile(reader: Account, username: string): Promise<Profile> {
    const response = await reader.api.get(API_PATHS.user(username));

    expect(response.status()).toBe(HTTP_STATUS.ok);

    return response.json();
}

async function countsOf(reader: Account, subject: Account): Promise<FollowCounts> {
    const profile = await readProfile(reader, subject.user.username);

    return {
        followers: profile.followersCount,
        following: profile.followingCount,
    };
}

async function readMembers(reader: Account, path: string): Promise<MemberPage> {
    const response = await reader.api.get(path);

    expect(response.status()).toBe(HTTP_STATUS.ok);

    return response.json();
}

function usernamesOf(page: MemberPage): string[] {
    return page.items.map((member) => member.username);
}

async function expectRefusal(response: APIResponse, status: number, message: string) {
    expect(response.status()).toBe(status);

    const errorBody: ErrorBody = await response.json();

    expect(errorBody.status).toBe(status);
    expect(errorBody.messages).toEqual([message]);
}

test.describe(
    'follows',
    () => {
        test(
            'should_answer_400_and_change_nothing_when_a_user_follows_or_unfollows_themselves',
            async ({ createAccount }) => {
                const ana = await createAccount();

                await expectRefusal(
                    await ana.api.put(API_PATHS.follow(ana.user.username)),
                    HTTP_STATUS.badRequest,
                    SELF_FOLLOW_MESSAGE,
                );
                await expectRefusal(
                    await ana.api.delete(API_PATHS.follow(ana.user.username)),
                    HTTP_STATUS.badRequest,
                    SELF_FOLLOW_MESSAGE,
                );

                expect(await countsOf(ana, ana)).toEqual({
                    followers: 0,
                    following: 0,
                });
            },
        );

        test(
            'should_answer_404_when_the_user_to_follow_or_unfollow_does_not_exist',
            async ({ createAccount }) => {
                const ana = await createAccount();

                await expectRefusal(
                    await ana.api.put(API_PATHS.follow(UNKNOWN_USERNAME)),
                    HTTP_STATUS.notFound,
                    USER_NOT_FOUND_MESSAGE,
                );
                await expectRefusal(
                    await ana.api.delete(API_PATHS.follow(UNKNOWN_USERNAME)),
                    HTTP_STATUS.notFound,
                    USER_NOT_FOUND_MESSAGE,
                );
            },
        );

        test(
            'should_answer_204_both_times_and_count_one_follower_when_the_same_user_is_followed_twice',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();

                const first = await ana.api.put(API_PATHS.follow(bob.user.username));
                const second = await ana.api.put(API_PATHS.follow(bob.user.username));

                expect(first.status()).toBe(HTTP_STATUS.noContent);
                expect(second.status()).toBe(HTTP_STATUS.noContent);
                expect(await countsOf(ana, bob)).toEqual({
                    followers: 1,
                    following: 0,
                });
                expect(await countsOf(ana, ana)).toEqual({
                    followers: 0,
                    following: 1,
                });
                expect(usernamesOf(await readMembers(ana, API_PATHS.followers(bob.user.username)))).toEqual([ana.user.username]);
            },
        );

        test(
            'should_answer_204_both_times_and_count_zero_when_the_same_user_is_unfollowed_twice',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                await follow(ana, bob);

                const first = await ana.api.delete(API_PATHS.follow(bob.user.username));
                const second = await ana.api.delete(API_PATHS.follow(bob.user.username));

                expect(first.status()).toBe(HTTP_STATUS.noContent);
                expect(second.status()).toBe(HTTP_STATUS.noContent);
                expect(await countsOf(ana, bob)).toEqual({
                    followers: 0,
                    following: 0,
                });
                expect(await countsOf(ana, ana)).toEqual({
                    followers: 0,
                    following: 0,
                });
                expect((await readMembers(ana, API_PATHS.followers(bob.user.username))).items).toEqual([]);
            },
        );

        test(
            'should_answer_204_and_keep_the_counts_at_zero_when_someone_never_followed_is_unfollowed',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();

                const response = await ana.api.delete(API_PATHS.follow(bob.user.username));

                expect(response.status()).toBe(HTTP_STATUS.noContent);
                expect(await countsOf(ana, bob)).toEqual({
                    followers: 0,
                    following: 0,
                });
            },
        );

        test(
            'should_follow_when_the_username_is_written_in_another_case',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();

                const response = await ana.api.put(API_PATHS.follow(bob.user.username.toUpperCase()));

                expect(response.status()).toBe(HTTP_STATUS.noContent);
                expect(usernamesOf(await readMembers(ana, API_PATHS.following(ana.user.username)))).toEqual([bob.user.username]);
            },
        );

        test(
            'should_list_followers_and_following_newest_first_with_followed_by_me_for_the_reader',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const cat = await createAccount();
                await follow(ana, bob);
                await follow(ana, cat);
                await follow(cat, bob);

                const anaFollowing = await readMembers(ana, API_PATHS.following(ana.user.username));
                const bobFollowers = await readMembers(ana, API_PATHS.followers(bob.user.username));
                const catFollowers = await readMembers(bob, API_PATHS.followers(cat.user.username));

                expect(anaFollowing.items).toEqual([
                    expect.objectContaining({
                        username: cat.user.username,
                        followersCount: 1,
                        followedByMe: true,
                    }),
                    expect.objectContaining({
                        username: bob.user.username,
                        followersCount: 2,
                        followedByMe: true,
                    }),
                ]);
                expect(bobFollowers.items).toEqual([
                    expect.objectContaining({
                        username: cat.user.username,
                        followersCount: 1,
                        followedByMe: true,
                    }),
                    expect.objectContaining({
                        username: ana.user.username,
                        followersCount: 0,
                        followedByMe: false,
                    }),
                ]);
                expect(catFollowers.items).toEqual([
                    expect.objectContaining({
                        username: ana.user.username,
                        followedByMe: false,
                    }),
                ]);
                expect(await countsOf(ana, bob)).toEqual({
                    followers: 2,
                    following: 0,
                });
                expect(await countsOf(ana, ana)).toEqual({
                    followers: 0,
                    following: 2,
                });
                expect(await countsOf(ana, cat)).toEqual({
                    followers: 1,
                    following: 1,
                });
            },
        );

        test(
            'should_drop_the_follower_from_the_list_and_the_counts_when_the_follow_is_undone',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                await follow(ana, bob);

                await ana.api.delete(API_PATHS.follow(bob.user.username));

                expect((await readMembers(bob, API_PATHS.followers(bob.user.username))).items).toEqual([]);
                expect((await readMembers(ana, API_PATHS.following(ana.user.username))).items).toEqual([]);
            },
        );

        test(
            'should_page_a_list_with_a_cursor_and_end_without_one_when_size_is_1',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const bob = await createAccount();
                const cat = await createAccount();
                await follow(bob, ana);
                await follow(cat, ana);
                const path = `${API_PATHS.followers(ana.user.username)}?size=${ONE_PER_PAGE}`;

                const firstPage = await readMembers(ana, path);

                expect(usernamesOf(firstPage)).toEqual([cat.user.username]);
                expect(firstPage.nextCursor).not.toBeNull();

                const secondPage = await readMembers(ana, `${path}&cursor=${encodeURIComponent(firstPage.nextCursor ?? '')}`);

                expect(usernamesOf(secondPage)).toEqual([bob.user.username]);
                expect(secondPage.nextCursor).toBeNull();
            },
        );

        test(
            'should_answer_400_when_the_cursor_or_the_page_size_is_invalid',
            async ({ createAccount }) => {
                const ana = await createAccount();
                const path = API_PATHS.followers(ana.user.username);

                await expectRefusal(
                    await ana.api.get(`${path}?cursor=not-a-cursor`),
                    HTTP_STATUS.badRequest,
                    INVALID_CURSOR_MESSAGE,
                );
                await expectRefusal(
                    await ana.api.get(`${path}?size=0`),
                    HTTP_STATUS.badRequest,
                    INVALID_PAGE_SIZE_MESSAGE,
                );
                await expectRefusal(
                    await ana.api.get(`${path}?size=101`),
                    HTTP_STATUS.badRequest,
                    INVALID_PAGE_SIZE_MESSAGE,
                );
            },
        );

        test(
            'should_answer_404_when_the_list_owner_does_not_exist',
            async ({ createAccount }) => {
                const ana = await createAccount();

                await expectRefusal(
                    await ana.api.get(API_PATHS.followers(UNKNOWN_USERNAME)),
                    HTTP_STATUS.notFound,
                    USER_NOT_FOUND_MESSAGE,
                );
                await expectRefusal(
                    await ana.api.get(API_PATHS.following(UNKNOWN_USERNAME)),
                    HTTP_STATUS.notFound,
                    USER_NOT_FOUND_MESSAGE,
                );
            },
        );

        test(
            'should_answer_401_on_follow_unfollow_and_both_lists_when_the_caller_is_not_logged_in',
            async ({ createAccount, request }) => {
                const ana = await createAccount();
                const username = ana.user.username;

                await expectRefusal(await request.put(API_PATHS.follow(username)), HTTP_STATUS.unauthorized, UNAUTHENTICATED_MESSAGE);
                await expectRefusal(await request.delete(API_PATHS.follow(username)), HTTP_STATUS.unauthorized, UNAUTHENTICATED_MESSAGE);
                await expectRefusal(await request.get(API_PATHS.followers(username)), HTTP_STATUS.unauthorized, UNAUTHENTICATED_MESSAGE);
                await expectRefusal(await request.get(API_PATHS.following(username)), HTTP_STATUS.unauthorized, UNAUTHENTICATED_MESSAGE);
            },
        );
    },
);

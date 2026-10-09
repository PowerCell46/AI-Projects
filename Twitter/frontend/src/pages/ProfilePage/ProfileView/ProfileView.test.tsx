import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fetchAuthorTweets } from '../../../api/authorTweets';
import type { TweetItem } from '../../../api/tweetPage';
import { followUser, unfollowUser } from '../../../api/users';
import { useAuth } from '../../../contexts/AuthContext';
import { PostUpdatesProvider } from '../../../contexts/PostUpdatesContext';
import { advance } from '../../../test/stepFlowHelpers';
import { tweetItem } from '../../../test/tweetItem';
import { userProfile } from '../../../test/userProfile';
import ProfileView from './ProfileView';


vi.mock('../../../api/authorTweets', () => ({
    fetchAuthorTweets: vi.fn(),
}));

vi.mock('../../../api/users', () => ({
    followUser: vi.fn(),
    unfollowUser: vi.fn(),
}));

vi.mock('../../../contexts/AuthContext', () => ({
    useAuth: vi.fn(),
}));

const PROFILE = userProfile({
    id: 'author-1',
    username: 'ana_b',
    followersCount: 1204,
    followingCount: 31,
});

const FAILURE_RESET_MS = 3000;

const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

function viewTree(
    changes: Partial<ReturnType<typeof userProfile>>,
    onFollowChanged: () => void,
    ownPosts: TweetItem[],
) {
    return (
        <MemoryRouter>
            <PostUpdatesProvider>
                <Routes>
                    <Route
                        element={<Outlet context={{
                            ownPosts,
                            followChangeCount: 0,
                            onFollowChanged,
                            onProfilePictureChanged: vi.fn(),
                        }} />}
                    >
                        <Route
                            path="*"
                            element={(
                                <ProfileView
                                    profile={{ ...PROFILE, ...changes }}
                                    serverTweetCount={25}
                                    onProfileSaved={vi.fn()}
                                />
                            )}
                        />
                    </Route>
                </Routes>
            </PostUpdatesProvider>
        </MemoryRouter>
    );
}

function renderView(
    changes: Partial<ReturnType<typeof userProfile>> = {},
    onFollowChanged = vi.fn(),
    ownPosts: TweetItem[] = [],
) {
    const { rerender } = render(viewTree(changes, onFollowChanged, ownPosts));

    return {
        onFollowChanged,
        showOwnPosts: (newOwnPosts: TweetItem[]) => rerender(viewTree(changes, onFollowChanged, newOwnPosts)),
    };
}

function ownPost(id: string, content: string): TweetItem {
    return tweetItem({
        id,
        content,
        createdAt: '2099-01-01T00:00:00Z',
        updatedAt: '2099-01-01T00:00:00Z',
        author: {
            id: 'user-1',
            username: 'ana_b',
            profilePictureUrl: null,
        },
    });
}

function tweetsHeading(): HTMLElement {
    return screen.getByRole('heading', { level: 2 });
}

function followButton(): HTMLElement {
    return screen.getByRole('button', { name: 'Follow ana_b' });
}

function followersText(): string {
    return screen
        .getAllByRole('listitem')
        .map((item) => item.textContent ?? '')
        .find((text) => text.includes('FOLLOWER')) ?? '';
}

function signInAs(username: string) {
    vi.mocked(useAuth)
        .mockReturnValue({
            status: 'authenticated',
            user: {
                id: 'user-1',
                username,
                email: 'peter@example.com',
            },
            isSigningOut: false,
            hasSignedOut: false,
            signIn: vi.fn(),
            signOut: vi.fn(),
        });
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    signInAs('peter_g');
    vi.mocked(fetchAuthorTweets)
        .mockReset()
        .mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    vi.mocked(followUser)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(unfollowUser)
        .mockReset()
        .mockResolvedValue(undefined);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the follow button on a profile', () => {
    it('should_show_follow_on_the_profile_of_someone_else', async () => {
        renderView();
        await advance(1);

        expect(followButton().textContent).toBe('FOLLOW');
        expect(followButton().getAttribute('aria-pressed')).toBe('false');
    });

    it('should_show_following_when_the_reader_already_follows_the_person', async () => {
        renderView({ followedByMe: true });
        await advance(1);

        expect(followButton().textContent).toBe('FOLLOWING');
        expect(followButton().getAttribute('aria-pressed')).toBe('true');
    });

    it.each(['ana_b', 'ANA_B', 'Ana_B'])('should_show_no_follow_button_on_the_own_profile_for_%s', async (username) => {
        signInAs(username);

        renderView();
        await advance(1);

        expect(screen.queryByRole('button', { name: /^Follow /u })).toBeNull();
    });

    it('should_raise_the_followers_count_by_one_and_ask_the_server_when_following', async () => {
        renderView();
        await advance(1);

        await user.click(followButton());
        await advance(1);

        expect(followUser).toHaveBeenCalledExactlyOnceWith('ana_b');
        expect(followButton().textContent).toBe('FOLLOWING');
        expect(followersText()).toBe('1,205 FOLLOWERS');
    });

    it('should_tell_the_shell_the_follow_changed_when_the_request_has_gone_through', async () => {
        const { onFollowChanged } = renderView();
        await advance(1);

        await user.click(followButton());
        await advance(1);

        expect(onFollowChanged).toHaveBeenCalledOnce();
    });

    it('should_arm_on_the_first_tap_and_unfollow_with_the_count_one_lower_on_the_second', async () => {
        const { onFollowChanged } = renderView({ followedByMe: true });
        await advance(1);

        await user.click(followButton());

        expect(followButton().textContent).toBe('UNFOLLOW?');
        expect(unfollowUser).not.toHaveBeenCalled();

        await user.click(followButton());
        await advance(1);

        expect(unfollowUser).toHaveBeenCalledExactlyOnceWith('ana_b');
        expect(followButton().textContent).toBe('FOLLOW');
        expect(followersText()).toBe('1,203 FOLLOWERS');
        expect(onFollowChanged).toHaveBeenCalledOnce();
    });

    it('should_put_the_count_back_and_show_try_again_when_the_follow_fails', async () => {
        vi.mocked(followUser)
            .mockRejectedValueOnce(new Error('The request failed.'));
        const { onFollowChanged } = renderView();
        await advance(1);

        await user.click(followButton());
        await advance(1);

        expect(followButton().textContent).toBe('TRY AGAIN');
        expect(followersText()).toBe('1,204 FOLLOWERS');
        expect(onFollowChanged).not.toHaveBeenCalled();

        await advance(FAILURE_RESET_MS);

        expect(followButton().textContent).toBe('FOLLOW');
    });
});

describe('the reader\'s own posts on their own profile', () => {
    beforeEach(() => {
        signInAs('ana_b');
    });

    it('should_show_a_post_published_while_the_page_is_open_at_the_top_and_raise_the_count_by_one', async () => {
        const { showOwnPosts } = renderView();
        await advance(1);

        showOwnPosts([ownPost('new-1', 'Fresh from the profile')]);
        await advance(1);

        expect(screen.getByText('Fresh from the profile')).toBeTruthy();
        expect(tweetsHeading().textContent).toBe('TWEETS · 26');
    });

    it('should_raise_the_count_by_one_for_each_post_published_while_the_page_is_open', async () => {
        const { showOwnPosts } = renderView();
        await advance(1);

        showOwnPosts([ownPost('new-2', 'Second'), ownPost('new-1', 'First')]);
        await advance(1);

        expect(tweetsHeading().textContent).toBe('TWEETS · 27');
    });

    it('should_not_count_a_post_published_before_the_page_opened_because_the_server_count_has_it', async () => {
        renderView({}, vi.fn(), [ownPost('old-1', 'Published earlier')]);
        await advance(1);

        expect(tweetsHeading().textContent).toBe('TWEETS · 25');
    });

    it('should_show_the_raised_count_in_the_stats_too', async () => {
        const { showOwnPosts } = renderView();
        await advance(1);

        showOwnPosts([ownPost('new-1', 'Fresh')]);
        await advance(1);

        expect(screen.getByText('26')).toBeTruthy();
    });
});

describe('the reader\'s own posts on someone else\'s profile', () => {
    it('should_show_no_own_post_and_keep_the_server_count', async () => {
        const { showOwnPosts } = renderView();
        await advance(1);

        showOwnPosts([ownPost('new-1', 'Not for this page')]);
        await advance(1);

        expect(screen.queryByText('Not for this page')).toBeNull();
        expect(tweetsHeading().textContent).toBe('TWEETS · 25');
    });
});

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../../src/api/accounts/auth';
import { fetchFeed } from '../../../src/api/tweets/feed';
import { fetchPeople, fetchUserProfile, followUser } from '../../../src/api/accounts/users';
import { fetchAuthorTweets } from '../../../src/api/tweets/authorTweets';
import { reportViews } from '../../../src/api/tweets/views';
import { ROUTES } from '../../../src/routes';
import { intersect } from '../../support/intersectionObserver';
import { onlyPostItem } from '../../support/postListHelpers';
import { renderApp } from '../../support/renderApp';
import { advance } from '../../support/stepFlowHelpers';
import { userProfile } from '../../support/userProfile';


// The first of BACKFILL_DELAYS_MS in useBackfillReloadKey.ts.
const BACKFILL_DELAY_MS = 4000;

vi.mock('../../../src/api/accounts/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../../src/api/accounts/auth')>(),
    me: vi.fn(),
}));

vi.mock('../../../src/api/tweets/feed', () => ({
    fetchFeed: vi.fn(),
}));

vi.mock('../../../src/api/accounts/users', () => ({
    fetchUserProfile: vi.fn(),
    fetchPeople: vi.fn(),
    followUser: vi.fn(),
    unfollowUser: vi.fn(),
}));

vi.mock('../../../src/api/tweets/authorTweets', () => ({
    fetchAuthorTweets: vi.fn(),
}));

vi.mock('../../../src/api/tweets/views', () => ({
    reportViews: vi.fn(),
}));

vi.mock('../../../src/api/tweets/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../../src/api/tweets/savedTweets', () => ({
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

const POST = {
    id: 'tweet-1',
    views: 0,
    savedByMe: true,
    likes: 0,
    likedByMe: false,
    replyCount: 0,
    content: 'a post from the feed',
    createdAt: '2026-10-04T11:55:00.000Z',
    updatedAt: '2026-10-04T11:55:00.000Z',
    author: {
        id: 'author-1',
        username: 'ana',
        profilePictureUrl: null,
    },
    images: [],
};

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
    vi.mocked(fetchUserProfile).mockResolvedValue(userProfile({
        id: 'user-1',
        username: 'peter_g',
        profilePictureUrl: null,
    }));
    vi.mocked(fetchFeed).mockReset();
    vi.mocked(fetchPeople)
        .mockReset()
        .mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    vi.mocked(fetchAuthorTweets)
        .mockReset()
        .mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    vi.mocked(reportViews)
        .mockReset()
        .mockResolvedValue(undefined);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the feed page', () => {
    it('should_show_the_posts_of_the_feed_inside_main', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        const main = within(screen.getByRole('main'));
        expect(main.getByRole('heading', { name: 'Feed' })).toBeTruthy();
        expect(main.getByText('a post from the feed')).toBeTruthy();
    });

    it('should_read_the_feed_and_not_the_saved_list', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        expect(fetchFeed).toHaveBeenCalledExactlyOnceWith({
            cursor: null,
            size: 20,
        });
    });

    it('should_end_with_end_of_feed_when_the_cursor_is_exhausted', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        expect(screen.getByText('END OF FEED')).toBeTruthy();
    });

    it('should_tell_a_reader_with_an_empty_feed_to_follow_someone', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        expect(screen.getByText('NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS')).toBeTruthy();
    });

    it('should_say_signal_lost_with_a_try_again_button_when_the_feed_cannot_be_read', async () => {
        vi.mocked(fetchFeed).mockRejectedValue(new Error('The request failed.'));

        await renderApp(ROUTES.feed);

        expect(screen.getByText('SIGNAL LOST')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'TRY AGAIN' })).toBeTruthy();
    });

    it('should_start_a_saved_post_with_a_filled_bookmark', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        expect(screen.getByRole('button', { name: /^save/i }).getAttribute('aria-pressed')).toBe('true');
    });
});

describe('the empty feed panel', () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    function person(username: string, followedByMe = false) {
        return {
            id: `id-${username}`,
            username,
            bio: null,
            followersCount: 0,
            followedByMe,
            profilePictureUrl: null,
        };
    }

    beforeEach(() => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    });

    it('should_show_the_heading_and_the_follow_text_when_the_feed_is_empty', async () => {
        await renderApp(ROUTES.feed);

        expect(screen.getByRole('heading', { name: 'THE SURFACE IS QUIET' })).toBeTruthy();
        expect(screen.getByText('NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS')).toBeTruthy();
    });

    it('should_suggest_the_first_three_people_the_reader_does_not_follow', async () => {
        vi.mocked(fetchPeople).mockResolvedValue({
            items: [person('ana', true), person('bob'), person('cy'), person('di'), person('ed')],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        const panel = within(screen.getByRole('region', { name: 'THE SURFACE IS QUIET' }));
        expect(panel.getAllByRole('button', { name: /^Follow / }).map((button) => button.getAttribute('aria-label'))).toEqual([
            'Follow bob',
            'Follow cy',
            'Follow di',
        ]);
    });

    it('should_keep_a_followed_suggestion_in_the_panel', async () => {
        vi.mocked(fetchPeople).mockResolvedValue({
            items: [person('bob')],
            nextCursor: null,
        });
        vi.mocked(followUser).mockResolvedValue(undefined);
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('button', { name: 'Follow bob' }));
        await advance(1);

        expect(screen.getByRole('link', { name: 'bob' })).toBeTruthy();
        expect(screen.getByRole('button', { name: 'Follow bob' }).getAttribute('aria-pressed')).toBe('true');
    });

    it('should_show_the_posts_of_a_followed_suggestion_at_once_without_waiting_for_the_back_fill', async () => {
        vi.mocked(fetchPeople).mockResolvedValue({
            items: [person('bob')],
            nextCursor: null,
        });
        vi.mocked(followUser).mockResolvedValue(undefined);
        vi.mocked(fetchAuthorTweets).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('button', { name: 'Follow bob' }));
        await advance(1);
        await advance(1);

        expect(fetchAuthorTweets).toHaveBeenCalledWith('id-bob', expect.objectContaining({ cursor: null }));
        expect(screen.getByText('a post from the feed')).toBeTruthy();
        expect(screen.queryByRole('heading', { name: 'THE SURFACE IS QUIET' })).toBeNull();
    });

    it('should_keep_the_panel_when_the_followed_person_has_no_posts_yet', async () => {
        vi.mocked(fetchPeople).mockResolvedValue({
            items: [person('bob')],
            nextCursor: null,
        });
        vi.mocked(followUser).mockResolvedValue(undefined);
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('button', { name: 'Follow bob' }));
        await advance(1);
        await advance(1);

        expect(screen.getByRole('heading', { name: 'THE SURFACE IS QUIET' })).toBeTruthy();
    });

    it('should_keep_the_panel_when_the_posts_of_the_followed_person_cannot_be_read', async () => {
        vi.mocked(fetchPeople).mockResolvedValue({
            items: [person('bob')],
            nextCursor: null,
        });
        vi.mocked(followUser).mockResolvedValue(undefined);
        vi.mocked(fetchAuthorTweets).mockRejectedValue(new Error('The request failed.'));
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('button', { name: 'Follow bob' }));
        await advance(1);
        await advance(1);

        expect(screen.getByRole('heading', { name: 'THE SURFACE IS QUIET' })).toBeTruthy();
    });

    it('should_point_to_the_people_tab_with_a_link', async () => {
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('link', { name: 'PEOPLE TAB' }));

        expect(window.location.pathname).toBe(ROUTES.people);
        expect(screen.getByRole('tab', { name: 'PEOPLE' }).getAttribute('aria-selected')).toBe('true');
    });

    it('should_still_show_the_panel_without_suggestions_when_the_people_cannot_be_read', async () => {
        vi.mocked(fetchPeople).mockRejectedValue(new Error('The request failed.'));

        await renderApp(ROUTES.feed);

        expect(screen.getByRole('heading', { name: 'THE SURFACE IS QUIET' })).toBeTruthy();
        expect(screen.getByRole('link', { name: 'PEOPLE TAB' })).toBeTruthy();
    });

    it('should_read_the_feed_again_after_a_follow_so_the_back_fill_replaces_the_panel', async () => {
        vi.mocked(fetchPeople).mockResolvedValue({
            items: [person('bob')],
            nextCursor: null,
        });
        vi.mocked(followUser).mockResolvedValue(undefined);
        await renderApp(ROUTES.feed);
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });

        await user.click(screen.getByRole('button', { name: 'Follow bob' }));
        // Two stages: the follow's state update lands when the first act ends, and only then does the timer start.
        await advance(1);
        await advance(BACKFILL_DELAY_MS);
        await advance(1);

        expect(screen.getByText('a post from the feed')).toBeTruthy();
        expect(screen.queryByRole('heading', { name: 'THE SURFACE IS QUIET' })).toBeNull();
    });

    it('should_not_show_the_panel_when_the_feed_has_posts', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        expect(screen.queryByRole('heading', { name: 'THE SURFACE IS QUIET' })).toBeNull();
    });
});

describe('views on the feed page', () => {
    it('should_report_a_post_that_stayed_half_visible_for_a_second_after_the_batch_interval', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [
                {
                    ...POST,
                    id: 'feed-viewed-post',
                },
            ],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);

        await intersect(onlyPostItem(), 0.6);
        await advance(1000);
        expect(reportViews).not.toHaveBeenCalled();

        await advance(5000);
        expect(reportViews).toHaveBeenCalledExactlyOnceWith(
            ['feed-viewed-post'],
            {},
        );
    });

    it('should_not_report_a_post_that_was_only_glimpsed', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [
                {
                    ...POST,
                    id: 'feed-glimpsed-post',
                },
            ],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);

        await intersect(onlyPostItem(), 0.6);
        await advance(500);
        await intersect(onlyPostItem(), 0);
        await advance(10000);

        expect(reportViews).not.toHaveBeenCalled();
    });
});

describe('new posts on the feed page', () => {
    const NEWER_POST = {
        ...POST,
        id: 'brand-new-post',
        content: 'something fresh',
        createdAt: '2026-10-04T12:30:00.000Z',
        updatedAt: '2026-10-04T12:30:00.000Z',
    };

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    beforeEach(() => {
        vi.useRealTimers();
        vi.useFakeTimers({
            toFake: [
                'setTimeout',
                'clearTimeout',
                'setInterval',
                'clearInterval',
                'requestAnimationFrame',
                'cancelAnimationFrame',
            ],
        });
    });

    it('should_offer_new_posts_after_a_minute_and_show_them_on_top_when_the_button_is_pressed', async () => {
        vi.mocked(fetchFeed)
            .mockResolvedValueOnce({
                items: [POST],
                nextCursor: null,
            })
            .mockResolvedValueOnce({
                items: [NEWER_POST, POST],
                nextCursor: null,
            });
        await renderApp(ROUTES.feed);
        expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();

        await advance(60_000);
        await user.click(screen.getByRole('button', { name: 'NEW POSTS' }));

        const texts = screen
            .getAllByRole('article')
            .map((article) => article.querySelector('.post-cell-body')?.textContent);

        expect(texts).toEqual(['something fresh', 'a post from the feed']);
    });

    it('should_not_offer_anything_when_the_feed_has_not_changed', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);

        await advance(60_000);

        expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
        expect(fetchFeed).toHaveBeenCalledTimes(2);
    });
});

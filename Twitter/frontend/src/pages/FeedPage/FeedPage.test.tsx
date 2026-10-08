import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../api/auth';
import { fetchFeed } from '../../api/feed';
import { fetchPeople, fetchUserProfile } from '../../api/users';
import { reportViews } from '../../api/views';
import { ROUTES } from '../../routes';
import { intersect } from '../../test/intersectionObserver';
import { onlyPostItem } from '../../test/postListHelpers';
import { renderApp } from '../../test/renderApp';
import { advance } from '../../test/stepFlowHelpers';
import { userProfile } from '../../test/userProfile';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    me: vi.fn(),
}));

vi.mock('../../api/feed', () => ({
    fetchFeed: vi.fn(),
}));

vi.mock('../../api/users', () => ({
    fetchUserProfile: vi.fn(),
    fetchPeople: vi.fn(),
    followUser: vi.fn(),
    unfollowUser: vi.fn(),
}));

vi.mock('../../api/views', () => ({
    reportViews: vi.fn(),
}));

vi.mock('../../api/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../api/savedTweets', () => ({
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

describe('find people from an empty feed', () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    beforeEach(() => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    });

    it('should_offer_a_find_people_button_when_the_feed_is_empty', async () => {
        await renderApp(ROUTES.feed);

        expect(screen.getByRole('button', { name: 'FIND PEOPLE' })).toBeTruthy();
    });

    it('should_open_the_people_tab_and_the_people_address_when_find_people_is_pressed', async () => {
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('button', { name: 'FIND PEOPLE' }));

        expect(window.location.pathname).toBe(ROUTES.people);
        expect(screen.getByRole('tab', { name: 'PEOPLE' }).getAttribute('aria-selected')).toBe('true');
    });

    it('should_not_offer_find_people_when_the_feed_has_posts', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.feed);

        expect(screen.queryByRole('button', { name: 'FIND PEOPLE' })).toBeNull();
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

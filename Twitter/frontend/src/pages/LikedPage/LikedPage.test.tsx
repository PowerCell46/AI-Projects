import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../api/auth';
import { fetchFeed } from '../../api/feed';
import { fetchLikedTweets, unlikeTweet } from '../../api/likes';
import { fetchUserProfile } from '../../api/users';
import { reportViews } from '../../api/views';
import { ROUTES } from '../../routes';
import { intersect } from '../../test/intersectionObserver';
import { onlyPostItem } from '../../test/postListHelpers';
import { renderApp } from '../../test/renderApp';
import { advance } from '../../test/stepFlowHelpers';


vi.mock('../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../api/auth')>(),
    me: vi.fn(),
}));

vi.mock('../../api/feed', () => ({
    fetchFeed: vi.fn(),
}));

vi.mock('../../api/savedTweets', () => ({
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

vi.mock('../../api/users', () => ({
    fetchUserProfile: vi.fn(),
}));

vi.mock('../../api/views', () => ({
    reportViews: vi.fn(),
}));

vi.mock('../../api/likes', () => ({
    fetchLikedTweets: vi.fn(),
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

const LIKED_POST = {
    id: 'tweet-1',
    views: 0,
    savedByMe: false,
    likes: 1,
    likedByMe: true,
    content: 'a liked post',
    createdAt: '2026-10-04T11:55:00.000Z',
    updatedAt: '2026-10-04T11:55:00.000Z',
    author: {
        id: 'author-1',
        username: 'ana',
        profilePictureUrl: null,
    },
    images: [],
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
    vi.mocked(fetchUserProfile).mockResolvedValue({
        id: 'user-1',
        username: 'peter_g',
        profilePictureUrl: null,
    });
    vi.mocked(fetchFeed)
        .mockReset()
        .mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    vi.mocked(fetchLikedTweets).mockReset();
    vi.mocked(unlikeTweet)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(reportViews)
        .mockReset()
        .mockResolvedValue(undefined);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the liked page', () => {
    it('should_show_the_liked_tweets_label_above_the_list', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [LIKED_POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.liked);

        const main = within(screen.getByRole('main'));
        expect(main.getByRole('heading', { name: 'LIKED TWEETS' })).toBeTruthy();
        expect(main.getByText('a liked post')).toBeTruthy();
    });

    it('should_read_the_liked_list_and_not_the_feed', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [],
            nextCursor: null,
        });

        await renderApp(ROUTES.liked);

        expect(fetchLikedTweets).toHaveBeenCalledExactlyOnceWith({
            cursor: null,
            size: 20,
        });
        expect(fetchFeed).not.toHaveBeenCalled();
    });

    it('should_list_the_first_page_of_liked_posts', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [
                LIKED_POST,
                {
                    ...LIKED_POST,
                    id: 'tweet-2',
                    content: 'a second liked post',
                },
            ],
            nextCursor: 'more',
        });

        await renderApp(ROUTES.liked);

        expect(screen.getByText('a liked post')).toBeTruthy();
        expect(screen.getByText('a second liked post')).toBeTruthy();
    });

    it('should_end_with_end_of_liked_tweets_when_the_cursor_is_exhausted', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [LIKED_POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.liked);

        expect(screen.getByText('END OF LIKED TWEETS')).toBeTruthy();
    });

    it('should_say_no_liked_tweets_yet_when_nothing_is_liked', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [],
            nextCursor: null,
        });

        await renderApp(ROUTES.liked);

        expect(screen.getByText('NO LIKED TWEETS YET')).toBeTruthy();
    });

    it('should_keep_an_unliked_post_in_place_with_an_empty_heart_and_a_count_one_lower', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [LIKED_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.liked);

        await user.click(screen.getByRole('button', { name: /^like/i }));

        const like = screen.getByRole('button', { name: /^like/i });

        expect(unlikeTweet).toHaveBeenCalledExactlyOnceWith('tweet-1');
        expect(screen.getByText('a liked post')).toBeTruthy();
        expect(like.getAttribute('aria-pressed')).toBe('false');
        expect(like.textContent).toContain('0');
    });

    it('should_show_the_liked_list_when_the_menu_item_is_chosen_from_the_feed', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [LIKED_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('button', { name: 'Account menu' }));
        await user.click(screen.getByRole('menuitem', { name: 'LIKED TWEETS' }));
        await advance(1);

        expect(screen.getByText('a liked post')).toBeTruthy();
        expect(fetchLikedTweets).toHaveBeenCalledTimes(1);
    });
});

describe('views on the liked page', () => {
    it('should_report_a_liked_post_that_stayed_half_visible_for_a_second_once_per_page_load', async () => {
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [
                {
                    ...LIKED_POST,
                    id: 'liked-viewed-post',
                },
            ],
            nextCursor: null,
        });
        await renderApp(ROUTES.liked);

        await intersect(onlyPostItem(), 1);
        await advance(1000);
        await advance(5000);

        expect(reportViews).toHaveBeenCalledExactlyOnceWith(
            ['liked-viewed-post'],
            {},
        );
    });

    it('should_not_report_a_post_again_when_it_was_already_reported_on_the_feed_in_the_same_page_load', async () => {
        const sharedPost = {
            ...LIKED_POST,
            id: 'seen-on-both-pages',
        };
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [sharedPost],
            nextCursor: null,
        });
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [sharedPost],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);
        await intersect(onlyPostItem(), 1);
        await advance(1000);
        await advance(5000);
        expect(reportViews).toHaveBeenCalledTimes(1);

        await user.click(screen.getByRole('button', { name: 'Account menu' }));
        await user.click(screen.getByRole('menuitem', { name: 'LIKED TWEETS' }));
        await advance(1);
        await intersect(onlyPostItem(), 1);
        await advance(1000);
        await advance(5000);

        expect(reportViews).toHaveBeenCalledTimes(1);
    });
});

describe('the liked page and new posts', () => {
    it('should_never_check_for_new_posts_on_the_liked_page', async () => {
        vi.useRealTimers();
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
        vi.mocked(fetchLikedTweets).mockResolvedValue({
            items: [LIKED_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.liked);

        await advance(60_000 * 3);

        expect(fetchLikedTweets).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
    });
});

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../api/auth';
import { fetchFeed } from '../../api/feed';
import { unsaveTweet, fetchSavedTweets } from '../../api/savedTweets';
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
    fetchSavedTweets: vi.fn(),
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
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

const SAVED_POST = {
    id: 'tweet-1',
    views: 0,
    savedByMe: true,
    content: 'a saved post',
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
    vi.mocked(fetchSavedTweets).mockReset();
    vi.mocked(unsaveTweet)
        .mockReset()
        .mockResolvedValue(undefined);
    vi.mocked(reportViews)
        .mockReset()
        .mockResolvedValue(undefined);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the saved page', () => {
    it('should_show_the_saved_tweets_label_above_the_list', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [SAVED_POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.saved);

        const main = within(screen.getByRole('main'));
        expect(main.getByRole('heading', { name: 'SAVED TWEETS' })).toBeTruthy();
        expect(main.getByText('a saved post')).toBeTruthy();
    });

    it('should_read_the_saved_list_and_not_the_feed', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [],
            nextCursor: null,
        });

        await renderApp(ROUTES.saved);

        expect(fetchSavedTweets).toHaveBeenCalledExactlyOnceWith({
            cursor: null,
            size: 20,
        });
        expect(fetchFeed).not.toHaveBeenCalled();
    });

    it('should_end_with_end_of_saved_tweets_when_the_cursor_is_exhausted', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [SAVED_POST],
            nextCursor: null,
        });

        await renderApp(ROUTES.saved);

        expect(screen.getByText('END OF SAVED TWEETS')).toBeTruthy();
    });

    it('should_say_no_saved_tweets_yet_when_nothing_is_saved', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [],
            nextCursor: null,
        });

        await renderApp(ROUTES.saved);

        expect(screen.getByText('NO SAVED TWEETS YET')).toBeTruthy();
    });

    it('should_keep_an_unsaved_post_in_place_with_an_empty_bookmark', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [SAVED_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.saved);

        await user.click(screen.getByRole('button', { name: /^save/i }));

        expect(unsaveTweet).toHaveBeenCalledExactlyOnceWith('tweet-1');
        expect(screen.getByText('a saved post')).toBeTruthy();
        expect(screen.getByRole('button', { name: /^save/i }).getAttribute('aria-pressed')).toBe('false');
    });

    it('should_show_the_saved_list_when_the_menu_item_is_chosen_from_the_feed', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [SAVED_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);

        await user.click(screen.getByRole('button', { name: 'Account menu' }));
        await user.click(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));
        await advance(1);

        expect(screen.getByText('a saved post')).toBeTruthy();
        expect(fetchSavedTweets).toHaveBeenCalledTimes(1);
    });
});

describe('views on the saved page', () => {
    it('should_report_a_saved_post_that_stayed_half_visible_for_a_second_after_the_batch_interval', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [
                {
                    ...SAVED_POST,
                    id: 'saved-viewed-post',
                },
            ],
            nextCursor: null,
        });
        await renderApp(ROUTES.saved);

        await intersect(onlyPostItem(), 1);
        await advance(1000);
        await advance(5000);

        expect(reportViews).toHaveBeenCalledExactlyOnceWith(
            ['saved-viewed-post'],
            {},
        );
    });

    it('should_not_report_a_post_again_when_it_was_already_reported_on_the_feed_in_the_same_page_load', async () => {
        const sharedPost = {
            ...SAVED_POST,
            id: 'seen-on-both-pages',
        };
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [sharedPost],
            nextCursor: null,
        });
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [sharedPost],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);
        await intersect(onlyPostItem(), 1);
        await advance(1000);
        await advance(5000);
        expect(reportViews).toHaveBeenCalledTimes(1);

        await user.click(screen.getByRole('button', { name: 'Account menu' }));
        await user.click(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));
        await advance(1);
        await intersect(onlyPostItem(), 1);
        await advance(1000);
        await advance(5000);

        expect(reportViews).toHaveBeenCalledTimes(1);
    });
});

describe('the saved page and new posts', () => {
    it('should_never_check_for_new_posts_on_the_saved_page', async () => {
        vi.useRealTimers();
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [SAVED_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.saved);

        await advance(60_000 * 3);

        expect(fetchSavedTweets).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
    });
});

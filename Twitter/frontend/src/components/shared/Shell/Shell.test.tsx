import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../../api/auth';
import { fetchFeed } from '../../../api/feed';
import { ApiError } from '../../../api/http';
import { fetchSavedTweets } from '../../../api/savedTweets';
import { publishTweet } from '../../../api/tweets';
import { fetchPeople, fetchUserProfile } from '../../../api/users';
import { ROUTES } from '../../../routes';
import { reachListBottom } from '../../../test/postListHelpers';
import { renderApp } from '../../../test/renderApp';
import { advance } from '../../../test/stepFlowHelpers';


vi.mock('../../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../../api/auth')>(),
    me: vi.fn(),
}));

vi.mock('../../../api/feed', () => ({
    fetchFeed: vi.fn(),
}));

vi.mock('../../../api/savedTweets', () => ({
    fetchSavedTweets: vi.fn(),
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

vi.mock('../../../api/tweets', () => ({
    publishTweet: vi.fn(),
}));

vi.mock('../../../api/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../../api/users', () => ({
    fetchUserProfile: vi.fn(),
    fetchPeople: vi.fn(),
    followUser: vi.fn(),
    unfollowUser: vi.fn(),
}));

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

const EMPTY_PAGE = {
    items: [],
    nextCursor: null,
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
    vi.mocked(fetchFeed)
        .mockReset()
        .mockResolvedValue(EMPTY_PAGE);
    vi.mocked(fetchSavedTweets)
        .mockReset()
        .mockResolvedValue(EMPTY_PAGE);
    vi.mocked(fetchPeople)
        .mockReset()
        .mockResolvedValue(EMPTY_PAGE);
    vi.mocked(fetchUserProfile)
        .mockReset()
        .mockResolvedValue({
            id: SIGNED_IN_USER.id,
            username: SIGNED_IN_USER.username,
            profilePictureUrl: null,
        });
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the shell', () => {
    it.each([ROUTES.feed, ROUTES.saved])('should_wrap_the_%s_page_in_the_header_and_a_main_landmark', async (path) => {
        await renderApp(path);

        expect(screen.getByRole('banner')).toBeTruthy();
        expect(within(screen.getByRole('main')).getByRole('heading')).toBeTruthy();
    });

    it('should_show_the_feed_page_inside_main_when_the_path_is_feed', async () => {
        await renderApp(ROUTES.feed);

        expect(within(screen.getByRole('main')).getByRole('heading', { name: 'Feed' })).toBeTruthy();
    });

    it('should_show_the_saved_page_inside_main_when_the_path_is_saved', async () => {
        await renderApp(ROUTES.saved);

        expect(within(screen.getByRole('main')).getByRole('heading', { name: 'SAVED TWEETS' })).toBeTruthy();
    });

    it('should_send_a_logged_out_visitor_to_login_instead_of_showing_the_shell', async () => {
        vi.mocked(me).mockRejectedValue(new ApiError(401, []));

        await renderApp(ROUTES.saved);

        expect(window.location.pathname).toBe(ROUTES.login);
        expect(screen.queryByRole('banner')).toBeNull();
    });
});

describe('the tab row', () => {
    it.each([ROUTES.feed, ROUTES.people])('should_show_the_tab_row_under_the_header_on_%s', async (path) => {
        await renderApp(path);

        expect(screen.getByRole('tablist')).toBeTruthy();
    });

    it('should_show_no_tab_row_on_the_saved_page', async () => {
        await renderApp(ROUTES.saved);

        expect(screen.queryByRole('tablist')).toBeNull();
    });

    it('should_keep_the_header_and_the_tab_row_in_one_block_so_they_stick_together', async () => {
        await renderApp(ROUTES.feed);

        const stickyBlock = screen.getByRole('banner').parentElement;

        expect(stickyBlock?.contains(screen.getByRole('tablist'))).toBe(true);
        expect(stickyBlock?.contains(screen.getByRole('main'))).toBe(false);
    });

    it('should_keep_the_header_in_place_when_the_user_switches_tabs', async () => {
        await renderApp(ROUTES.feed);
        const header = screen.getByRole('banner');

        await user.click(screen.getByRole('tab', { name: 'PEOPLE' }));

        expect(screen.getByRole('banner')).toBe(header);
    });

    it('should_keep_the_tab_row_in_place_when_the_user_switches_tabs', async () => {
        await renderApp(ROUTES.feed);
        const tablist = screen.getByRole('tablist');

        await user.click(screen.getByRole('tab', { name: 'PEOPLE' }));

        expect(screen.getByRole('tablist')).toBe(tablist);
    });
});

describe('the header', () => {
    it('should_link_the_brand_to_the_feed', async () => {
        await renderApp(ROUTES.saved);

        const brand = within(screen.getByRole('banner'))
            .getByRole('link', { name: 'TWITTER' });
        await user.click(brand);

        expect(brand.getAttribute('href')).toBe(ROUTES.feed);
        expect(window.location.pathname).toBe(ROUTES.feed);
    });

    it('should_show_the_post_button_and_the_account_menu_in_the_header', async () => {
        await renderApp(ROUTES.feed);

        const header = within(screen.getByRole('banner'));

        expect(header.getByRole('button', { name: 'Post' })).toBeTruthy();
        expect(header.getByRole('button', { name: 'Account menu' })).toBeTruthy();
    });

    it('should_show_the_post_label_as_text_next_to_the_plus', async () => {
        await renderApp(ROUTES.feed);

        expect(screen.getByRole('button', { name: 'Post' }).textContent).toBe('POST');
    });

    it('should_keep_the_header_in_place_when_the_user_moves_from_the_feed_to_saved_tweets', async () => {
        await renderApp(ROUTES.feed);
        const header = screen.getByRole('banner');

        await user.click(screen.getByRole('button', { name: 'Account menu' }));
        await user.click(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));

        expect(screen.getByRole('banner')).toBe(header);
    });
});

describe('composing', () => {
    const PUBLISHED = {
        id: 'new-tweet',
        authorId: 'user-1',
        content: 'my brand new post',
        createdAt: '2026-10-04T12:00:00.000Z',
        updatedAt: '2026-10-04T12:00:00.000Z',
        images: [],
    };

    const OLDER_POST = {
        id: 'older-tweet',
        views: 0,
        savedByMe: false,
        content: 'an older post',
        createdAt: '2026-10-04T10:00:00.000Z',
        updatedAt: '2026-10-04T10:00:00.000Z',
        author: {
            id: 'author-2',
            username: 'ana',
            profilePictureUrl: null,
        },
        images: [],
    };

    beforeEach(() => {
        vi.mocked(publishTweet)
            .mockReset()
            .mockResolvedValue(PUBLISHED);
    });

    async function openCompose() {
        await user.click(screen.getByRole('button', { name: 'Post' }));
        await advance(80);
    }

    async function publish(text: string) {
        await user.paste(text);
        await user.click(screen.getByRole('button', { name: /^PUBLISH/ }));
        await advance(1);
    }

    function articleTexts(): string[] {
        return screen.getAllByRole('article').map((article) => article.textContent ?? '');
    }

    it('should_open_the_compose_dialog_when_the_post_button_is_pressed', async () => {
        await renderApp(ROUTES.feed);

        await openCompose();

        expect(screen.getByRole('dialog', { name: "What's worth sending up?" })).toBeTruthy();
    });

    it('should_open_the_dialog_on_the_saved_page_too', async () => {
        await renderApp(ROUTES.saved);

        await openCompose();

        expect(screen.getByRole('dialog')).toBeTruthy();
    });

    it('should_close_and_return_focus_to_the_post_button_when_cancel_is_pressed', async () => {
        await renderApp(ROUTES.feed);
        await openCompose();

        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        expect(screen.queryByRole('dialog')).toBeNull();
        expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Post' }));
    });

    it('should_lock_the_page_scroll_only_while_the_dialog_is_open', async () => {
        await renderApp(ROUTES.feed);
        await openCompose();
        expect(document.documentElement.dataset.scrollLocked).toBe('true');

        await user.click(screen.getByRole('button', { name: 'CANCEL' }));

        expect(document.documentElement.dataset.scrollLocked).toBeUndefined();
    });

    it('should_start_with_an_empty_draft_each_time_the_dialog_is_opened', async () => {
        await renderApp(ROUTES.feed);
        await openCompose();
        await user.paste('a draft');
        await user.click(screen.getByRole('button', { name: 'CANCEL' }));
        await user.click(screen.getByRole('button', { name: 'DISCARD' }));

        await openCompose();

        expect(screen.getByRole<HTMLTextAreaElement>('textbox').value).toBe('');
    });

    it('should_close_the_dialog_and_return_focus_to_the_post_button_when_the_post_is_published', async () => {
        await renderApp(ROUTES.feed);
        await openCompose();

        await publish('my brand new post');

        expect(screen.queryByRole('dialog')).toBeNull();
        expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Post' }));
    });

    it('should_show_the_new_post_at_the_top_of_the_feed_at_once_above_the_loaded_posts', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [OLDER_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);
        await openCompose();

        await publish('my brand new post');

        expect(articleTexts()).toEqual([
            expect.stringContaining('my brand new post'),
            expect.stringContaining('an older post'),
        ]);
    });

    it('should_show_the_new_post_with_my_username_the_default_picture_and_no_saved_bookmark', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);
        await openCompose();

        await publish('my brand new post');

        const article = within(screen.getByRole('article'));
        expect(article.getByText('peter_g')).toBeTruthy();
        expect(screen.getByRole('article').querySelector('img.avatar')?.getAttribute('src'))
            .toBe('/Default-Profile-Picture.png');
        expect(article.getByRole('button', { name: /^save/i }).getAttribute('aria-pressed')).toBe('false');
    });

    it('should_show_my_profile_picture_on_the_new_post_when_the_profile_has_one', async () => {
        vi.mocked(fetchUserProfile).mockResolvedValue({
            id: 'user-1',
            username: 'peter_g',
            profilePictureUrl: 'http://localhost/api/v1/files/pic-1',
        });
        await renderApp(ROUTES.feed);
        await openCompose();

        await publish('my brand new post');

        expect(screen.getByRole('article').querySelector('img.avatar')?.getAttribute('src'))
            .toBe('http://localhost/api/v1/files/pic-1');
    });

    it('should_end_the_list_instead_of_calling_it_empty_when_only_the_new_post_is_there', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [],
            nextCursor: null,
        });
        await renderApp(ROUTES.feed);
        expect(screen.getByText('NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS')).toBeTruthy();
        await openCompose();

        await publish('my brand new post');

        expect(screen.queryByText('NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS')).toBeNull();
        expect(screen.getByText('END OF FEED')).toBeTruthy();
    });

    it('should_show_the_post_once_when_a_later_page_delivers_the_same_id', async () => {
        vi.mocked(fetchFeed)
            .mockResolvedValueOnce({
                items: [OLDER_POST],
                nextCursor: 'c1',
            })
            .mockResolvedValueOnce({
                items: [
                    {
                        ...OLDER_POST,
                        id: 'new-tweet',
                        content: 'my brand new post',
                    },
                ],
                nextCursor: null,
            });
        await renderApp(ROUTES.feed);
        await openCompose();
        await publish('my brand new post');

        await reachListBottom();

        expect(articleTexts().filter((text) => text.includes('my brand new post'))).toHaveLength(1);
    });

    it('should_keep_the_new_post_on_top_of_the_feed_when_the_user_comes_back_from_the_saved_page', async () => {
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [OLDER_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.saved);
        await openCompose();
        await publish('my brand new post');
        expect(screen.queryByText('my brand new post')).toBeNull();

        await user.click(screen.getByRole('link', { name: 'TWITTER' }));
        await advance(1);

        expect(articleTexts()[0]).toContain('my brand new post');
    });

    it('should_not_show_the_new_post_on_the_saved_page', async () => {
        vi.mocked(fetchSavedTweets).mockResolvedValue({
            items: [OLDER_POST],
            nextCursor: null,
        });
        await renderApp(ROUTES.saved);
        await openCompose();

        await publish('my brand new post');

        expect(articleTexts()).toEqual([expect.stringContaining('an older post')]);
    });

    it('should_keep_the_dialog_open_with_the_draft_when_publishing_fails', async () => {
        vi.mocked(publishTweet).mockRejectedValue(new ApiError(500, []));
        await renderApp(ROUTES.feed);
        await openCompose();

        await publish('my brand new post');

        expect(screen.getByRole('dialog')).toBeTruthy();
        expect(screen.getByRole<HTMLTextAreaElement>('textbox').value).toBe('my brand new post');
    });
});

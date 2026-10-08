import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { logout, me } from '../../../../../api/auth';
import { fetchAuthorTweets } from '../../../../../api/authorTweets';
import { fetchFeed } from '../../../../../api/feed';
import { ApiError } from '../../../../../api/http';
import { fetchLikedTweets } from '../../../../../api/likes';
import { fetchSavedTweets } from '../../../../../api/savedTweets';
import { fetchTweetCount } from '../../../../../api/tweets';
import { fetchUserProfile } from '../../../../../api/users';
import { ROUTES, profilePath } from '../../../../../routes';
import { renderApp } from '../../../../../test/renderApp';
import { advance } from '../../../../../test/stepFlowHelpers';
import { SIGNAL_LOST_MESSAGE } from '../../../../../utils/authErrors';
import { userProfile } from '../../../../../test/userProfile';


vi.mock('../../../../../api/auth', async (importOriginal) => ({
    ...await importOriginal<typeof import('../../../../../api/auth')>(),
    me: vi.fn(),
    logout: vi.fn(),
}));

vi.mock('../../../../../api/feed', () => ({
    fetchFeed: vi.fn(),
}));

vi.mock('../../../../../api/likes', () => ({
    fetchLikedTweets: vi.fn(),
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../../../../api/savedTweets', () => ({
    fetchSavedTweets: vi.fn(),
}));

vi.mock('../../../../../api/users', () => ({
    fetchUserProfile: vi.fn(),
}));

vi.mock('../../../../../api/authorTweets', () => ({
    fetchAuthorTweets: vi.fn(),
}));

vi.mock('../../../../../api/tweets', () => ({
    fetchTweetCount: vi.fn(),
    publishTweet: vi.fn(),
}));

const SIGN_OUT_LEAVE_MS = 1200;

const SIGNED_IN_USER = {
    id: 'user-1',
    username: 'peter_g',
    email: 'peter@example.com',
};

const EMPTY_PAGE = {
    items: [],
    nextCursor: null,
};

const PROFILE_WITHOUT_PICTURE = userProfile({
    id: SIGNED_IN_USER.id,
    username: SIGNED_IN_USER.username,
    profilePictureUrl: null,
});

let user: ReturnType<typeof userEvent.setup>;

beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame'] });
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
    vi.mocked(fetchFeed)
        .mockReset()
        .mockResolvedValue(EMPTY_PAGE);
    vi.mocked(fetchSavedTweets)
        .mockReset()
        .mockResolvedValue(EMPTY_PAGE);
    vi.mocked(fetchLikedTweets)
        .mockReset()
        .mockResolvedValue(EMPTY_PAGE);
    vi.mocked(fetchAuthorTweets)
        .mockReset()
        .mockResolvedValue(EMPTY_PAGE);
    vi.mocked(fetchTweetCount)
        .mockReset()
        .mockResolvedValue(0);
    vi.mocked(logout).mockReset();
    vi.mocked(fetchUserProfile)
        .mockReset()
        .mockResolvedValue(PROFILE_WITHOUT_PICTURE);
});

afterEach(() => {
    vi.useRealTimers();
});

function trigger(): HTMLElement {
    return screen.getByRole('button', { name: 'Account menu' });
}

function triggerPicture(): HTMLImageElement {
    const picture = trigger().querySelector('img');

    if (!picture) {
        throw new Error('The account menu picture is missing.');
    }

    return picture;
}

async function openMenu() {
    await user.click(trigger());
}

function menuItemNames(): string[] {
    return within(screen.getByRole('menu'))
        .getAllByRole('menuitem')
        .map((item) => item.textContent ?? '');
}

describe('the avatar', () => {
    it('should_show_the_default_picture_while_there_is_no_picture', async () => {
        await renderApp(ROUTES.feed);

        expect(trigger().querySelector('img')?.getAttribute('src')).toBe('/Default-Profile-Picture.png');
    });

    it('should_read_the_profile_of_the_signed_in_user_once', async () => {
        await renderApp(ROUTES.feed);

        expect(fetchUserProfile).toHaveBeenCalledExactlyOnceWith('peter_g');
    });

    it('should_show_the_picture_instead_of_the_default_when_the_profile_has_one', async () => {
        vi.mocked(fetchUserProfile).mockResolvedValue({
            ...PROFILE_WITHOUT_PICTURE,
            profilePictureUrl: 'http://localhost/api/v1/files/pic-1',
        });

        await renderApp(ROUTES.feed);

        expect(trigger().querySelector('img')?.getAttribute('src')).toBe('http://localhost/api/v1/files/pic-1');
    });

    it('should_keep_the_default_picture_when_the_profile_cannot_be_read', async () => {
        vi.mocked(fetchUserProfile).mockRejectedValue(new ApiError(502, []));

        await renderApp(ROUTES.feed);

        expect(trigger().querySelector('img')?.getAttribute('src')).toBe('/Default-Profile-Picture.png');
    });

    it('should_fall_back_to_the_default_picture_when_the_profile_picture_fails_to_load', async () => {
        vi.mocked(fetchUserProfile).mockResolvedValue({
            ...PROFILE_WITHOUT_PICTURE,
            profilePictureUrl: 'http://localhost/api/v1/files/pic-1',
        });
        await renderApp(ROUTES.feed);

        fireEvent.error(triggerPicture());

        expect(trigger().querySelector('img')?.getAttribute('src')).toBe('/Default-Profile-Picture.png');
    });

    it('should_not_read_the_profile_again_when_the_user_moves_between_pages', async () => {
        await renderApp(ROUTES.feed);
        await openMenu();

        await user.click(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));

        expect(fetchUserProfile).toHaveBeenCalledTimes(1);
    });
});

describe('opening and closing', () => {
    beforeEach(async () => {
        await renderApp(ROUTES.feed);
    });

    it('should_start_closed', () => {
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
        expect(trigger().getAttribute('aria-haspopup')).toBe('menu');
        expect(screen.queryByRole('menu')).toBeNull();
    });

    it('should_open_with_the_four_items_when_the_avatar_is_clicked', async () => {
        await openMenu();

        expect(trigger().getAttribute('aria-expanded')).toBe('true');
        expect(menuItemNames()).toEqual(['PROFILE', 'SAVED TWEETS', 'LIKED TWEETS', 'LOG OUT']);
    });

    it('should_move_focus_to_the_first_item_when_opened', async () => {
        await openMenu();

        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'PROFILE' }));
    });

    it('should_close_when_the_avatar_is_clicked_again', async () => {
        await openMenu();

        await user.click(trigger());

        expect(screen.queryByRole('menu')).toBeNull();
        expect(trigger().getAttribute('aria-expanded')).toBe('false');
    });

    it('should_close_and_return_focus_to_the_avatar_when_escape_is_pressed', async () => {
        await openMenu();

        await user.keyboard('{Escape}');

        expect(screen.queryByRole('menu')).toBeNull();
        expect(document.activeElement).toBe(trigger());
    });

    it('should_close_when_the_page_outside_the_menu_is_clicked', async () => {
        await openMenu();

        await user.click(document.body);

        expect(screen.queryByRole('menu')).toBeNull();
    });

    it('should_stay_open_when_the_menu_itself_is_clicked_outside_an_item', async () => {
        await openMenu();

        await user.click(screen.getByRole('menu'));

        expect(screen.getByRole('menu')).toBeTruthy();
    });

    it('should_close_when_tab_is_pressed', async () => {
        await openMenu();

        await user.keyboard('{Tab}');

        expect(screen.queryByRole('menu')).toBeNull();
    });
});

describe('keyboard', () => {
    beforeEach(async () => {
        await renderApp(ROUTES.feed);
    });

    it('should_open_on_the_first_item_when_arrow_down_is_pressed_on_the_avatar', async () => {
        trigger()
            .focus();

        await user.keyboard('{ArrowDown}');

        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'PROFILE' }));
    });

    it('should_open_on_the_last_item_when_arrow_up_is_pressed_on_the_avatar', async () => {
        trigger()
            .focus();

        await user.keyboard('{ArrowUp}');

        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'LOG OUT' }));
    });

    it('should_open_when_enter_is_pressed_on_the_avatar', async () => {
        trigger()
            .focus();

        await user.keyboard('{Enter}');

        expect(screen.getByRole('menu')).toBeTruthy();
    });

    it('should_move_to_the_next_item_and_wrap_around_across_four_items_when_arrow_down_is_pressed', async () => {
        await openMenu();

        await user.keyboard('{ArrowDown}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));

        await user.keyboard('{ArrowDown}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'LIKED TWEETS' }));

        await user.keyboard('{ArrowDown}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'LOG OUT' }));

        await user.keyboard('{ArrowDown}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'PROFILE' }));
    });

    it('should_move_to_the_previous_item_and_wrap_around_across_four_items_when_arrow_up_is_pressed', async () => {
        await openMenu();

        await user.keyboard('{ArrowUp}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'LOG OUT' }));

        await user.keyboard('{ArrowUp}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'LIKED TWEETS' }));

        await user.keyboard('{ArrowUp}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));

        await user.keyboard('{ArrowUp}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'PROFILE' }));

        await user.keyboard('{ArrowUp}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'LOG OUT' }));
    });

    it('should_jump_to_the_last_and_the_first_item_when_end_and_home_are_pressed', async () => {
        await openMenu();

        await user.keyboard('{End}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'LOG OUT' }));

        await user.keyboard('{Home}');
        expect(document.activeElement).toBe(screen.getByRole('menuitem', { name: 'PROFILE' }));
    });
});

describe('profile', () => {
    beforeEach(async () => {
        await renderApp(ROUTES.feed);
        await openMenu();
    });

    it('should_link_to_the_profile_of_the_signed_in_user', () => {
        expect(screen.getByRole('menuitem', { name: 'PROFILE' }).getAttribute('href')).toBe(profilePath('peter_g'));
    });

    it('should_go_to_the_own_profile_and_close_the_menu_when_profile_is_chosen', async () => {
        await user.click(screen.getByRole('menuitem', { name: 'PROFILE' }));
        await advance(1);

        expect(window.location.pathname).toBe(profilePath('peter_g'));
        expect(screen.queryByRole('menu')).toBeNull();
        expect(screen.getByRole('heading', { level: 1, name: 'peter_g' })).toBeTruthy();
    });

    it('should_return_focus_to_the_avatar_when_profile_is_chosen', async () => {
        await user.click(screen.getByRole('menuitem', { name: 'PROFILE' }));

        expect(document.activeElement).toBe(trigger());
    });
});

describe('saved tweets', () => {
    beforeEach(async () => {
        await renderApp(ROUTES.feed);
        await openMenu();
    });

    it('should_go_to_the_saved_page_and_close_the_menu_when_saved_tweets_is_chosen', async () => {
        await user.click(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));

        expect(window.location.pathname).toBe(ROUTES.saved);
        expect(screen.queryByRole('menu')).toBeNull();
        expect(screen.getByRole('heading', { name: 'SAVED TWEETS' })).toBeTruthy();
    });

    it('should_return_focus_to_the_avatar_when_saved_tweets_is_chosen', async () => {
        await user.click(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));

        expect(document.activeElement).toBe(trigger());
    });
});

describe('log out', () => {
    beforeEach(async () => {
        await renderApp(ROUTES.feed);
        await openMenu();
    });

    it('should_call_logout_and_land_on_login_when_log_out_is_chosen', async () => {
        vi.mocked(logout).mockResolvedValue(undefined);
        vi.mocked(me).mockRejectedValue(new ApiError(401, []));

        await user.click(screen.getByRole('menuitem', { name: 'LOG OUT' }));
        await advance(SIGN_OUT_LEAVE_MS);

        expect(logout).toHaveBeenCalledTimes(1);
        expect(window.location.pathname).toBe(ROUTES.login);
    });

    it('should_play_the_feed_out_before_landing_on_login_when_log_out_is_chosen', async () => {
        vi.mocked(logout).mockResolvedValue(undefined);
        vi.mocked(me).mockRejectedValue(new ApiError(401, []));

        await user.click(screen.getByRole('menuitem', { name: 'LOG OUT' }));
        await advance(SIGN_OUT_LEAVE_MS - 1);

        expect(document.querySelector('.shell')?.getAttribute('data-signing-out')).toBe('true');
        expect(window.location.pathname).toBe(ROUTES.feed);

        await advance(1);

        expect(window.location.pathname).toBe(ROUTES.login);
        expect(document.querySelector('.descent-stage')?.getAttribute('data-entering')).toBe('true');
    });

    it('should_stay_on_the_feed_with_signal_lost_in_the_menu_when_logout_fails', async () => {
        vi.mocked(logout).mockRejectedValue(new ApiError(0, []));

        await user.click(screen.getByRole('menuitem', { name: 'LOG OUT' }));
        await advance(1);

        expect(window.location.pathname).toBe(ROUTES.feed);
        expect(screen.getByRole('menu')).toBeTruthy();
        expect(screen.getByRole('alert').textContent).toBe(SIGNAL_LOST_MESSAGE);
    });

    it('should_clear_the_signal_lost_message_when_log_out_is_tried_again', async () => {
        vi.mocked(logout)
            .mockRejectedValueOnce(new ApiError(0, []))
            .mockReturnValue(new Promise<void>(() => {}));
        await user.click(screen.getByRole('menuitem', { name: 'LOG OUT' }));
        await advance(1);

        await user.click(screen.getByRole('menuitem', { name: 'LOG OUT' }));

        expect(logout).toHaveBeenCalledTimes(2);
        expect(screen.getByRole('alert').textContent).toBe('');
    });

    it('should_not_send_a_second_logout_while_the_first_is_still_in_flight', async () => {
        vi.mocked(logout).mockReturnValue(new Promise<void>(() => {}));

        await user.click(screen.getByRole('menuitem', { name: 'LOG OUT' }));
        await user.click(screen.getByRole('menuitem', { name: 'LOG OUT' }));

        expect(logout).toHaveBeenCalledTimes(1);
    });
});

describe('liked tweets', () => {
    beforeEach(async () => {
        await renderApp(ROUTES.feed);
        await openMenu();
    });

    it('should_go_to_the_liked_page_and_close_the_menu_when_liked_tweets_is_chosen', async () => {
        await user.click(screen.getByRole('menuitem', { name: 'LIKED TWEETS' }));

        expect(window.location.pathname).toBe(ROUTES.liked);
        expect(screen.queryByRole('menu')).toBeNull();
        expect(screen.getByRole('heading', { name: 'LIKED TWEETS' })).toBeTruthy();
    });

    it('should_return_focus_to_the_avatar_when_liked_tweets_is_chosen', async () => {
        await user.click(screen.getByRole('menuitem', { name: 'LIKED TWEETS' }));

        expect(document.activeElement).toBe(trigger());
    });
});

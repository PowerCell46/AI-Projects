import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { me } from '../../../api/auth';
import { fetchFeed } from '../../../api/feed';
import { fetchSavedTweets } from '../../../api/savedTweets';
import { fetchPeople, fetchUserProfile, followUser } from '../../../api/users';
import { ROUTES } from '../../../routes';
import { renderApp } from '../../../test/renderApp';
import { advance, waitForHistoryTraversal } from '../../../test/stepFlowHelpers';
import { tabPanelId } from '../../../utils/tabs';
import type { TabId } from '../../../utils/tabs';


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

const POST = {
    id: 'tweet-1',
    views: 0,
    savedByMe: false,
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

const PERSON = {
    id: 'person-1',
    username: 'bob',
    bio: 'Sails at dawn',
    followersCount: 12,
    followedByMe: false,
    profilePictureUrl: null,
};

const NEWER_POST = {
    ...POST,
    id: 'tweet-2',
    content: 'a newer post',
    createdAt: '2026-10-04T12:30:00.000Z',
    updatedAt: '2026-10-04T12:30:00.000Z',
};

const NEW_POSTS_INTERVAL_MS = 60_000;

let user: ReturnType<typeof userEvent.setup>;

// By id, not by role and name: the accessible name of a hidden element is empty.
function panelOf(tabId: TabId): HTMLElement {
    const panel = document.getElementById(tabPanelId(tabId));

    if (!panel) {
        throw new Error(`Expected a panel for the ${tabId} tab.`);
    }

    return panel;
}

function tweetsPanel(): HTMLElement {
    return panelOf('tweets');
}

function peoplePanel(): HTMLElement {
    return panelOf('people');
}

function tab(name: 'TWEETS' | 'PEOPLE'): HTMLElement {
    return screen.getByRole('tab', { name });
}

function setScrollY(scrollY: number) {
    Object.defineProperty(window, 'scrollY', {
        configurable: true,
        value: scrollY,
    });
}

function scrollTo(scrollY: number) {
    setScrollY(scrollY);

    act(() => {
        window.dispatchEvent(new Event('scroll'));
    });
}

beforeEach(() => {
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
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.mocked(me).mockResolvedValue(SIGNED_IN_USER);
    vi.mocked(fetchFeed)
        .mockReset()
        .mockResolvedValue({
            items: [POST],
            nextCursor: null,
        });
    vi.mocked(fetchSavedTweets)
        .mockReset()
        .mockResolvedValue({
            items: [],
            nextCursor: null,
        });
    vi.mocked(fetchPeople)
        .mockReset()
        .mockResolvedValue({
            items: [PERSON],
            nextCursor: null,
        });
    vi.mocked(fetchUserProfile)
        .mockReset()
        .mockResolvedValue({
            id: SIGNED_IN_USER.id,
            username: SIGNED_IN_USER.username,
            profilePictureUrl: null,
        });
});

afterEach(() => {
    Reflect.deleteProperty(window, 'scrollY');
    vi.useRealTimers();
});

describe('the panels on first load', () => {
    it('should_show_the_tweets_panel_and_hide_the_people_panel_when_the_path_is_feed', async () => {
        await renderApp(ROUTES.feed);

        expect(tab('TWEETS').getAttribute('aria-selected')).toBe('true');
        expect(tweetsPanel().hidden).toBe(false);
        expect(peoplePanel().hidden).toBe(true);
    });

    it('should_show_the_people_panel_and_hide_the_tweets_panel_when_the_path_is_users', async () => {
        await renderApp(ROUTES.people);

        expect(tab('PEOPLE').getAttribute('aria-selected')).toBe('true');
        expect(peoplePanel().hidden).toBe(false);
        expect(tweetsPanel().hidden).toBe(true);
    });

    it('should_not_mount_the_people_page_until_the_people_tab_is_opened', async () => {
        await renderApp(ROUTES.feed);

        expect(screen.queryByRole('heading', {
            name: 'People',
            hidden: true,
        })).toBeNull();
    });

    it('should_not_read_the_feed_when_the_page_is_loaded_on_the_people_tab', async () => {
        await renderApp(ROUTES.people);

        expect(fetchFeed).not.toHaveBeenCalled();
        expect(screen.queryByText('a post from the feed')).toBeNull();
    });

    it('should_show_the_hidden_people_heading_and_the_people_in_the_people_panel', async () => {
        await renderApp(ROUTES.people);

        expect(screen.getByRole('heading', { name: 'People' })).toBeTruthy();
        expect(peoplePanel().querySelectorAll('li')).toHaveLength(1);
        expect(within(peoplePanel()).getByText('bob')).toBeTruthy();
    });

    it('should_label_each_panel_by_its_tab', async () => {
        await renderApp(ROUTES.feed);

        expect(tweetsPanel().getAttribute('aria-labelledby')).toBe(tab('TWEETS').id);
        expect(peoplePanel().getAttribute('aria-labelledby')).toBe(tab('PEOPLE').id);
        expect(tab('TWEETS').getAttribute('aria-controls')).toBe(tweetsPanel().id);
    });

    it('should_not_mark_any_panel_as_entering_on_the_first_render', async () => {
        await renderApp(ROUTES.feed);

        expect(tweetsPanel().dataset.entering).toBe('false');
        expect(peoplePanel().dataset.entering).toBe('false');
    });

    it('should_accept_a_trailing_slash_on_the_path', async () => {
        await renderApp(`${ROUTES.people}/`);

        expect(tab('PEOPLE').getAttribute('aria-selected')).toBe('true');
    });
});

describe('switching tabs', () => {
    it('should_show_the_people_panel_and_hide_the_tweets_panel_when_people_is_clicked', async () => {
        await renderApp(ROUTES.feed);

        await user.click(tab('PEOPLE'));

        expect(window.location.pathname).toBe(ROUTES.people);
        expect(tab('PEOPLE').getAttribute('aria-selected')).toBe('true');
        expect(peoplePanel().hidden).toBe(false);
        expect(tweetsPanel().hidden).toBe(true);
        expect(screen.getByRole('heading', { name: 'People' })).toBeTruthy();
    });

    it('should_keep_the_feed_mounted_and_not_read_it_again_when_the_user_goes_back_to_tweets', async () => {
        await renderApp(ROUTES.feed);
        const postBefore = screen.getByText('a post from the feed');

        await user.click(tab('PEOPLE'));
        await user.click(tab('TWEETS'));

        expect(screen.getByText('a post from the feed')).toBe(postBefore);
        expect(fetchFeed).toHaveBeenCalledOnce();
    });

    it('should_mount_the_feed_the_first_time_tweets_is_opened_when_the_page_was_loaded_on_people', async () => {
        await renderApp(ROUTES.people);

        await user.click(tab('TWEETS'));
        await advance(1);

        expect(fetchFeed).toHaveBeenCalledOnce();
        expect(screen.getByText('a post from the feed')).toBeTruthy();
    });

    it('should_mark_only_the_incoming_panel_as_entering_after_a_switch', async () => {
        await renderApp(ROUTES.feed);

        await user.click(tab('PEOPLE'));

        expect(peoplePanel().dataset.entering).toBe('true');
        expect(tweetsPanel().dataset.entering).toBe('false');
    });

    it('should_mark_the_tweets_panel_as_entering_when_the_user_comes_back_to_it', async () => {
        await renderApp(ROUTES.feed);
        await user.click(tab('PEOPLE'));

        await user.click(tab('TWEETS'));

        expect(tweetsPanel().dataset.entering).toBe('true');
        expect(peoplePanel().dataset.entering).toBe('false');
    });

    it('should_push_a_history_entry_when_the_tab_changes', async () => {
        await renderApp(ROUTES.feed);
        const entriesBefore = window.history.length;

        await user.click(tab('PEOPLE'));

        expect(window.history.length).toBe(entriesBefore + 1);
    });

    it('should_add_no_history_entry_when_the_active_tab_is_clicked', async () => {
        await renderApp(ROUTES.feed);
        const entriesBefore = window.history.length;

        await user.click(tab('TWEETS'));

        expect(window.history.length).toBe(entriesBefore);
        expect(window.location.pathname).toBe(ROUTES.feed);
    });

    it('should_return_to_the_previous_tab_when_the_browser_goes_back', async () => {
        await renderApp(ROUTES.feed);
        await user.click(tab('PEOPLE'));

        window.history.back();
        await waitForHistoryTraversal();

        expect(window.location.pathname).toBe(ROUTES.feed);
        expect(tab('TWEETS').getAttribute('aria-selected')).toBe('true');
        expect(tweetsPanel().hidden).toBe(false);
    });

    it('should_switch_to_people_and_move_focus_when_right_arrow_is_pressed_on_tweets', async () => {
        await renderApp(ROUTES.feed);
        tab('TWEETS').focus();

        await user.keyboard('{ArrowRight}');

        expect(window.location.pathname).toBe(ROUTES.people);
        expect(document.activeElement).toBe(tab('PEOPLE'));
    });

    it('should_wrap_to_tweets_when_right_arrow_is_pressed_on_people', async () => {
        await renderApp(ROUTES.people);
        tab('PEOPLE').focus();

        await user.keyboard('{ArrowRight}');

        expect(window.location.pathname).toBe(ROUTES.feed);
        expect(document.activeElement).toBe(tab('TWEETS'));
    });
});

describe('the feed while it is hidden', () => {
    it('should_keep_checking_for_new_posts_while_the_people_tab_is_open', async () => {
        await renderApp(ROUTES.feed);
        await user.click(tab('PEOPLE'));
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [NEWER_POST, POST],
            nextCursor: null,
        });

        await advance(NEW_POSTS_INTERVAL_MS);

        expect(fetchFeed).toHaveBeenCalledTimes(2);
        expect(screen.getByText('NEW POSTS')).toBeTruthy();
    });

    it('should_show_the_new_posts_button_when_the_user_comes_back_to_tweets', async () => {
        await renderApp(ROUTES.feed);
        await user.click(tab('PEOPLE'));
        vi.mocked(fetchFeed).mockResolvedValue({
            items: [NEWER_POST, POST],
            nextCursor: null,
        });
        await advance(NEW_POSTS_INTERVAL_MS);

        await user.click(tab('TWEETS'));

        expect(screen.getByRole('button', { name: 'NEW POSTS' })).toBeTruthy();
    });
});

describe('the feed after a follow change', () => {
    const EMPTY_FEED = {
        items: [],
        nextCursor: null,
    };

    async function followBobOnThePeopleTab() {
        await user.click(tab('PEOPLE'));
        await advance(1);
        await user.click(screen.getByRole('button', { name: 'Follow bob' }));
        await advance(1);
    }

    beforeEach(() => {
        vi.mocked(followUser).mockReset().mockResolvedValue(undefined);
    });

    it('should_read_an_empty_feed_again_when_the_user_comes_back_after_following_someone', async () => {
        vi.mocked(fetchFeed)
            .mockReset()
            .mockResolvedValueOnce(EMPTY_FEED)
            .mockResolvedValueOnce({
                items: [POST],
                nextCursor: null,
            });
        await renderApp(ROUTES.feed);
        await followBobOnThePeopleTab();

        await user.click(tab('TWEETS'));
        await advance(1);

        expect(fetchFeed).toHaveBeenCalledTimes(2);
        expect(within(tweetsPanel()).getByText('a post from the feed')).toBeTruthy();
    });

    it('should_not_read_the_empty_feed_again_while_the_user_is_still_on_the_people_tab', async () => {
        vi.mocked(fetchFeed)
            .mockReset()
            .mockResolvedValue(EMPTY_FEED);
        await renderApp(ROUTES.feed);

        await followBobOnThePeopleTab();

        expect(fetchFeed).toHaveBeenCalledTimes(1);
    });

    it('should_keep_a_feed_with_posts_as_it_is_when_the_user_comes_back_after_following_someone', async () => {
        await renderApp(ROUTES.feed);
        const postBefore = screen.getByText('a post from the feed');
        await followBobOnThePeopleTab();

        await user.click(tab('TWEETS'));
        await advance(1);

        expect(fetchFeed).toHaveBeenCalledOnce();
        expect(screen.getByText('a post from the feed')).toBe(postBefore);
    });

    it('should_not_read_an_empty_feed_again_when_the_user_only_looked_at_the_people_tab', async () => {
        vi.mocked(fetchFeed)
            .mockReset()
            .mockResolvedValue(EMPTY_FEED);
        await renderApp(ROUTES.feed);

        await user.click(tab('PEOPLE'));
        await user.click(tab('TWEETS'));
        await advance(1);

        expect(fetchFeed).toHaveBeenCalledTimes(1);
    });

    it('should_read_an_empty_feed_only_once_for_one_follow_change_when_the_user_switches_back_and_forth', async () => {
        vi.mocked(fetchFeed)
            .mockReset()
            .mockResolvedValue(EMPTY_FEED);
        await renderApp(ROUTES.feed);
        await followBobOnThePeopleTab();

        await user.click(tab('TWEETS'));
        await user.click(tab('PEOPLE'));
        await user.click(tab('TWEETS'));
        await advance(1);

        expect(fetchFeed).toHaveBeenCalledTimes(2);
    });

    it('should_not_read_the_feed_on_its_first_open_after_a_follow_change_beyond_that_first_read', async () => {
        vi.mocked(fetchFeed)
            .mockReset()
            .mockResolvedValue(EMPTY_FEED);
        await renderApp(ROUTES.people);
        await advance(1);
        await user.click(screen.getByRole('button', { name: 'Follow bob' }));
        await advance(1);

        await user.click(tab('TWEETS'));
        await advance(1);

        expect(fetchFeed).toHaveBeenCalledTimes(1);
    });
});

describe('scroll memory', () => {
    it('should_start_the_people_tab_at_the_top_on_its_first_visit', async () => {
        await renderApp(ROUTES.feed);
        scrollTo(300);

        await user.click(tab('PEOPLE'));

        expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 0 });
    });

    it('should_give_tweets_its_scroll_position_back_when_the_user_returns', async () => {
        await renderApp(ROUTES.feed);
        scrollTo(300);
        await user.click(tab('PEOPLE'));

        await user.click(tab('TWEETS'));

        expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 300 });
    });

    it('should_keep_a_separate_position_for_each_tab', async () => {
        await renderApp(ROUTES.feed);
        scrollTo(300);
        await user.click(tab('PEOPLE'));
        scrollTo(40);
        await user.click(tab('TWEETS'));
        expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 300 });
        scrollTo(500);

        await user.click(tab('PEOPLE'));

        expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 40 });
    });

    it('should_save_the_position_the_page_has_when_the_tab_is_pressed_before_the_scroll_event_arrives', async () => {
        await renderApp(ROUTES.feed);
        scrollTo(40);
        setScrollY(300);
        await user.click(tab('PEOPLE'));

        await user.click(tab('TWEETS'));

        expect(window.scrollTo).toHaveBeenLastCalledWith({ top: 300 });
    });

    it('should_not_scroll_when_the_tab_does_not_change', async () => {
        await renderApp(ROUTES.feed);
        scrollTo(300);

        await user.click(tab('TWEETS'));

        expect(window.scrollTo).not.toHaveBeenCalled();
    });
});

describe('leaving the tabs', () => {
    it('should_show_no_tab_row_on_the_saved_page', async () => {
        await renderApp(ROUTES.saved);

        expect(screen.queryByRole('tablist')).toBeNull();
    });

    it('should_start_both_tabs_fresh_after_a_trip_to_saved_tweets', async () => {
        await renderApp(ROUTES.feed);
        await user.click(tab('PEOPLE'));
        await user.click(screen.getByRole('button', { name: 'Account menu' }));
        await user.click(screen.getByRole('menuitem', { name: 'SAVED TWEETS' }));
        expect(screen.queryByRole('tablist')).toBeNull();

        await user.click(screen.getByRole('link', { name: 'TWITTER' }));

        expect(fetchFeed).toHaveBeenCalledTimes(2);
        expect(tab('TWEETS').getAttribute('aria-selected')).toBe('true');
        expect(screen.queryByRole('heading', {
            name: 'People',
            hidden: true,
        })).toBeNull();
    });
});

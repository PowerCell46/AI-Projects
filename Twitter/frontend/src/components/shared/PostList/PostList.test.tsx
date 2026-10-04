import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PageRequest } from '../../../api/paging';
import type { TweetItem, TweetPage } from '../../../api/tweetPage';
import { IntersectionObserverDouble, intersect, observersWatching } from '../../../test/intersectionObserver';
import PostList from './PostList';


vi.mock('../../../api/likes', () => ({
    likeTweet: vi.fn(),
    unlikeTweet: vi.fn(),
}));

vi.mock('../../../api/savedTweets', () => ({
    saveTweet: vi.fn(),
    unsaveTweet: vi.fn(),
}));

interface Deferred<T> {
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (reason: Error) => void;
}

const MINUTE_MS = 60_000;

const END_TEXT = 'END OF FEED';

const EMPTY_TEXT = 'NOTHING HERE YET';

const fetchPage = vi.fn<(request: PageRequest) => Promise<TweetPage>>();

function deferred<T>(): Deferred<T> {
    let resolve: (value: T) => void = () => undefined;
    let reject: (reason: Error) => void = () => undefined;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });

    return {
        promise,
        resolve,
        reject,
    };
}

function post(id: string, createdAt = '2026-10-04T11:55:00.000Z'): TweetItem {
    return {
        id,
        views: 0,
        savedByMe: false,
        content: `content of ${id}`,
        createdAt,
        updatedAt: createdAt,
        author: {
            id: 'author-1',
            username: 'ana',
            profilePictureUrl: null,
        },
        images: [],
    };
}

function posts(...ids: string[]): TweetItem[] {
    return ids.map((id) => post(id));
}

function page(items: TweetItem[], nextCursor: string | null): TweetPage {
    return {
        items,
        nextCursor,
    };
}

async function renderList(shouldCheckForNewPosts = false) {
    await act(async () => {
        render(
            <PostList
                fetchPage={fetchPage}
                endText={END_TEXT}
                emptyText={EMPTY_TEXT}
                shouldCheckForNewPosts={shouldCheckForNewPosts}
            />,
        );
    });
}

function currentSentinel(): Element {
    const sentinel = Array.from(IntersectionObserverDouble.active.at(-1)?.targets ?? [])[0];

    if (!sentinel) {
        throw new Error('Expected a live observer watching the sentinel.');
    }

    return sentinel;
}

function renderedPostTexts(): string[] {
    return screen.queryAllByRole('article').map((article) => article.textContent ?? '');
}

function postIds(): string[] {
    return screen
        .getAllByRole('article')
        .map((article) => article.querySelector('.post-cell-body')?.textContent?.replace('content of ', '') ?? '');
}

function liveRegion(): HTMLElement {
    const region = document.querySelector<HTMLElement>('[aria-live="polite"]');

    if (!region) {
        throw new Error('Expected a polite live region.');
    }

    return region;
}

beforeEach(() => {
    fetchPage.mockReset();
});

afterEach(() => {
    vi.useRealTimers();
});

describe('the first page', () => {
    it('should_ask_for_the_first_page_without_a_cursor_and_with_a_page_size_of_20', async () => {
        fetchPage.mockResolvedValue(page([], null));

        await renderList();

        expect(fetchPage).toHaveBeenCalledExactlyOnceWith({
            cursor: null,
            size: 20,
        });
    });

    it('should_show_fetching_more_while_the_first_page_is_on_its_way', async () => {
        fetchPage.mockReturnValue(new Promise<TweetPage>(() => undefined));

        await renderList();

        expect(screen.getByText('FETCHING MORE')).toBeTruthy();
    });

    it('should_show_the_posts_in_the_order_the_server_sent_them', async () => {
        fetchPage.mockResolvedValue(page(
            posts('first', 'second', 'third'),
            'c1',
        ));

        await renderList();

        expect(renderedPostTexts()).toEqual([
            expect.stringContaining('content of first'),
            expect.stringContaining('content of second'),
            expect.stringContaining('content of third'),
        ]);
        expect(within(screen.getByRole('list')).getAllByRole('listitem')).toHaveLength(3);
    });

    it('should_show_nothing_at_the_bottom_when_a_page_has_a_cursor_and_nothing_is_loading', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), 'c1'));

        await renderList();

        expect(screen.queryByText('FETCHING MORE')).toBeNull();
        expect(screen.queryByText(END_TEXT)).toBeNull();
        expect(screen.queryByText(EMPTY_TEXT)).toBeNull();
    });
});

describe('the bottom of the list', () => {
    it('should_show_the_end_text_when_there_is_no_next_cursor', async () => {
        fetchPage.mockResolvedValue(page(
            posts('a', 'b'),
            null,
        ));

        await renderList();

        expect(screen.getByText(END_TEXT)).toBeTruthy();
        expect(screen.queryByText('FETCHING MORE')).toBeNull();
    });

    it('should_show_the_empty_text_and_not_the_end_text_when_there_are_no_items_at_all', async () => {
        fetchPage.mockResolvedValue(page([], null));

        await renderList();

        expect(screen.getByText(EMPTY_TEXT)).toBeTruthy();
        expect(screen.queryByText(END_TEXT)).toBeNull();
    });

    it('should_use_the_texts_it_is_given', async () => {
        fetchPage.mockResolvedValue(page([], null));

        await act(async () => {
            render(<PostList fetchPage={fetchPage} endText="END OF SAVED TWEETS" emptyText="NO SAVED TWEETS YET" />);
        });

        expect(screen.getByText('NO SAVED TWEETS YET')).toBeTruthy();
    });

    it('should_show_the_end_text_after_the_last_page_is_appended', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockResolvedValueOnce(page(posts('b'), null));
        await renderList();

        await intersect(currentSentinel());

        expect(screen.getByText(END_TEXT)).toBeTruthy();
    });
});

describe('failures', () => {
    it('should_show_signal_lost_and_a_try_again_button_when_the_first_page_fails', async () => {
        fetchPage.mockRejectedValue(new Error('The request failed.'));

        await renderList();

        expect(screen.getByText('SIGNAL LOST')).toBeTruthy();
        expect(screen.getByRole('button', { name: 'TRY AGAIN' })).toBeTruthy();
        expect(screen.queryByText('FETCHING MORE')).toBeNull();
    });

    it('should_load_the_first_page_when_try_again_is_pressed', async () => {
        fetchPage
            .mockRejectedValueOnce(new Error('The request failed.'))
            .mockResolvedValueOnce(page(posts('a'), null));
        await renderList();

        await userEvent.click(screen.getByRole('button', { name: 'TRY AGAIN' }));

        expect(fetchPage).toHaveBeenCalledTimes(2);
        expect(fetchPage).toHaveBeenLastCalledWith({
            cursor: null,
            size: 20,
        });
        expect(renderedPostTexts()).toHaveLength(1);
        expect(screen.queryByText('SIGNAL LOST')).toBeNull();
    });

    it('should_keep_the_posts_and_show_signal_lost_when_a_later_page_fails', async () => {
        fetchPage
            .mockResolvedValueOnce(page(
                posts('a', 'b'),
                'c1',
            ))
            .mockRejectedValueOnce(new Error('The request failed.'));
        await renderList();

        await intersect(currentSentinel());

        expect(renderedPostTexts()).toHaveLength(2);
        expect(screen.getByText('SIGNAL LOST')).toBeTruthy();
    });

    it('should_not_ask_again_by_itself_when_the_last_page_failed_and_the_sentinel_is_still_visible', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockRejectedValueOnce(new Error('The request failed.'));
        await renderList();
        await intersect(currentSentinel());

        await intersect(currentSentinel());
        await intersect(currentSentinel());

        expect(fetchPage).toHaveBeenCalledTimes(2);
    });

    it('should_ask_for_the_same_cursor_again_when_try_again_is_pressed_after_a_later_page_failed', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockRejectedValueOnce(new Error('The request failed.'))
            .mockResolvedValueOnce(page(posts('b'), null));
        await renderList();
        await intersect(currentSentinel());

        await userEvent.click(screen.getByRole('button', { name: 'TRY AGAIN' }));

        expect(fetchPage).toHaveBeenLastCalledWith({
            cursor: 'c1',
            size: 20,
        });
        expect(renderedPostTexts()).toHaveLength(2);
        expect(screen.getByText(END_TEXT)).toBeTruthy();
    });
});

describe('paging by scrolling', () => {
    it('should_watch_for_the_bottom_of_the_list_500px_ahead_of_the_viewport', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), 'c1'));

        await renderList();

        expect(IntersectionObserverDouble.active.at(-1)?.rootMargin).toBe('0px 0px 500px 0px');
    });

    it('should_ask_for_the_next_page_with_the_cursor_when_the_sentinel_comes_into_range', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockResolvedValueOnce(page(posts('b'), 'c2'));
        await renderList();

        await intersect(currentSentinel());

        expect(fetchPage).toHaveBeenLastCalledWith({
            cursor: 'c1',
            size: 20,
        });
        expect(renderedPostTexts()).toHaveLength(2);
    });

    it('should_add_the_next_page_below_the_posts_already_shown', async () => {
        fetchPage
            .mockResolvedValueOnce(page(
                posts('a', 'b'),
                'c1',
            ))
            .mockResolvedValueOnce(page(
                posts('c', 'd'),
                null,
            ));
        await renderList();

        await intersect(currentSentinel());

        const letters = renderedPostTexts().map((text) => text.replace(/.*content of (\w).*/, '$1'));

        expect(letters).toEqual(['a', 'b', 'c', 'd']);
    });

    it('should_show_a_post_once_when_a_later_page_repeats_it', async () => {
        fetchPage
            .mockResolvedValueOnce(page(
                posts('a', 'b'),
                'c1',
            ))
            .mockResolvedValueOnce(page(
                posts('b', 'c'),
                null,
            ));
        await renderList();

        await intersect(currentSentinel());

        expect(renderedPostTexts()).toHaveLength(3);
    });

    it('should_send_one_request_when_the_sentinel_reports_again_while_a_page_is_on_its_way', async () => {
        const secondPage = deferred<TweetPage>();
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockReturnValueOnce(secondPage.promise);
        await renderList();

        await intersect(currentSentinel());
        await intersect(currentSentinel());
        await intersect(currentSentinel());

        expect(fetchPage).toHaveBeenCalledTimes(2);
    });

    it('should_show_fetching_more_while_a_later_page_is_on_its_way', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockReturnValueOnce(new Promise<TweetPage>(() => undefined));
        await renderList();

        await intersect(currentSentinel());

        expect(screen.getByText('FETCHING MORE')).toBeTruthy();
        expect(renderedPostTexts()).toHaveLength(1);
    });

    it('should_ask_for_nothing_more_once_the_list_has_ended', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), null));
        await renderList();

        await intersect(currentSentinel());
        await intersect(currentSentinel());

        expect(fetchPage).toHaveBeenCalledTimes(1);
    });

    it('should_ignore_a_report_that_the_sentinel_left_the_range', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
        await renderList();

        await intersect(currentSentinel(), 0);

        expect(fetchPage).toHaveBeenCalledTimes(1);
    });

    it('should_watch_the_sentinel_with_a_fresh_observer_after_a_page_is_added', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockResolvedValueOnce(page(posts('b'), 'c2'));
        await renderList();
        const sentinel = currentSentinel();
        const observerBefore = observersWatching(sentinel)[0];

        await intersect(sentinel);

        expect(observersWatching(sentinel)).toHaveLength(1);
        expect(observersWatching(sentinel)[0]).not.toBe(observerBefore);
    });

    it('should_stop_watching_when_the_list_is_removed', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
        const { unmount } = render(<PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} />);
        await act(async () => undefined);
        const sentinel = currentSentinel();

        unmount();

        expect(observersWatching(sentinel)).toEqual([]);
    });
});

describe('a page that adds nothing new', () => {
    it('should_ask_for_the_next_page_at_once_when_a_page_is_empty_but_has_a_cursor', async () => {
        fetchPage
            .mockResolvedValueOnce(page([], 'c1'))
            .mockResolvedValueOnce(page(posts('a'), null));

        await renderList();

        expect(fetchPage).toHaveBeenCalledTimes(2);
        expect(fetchPage).toHaveBeenLastCalledWith({
            cursor: 'c1',
            size: 20,
        });
        expect(renderedPostTexts()).toHaveLength(1);
    });

    it('should_keep_asking_while_pages_stay_empty_and_have_a_cursor', async () => {
        fetchPage
            .mockResolvedValueOnce(page([], 'c1'))
            .mockResolvedValueOnce(page([], 'c2'))
            .mockResolvedValueOnce(page(posts('a'), 'c3'));

        await renderList();

        expect(fetchPage).toHaveBeenCalledTimes(3);
        expect(renderedPostTexts()).toHaveLength(1);
    });

    it('should_ask_for_the_next_page_at_once_when_a_page_only_repeats_posts_already_shown', async () => {
        fetchPage
            .mockResolvedValueOnce(page(
                posts('a', 'b'),
                'c1',
            ))
            .mockResolvedValueOnce(page(
                posts('a', 'b'),
                'c2',
            ))
            .mockResolvedValueOnce(page(posts('c'), 'c3'));
        await renderList();

        await intersect(currentSentinel());

        expect(fetchPage).toHaveBeenCalledTimes(3);
        expect(renderedPostTexts()).toHaveLength(3);
    });

    it('should_show_the_empty_text_when_the_only_page_is_empty_and_has_no_cursor', async () => {
        fetchPage.mockResolvedValueOnce(page([], null));

        await renderList();

        expect(fetchPage).toHaveBeenCalledTimes(1);
        expect(screen.getByText(EMPTY_TEXT)).toBeTruthy();
    });

    it('should_show_signal_lost_when_the_follow_up_page_fails', async () => {
        fetchPage
            .mockResolvedValueOnce(page([], 'c1'))
            .mockRejectedValueOnce(new Error('The request failed.'));

        await renderList();

        expect(screen.getByText('SIGNAL LOST')).toBeTruthy();
    });
});

describe('the live region', () => {
    it('should_stay_empty_when_the_first_page_arrives', async () => {
        fetchPage.mockResolvedValue(page(
            posts('a', 'b', 'c'),
            'c1',
        ));

        await renderList();

        expect(liveRegion().textContent).toBe('');
    });

    it('should_announce_how_many_posts_a_later_page_added', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockResolvedValueOnce(page(
                posts('b', 'c', 'd', 'e', 'f'),
                null,
            ));
        await renderList();

        await intersect(currentSentinel());

        expect(liveRegion().textContent).toBe('5 more posts loaded');
    });

    it('should_use_the_singular_when_a_later_page_added_one_post', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockResolvedValueOnce(page(posts('b'), null));
        await renderList();

        await intersect(currentSentinel());

        expect(liveRegion().textContent).toBe('1 more post loaded');
    });

    it('should_count_only_the_new_posts_when_a_later_page_repeats_some', async () => {
        fetchPage
            .mockResolvedValueOnce(page(
                posts('a', 'b'),
                'c1',
            ))
            .mockResolvedValueOnce(page(
                posts('b', 'c'),
                null,
            ));
        await renderList();

        await intersect(currentSentinel());

        expect(liveRegion().textContent).toBe('1 more post loaded');
    });

    it('should_clear_while_the_next_page_is_on_its_way_so_the_same_text_is_announced_again', async () => {
        const thirdPage = deferred<TweetPage>();
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockResolvedValueOnce(page(posts('b'), 'c2'))
            .mockReturnValueOnce(thirdPage.promise);
        await renderList();
        await intersect(currentSentinel());
        expect(liveRegion().textContent).toBe('1 more post loaded');

        await intersect(currentSentinel());

        expect(liveRegion().textContent).toBe('');
    });

    it('should_not_take_focus_when_posts_are_appended', async () => {
        fetchPage
            .mockResolvedValueOnce(page(posts('a'), 'c1'))
            .mockResolvedValueOnce(page(posts('b'), null));
        await renderList();
        const before = document.activeElement;

        await intersect(currentSentinel());

        expect(document.activeElement).toBe(before);
    });
});

describe('the clock', () => {
    it('should_show_the_age_of_each_post_and_move_every_age_forward_after_a_minute', async () => {
        vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
        vi.setSystemTime(new Date('2026-10-04T12:00:00.000Z'));
        fetchPage.mockResolvedValue(page([
            post('a', '2026-10-04T11:55:00.000Z'),
            post('b', '2026-10-04T11:30:00.000Z'),
        ], null));
        await renderList();
        expect(screen.getAllByRole('article').map((article) => article.querySelector('time')?.textContent))
            .toEqual(['5M', '30M']);

        await act(async () => {
            vi.advanceTimersByTime(MINUTE_MS);
        });

        expect(screen.getAllByRole('article').map((article) => article.querySelector('time')?.textContent))
            .toEqual(['6M', '31M']);
    });

    it('should_not_move_the_ages_before_a_minute_has_passed', async () => {
        vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
        vi.setSystemTime(new Date('2026-10-04T12:00:00.000Z'));
        fetchPage.mockResolvedValue(page([post('a', '2026-10-04T11:55:00.000Z')], null));
        await renderList();

        await act(async () => {
            vi.advanceTimersByTime(MINUTE_MS - 1);
        });

        expect(screen.getByRole('article').querySelector('time')?.textContent).toBe('5M');
    });

    it('should_stop_ticking_when_the_list_is_removed', async () => {
        vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
        fetchPage.mockResolvedValue(page([], null));
        const { unmount } = render(<PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} />);
        await act(async () => undefined);

        unmount();

        expect(vi.getTimerCount()).toBe(0);
    });
});

describe('the reader\'s own new posts', () => {
    async function renderWithOwnPosts(ownPosts: TweetItem[]) {
        await act(async () => {
            render(<PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} ownPosts={ownPosts} />);
        });
    }

    function contents(): string[] {
        return screen
            .getAllByRole('article')
            .map((article) => article.querySelector('.post-cell-body')?.textContent?.replace('content of ', '') ?? '');
    }

    it('should_show_the_own_posts_above_the_loaded_ones', async () => {
        fetchPage.mockResolvedValue(page(
            posts('a', 'b'),
            null,
        ));

        await renderWithOwnPosts(posts('mine'));

        expect(contents()).toEqual(['mine', 'a', 'b']);
    });

    it('should_keep_the_order_of_the_own_posts', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), null));

        await renderWithOwnPosts(posts('newest', 'older'));

        expect(contents()).toEqual(['newest', 'older', 'a']);
    });

    it('should_show_the_own_post_once_when_the_loaded_page_has_the_same_id', async () => {
        fetchPage.mockResolvedValue(page(
            posts('a', 'mine', 'b'),
            null,
        ));

        await renderWithOwnPosts(posts('mine'));

        expect(contents()).toEqual(['a', 'mine', 'b']);
    });

    it('should_call_the_list_ended_and_not_empty_when_only_an_own_post_is_there', async () => {
        fetchPage.mockResolvedValue(page([], null));

        await renderWithOwnPosts(posts('mine'));

        expect(screen.getByText(END_TEXT)).toBeTruthy();
        expect(screen.queryByText(EMPTY_TEXT)).toBeNull();
    });

    it('should_not_announce_an_own_post_as_loaded', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), null));

        await renderWithOwnPosts(posts('mine'));

        expect(liveRegion().textContent).toBe('');
    });

    it('should_show_a_post_published_while_the_list_is_open_above_the_loaded_posts', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), null));
        const { rerender } = render(
            <PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} ownPosts={[]} />,
        );
        await act(async () => undefined);

        rerender(<PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} ownPosts={posts('mine')} />);

        expect(contents()).toEqual(['mine', 'a']);
    });

    it('should_not_ask_for_a_page_again_when_an_own_post_is_added', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), null));
        const { rerender } = render(
            <PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} ownPosts={[]} />,
        );
        await act(async () => undefined);

        rerender(<PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} ownPosts={posts('mine')} />);

        expect(fetchPage).toHaveBeenCalledTimes(1);
    });
});

describe('new posts while reading', () => {
    const NEWER_TIME = '2026-10-04T12:30:00.000Z';

    function newer(id: string, createdAt = NEWER_TIME): TweetItem {
        return post(id, createdAt);
    }

    function newPostsButton(): HTMLElement {
        return screen.getByRole('button', { name: 'NEW POSTS' });
    }

    async function tick(milliseconds = MINUTE_MS) {
        await act(async () => {
            vi.advanceTimersByTime(milliseconds);
        });
    }

    function setVisibility(state: DocumentVisibilityState) {
        vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state);
    }

    beforeEach(() => {
        vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('checking', () => {
        it('should_read_the_first_page_without_a_cursor_after_a_minute', async () => {
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            await renderList(true);
            await tick(MINUTE_MS - 1);
            expect(fetchPage).toHaveBeenCalledTimes(1);

            await tick(1);

            expect(fetchPage).toHaveBeenCalledTimes(2);
            expect(fetchPage).toHaveBeenLastCalledWith({
                cursor: null,
                size: 20,
            });
        });

        it('should_check_again_every_minute', async () => {
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            await renderList(true);

            await tick();
            await tick();
            await tick();

            expect(fetchPage).toHaveBeenCalledTimes(4);
        });

        it('should_not_check_when_the_list_does_not_ask_for_it', async () => {
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            await renderList(false);

            await tick(MINUTE_MS * 3);

            expect(fetchPage).toHaveBeenCalledTimes(1);
        });

        it('should_not_check_before_the_first_page_has_arrived', async () => {
            fetchPage.mockReturnValue(new Promise<TweetPage>(() => undefined));
            await renderList(true);

            await tick(MINUTE_MS * 3);

            expect(fetchPage).toHaveBeenCalledTimes(1);
        });

        it('should_not_check_when_the_first_page_failed', async () => {
            fetchPage.mockRejectedValue(new Error('The request failed.'));
            await renderList(true);

            await tick(MINUTE_MS * 3);

            expect(fetchPage).toHaveBeenCalledTimes(1);
        });

        it('should_check_an_empty_feed_too', async () => {
            fetchPage
                .mockResolvedValueOnce(page([], null))
                .mockResolvedValueOnce(page([newer('x')], null));
            await renderList(true);

            await tick();

            expect(newPostsButton()).toBeTruthy();
        });

        it('should_send_one_request_when_a_check_is_still_on_its_way_at_the_next_tick', async () => {
            const slowCheck = deferred<TweetPage>();
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockReturnValueOnce(slowCheck.promise);
            await renderList(true);

            await tick();
            await tick();

            expect(fetchPage).toHaveBeenCalledTimes(2);
        });

        it('should_stay_silent_and_check_again_when_a_check_fails', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockRejectedValueOnce(new Error('The request failed.'))
                .mockResolvedValueOnce(page([newer('x')], null));
            await renderList(true);

            await tick();
            expect(screen.queryByRole('alert')).toBeNull();
            expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();

            await tick();
            expect(newPostsButton()).toBeTruthy();
        });

        it('should_stop_checking_when_the_list_is_removed', async () => {
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            const { unmount } = render(
                <PostList fetchPage={fetchPage} endText={END_TEXT} emptyText={EMPTY_TEXT} shouldCheckForNewPosts />,
            );
            await act(async () => undefined);

            unmount();
            await tick(MINUTE_MS * 3);

            expect(fetchPage).toHaveBeenCalledTimes(1);
        });
    });

    describe('while the tab is hidden', () => {
        it('should_not_check_while_the_tab_is_hidden', async () => {
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            await renderList(true);

            setVisibility('hidden');
            await act(async () => {
                document.dispatchEvent(new Event('visibilitychange'));
            });
            await tick(MINUTE_MS * 3);

            expect(fetchPage).toHaveBeenCalledTimes(1);
        });

        it('should_check_again_a_minute_after_the_tab_is_visible_again', async () => {
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            await renderList(true);
            setVisibility('hidden');
            await act(async () => {
                document.dispatchEvent(new Event('visibilitychange'));
            });
            await tick(MINUTE_MS * 3);

            setVisibility('visible');
            await act(async () => {
                document.dispatchEvent(new Event('visibilitychange'));
            });
            await tick(MINUTE_MS - 1);
            expect(fetchPage).toHaveBeenCalledTimes(1);

            await tick(1);
            expect(fetchPage).toHaveBeenCalledTimes(2);
        });

        it('should_not_start_checking_when_the_list_opens_in_a_hidden_tab_until_it_is_shown', async () => {
            setVisibility('hidden');
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            await renderList(true);
            await tick(MINUTE_MS * 2);
            expect(fetchPage).toHaveBeenCalledTimes(1);

            setVisibility('visible');
            await act(async () => {
                document.dispatchEvent(new Event('visibilitychange'));
            });
            await tick();

            expect(fetchPage).toHaveBeenCalledTimes(2);
        });

        it('should_not_start_a_second_timer_when_the_tab_becomes_visible_twice', async () => {
            fetchPage.mockResolvedValue(page(posts('a'), 'c1'));
            await renderList(true);

            await act(async () => {
                document.dispatchEvent(new Event('visibilitychange'));
                document.dispatchEvent(new Event('visibilitychange'));
            });
            await tick();

            expect(fetchPage).toHaveBeenCalledTimes(2);
        });
    });

    describe('what counts as new', () => {
        it('should_show_the_button_when_the_first_page_has_a_post_above_the_top_one', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'));
            await renderList(true);

            await tick();

            expect(newPostsButton()).toBeTruthy();
        });

        it('should_show_no_button_when_the_first_page_is_the_same_as_what_is_on_screen', async () => {
            fetchPage.mockResolvedValue(page(
                posts('a', 'b'),
                'c1',
            ));
            await renderList(true);

            await tick();

            expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
        });

        it('should_show_no_button_when_the_first_page_only_has_older_posts', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([post('old', '2026-10-04T08:00:00.000Z')], null));
            await renderList(true);

            await tick();

            expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
        });

        it('should_count_a_post_with_the_same_time_and_a_higher_id_as_new', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page(
                    posts('z', 'a'),
                    'c1',
                ));
            await renderList(true);

            await tick();

            expect(newPostsButton()).toBeTruthy();
        });

        it('should_not_count_a_post_that_is_already_further_down_the_list', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page(posts('b'), 'c2'))
                .mockResolvedValueOnce(page([newer('b', NEWER_TIME)], 'c1'));
            await renderList(true);
            await intersect(currentSentinel());

            await tick();

            expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
        });

        it('should_hide_the_button_when_a_later_check_finds_nothing_new', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'))
                .mockResolvedValueOnce(page(posts('a'), 'c1'));
            await renderList(true);
            await tick();
            expect(newPostsButton()).toBeTruthy();

            await tick();

            expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
        });

        it('should_not_count_my_own_new_post_as_new', async () => {
            const mine = newer('mine');
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([mine, ...posts('a')], 'c1'));
            await act(async () => {
                render(
                    <PostList
                        fetchPage={fetchPage}
                        endText={END_TEXT}
                        emptyText={EMPTY_TEXT}
                        ownPosts={[mine]}
                        shouldCheckForNewPosts
                    />,
                );
            });

            await tick();

            expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
        });
    });

    describe('the button', () => {
        it('should_be_a_button_that_does_not_take_focus_when_it_appears', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'));
            await renderList(true);
            const focusBefore = document.activeElement;

            await tick();

            expect(newPostsButton().getAttribute('type')).toBe('button');
            expect(document.activeElement).toBe(focusBefore);
        });

        it('should_put_the_new_posts_above_the_old_ones_in_order_when_it_is_pressed', async () => {
            fetchPage
                .mockResolvedValueOnce(page(
                    posts('a', 'b'),
                    'c1',
                ))
                .mockResolvedValueOnce(page(
                    [newer('x', '2026-10-04T12:40:00.000Z'), newer('y'), ...posts('a', 'b')],
                    'c1',
                ));
            await renderList(true);
            await tick();

            await userEvent.click(newPostsButton());

            expect(postIds()).toEqual(['x', 'y', 'a', 'b']);
        });

        it('should_scroll_to_the_top_and_hide_itself_when_it_is_pressed', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'));
            await renderList(true);
            await tick();

            await userEvent.click(newPostsButton());

            expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 0 });
            expect(screen.queryByRole('button', { name: 'NEW POSTS' })).toBeNull();
        });

        it('should_not_read_a_page_again_when_it_is_pressed_for_a_few_new_posts', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'));
            await renderList(true);
            await tick();

            await userEvent.click(newPostsButton());

            expect(fetchPage).toHaveBeenCalledTimes(2);
        });

        it('should_keep_the_cursor_so_the_next_page_continues_below_the_old_posts', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'))
                .mockResolvedValueOnce(page(posts('b'), null));
            await renderList(true);
            await tick();
            await userEvent.click(newPostsButton());

            await intersect(currentSentinel());

            expect(fetchPage).toHaveBeenLastCalledWith({
                cursor: 'c1',
                size: 20,
            });
            expect(postIds()).toEqual(['x', 'a', 'b']);
        });

        it('should_be_reachable_and_pressable_from_the_keyboard', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'));
            await renderList(true);
            await tick();
            newPostsButton()
                .focus();

            await userEvent.keyboard('{Enter}');

            expect(renderedPostTexts()).toHaveLength(2);
        });

        it('should_use_the_posts_of_the_latest_check_when_a_second_check_found_more', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page([newer('x'), ...posts('a')], 'c1'))
                .mockResolvedValueOnce(page([newer('y', '2026-10-04T12:45:00.000Z'), newer('x'), ...posts('a')], 'c1'));
            await renderList(true);
            await tick();
            await tick();

            await userEvent.click(newPostsButton());

            expect(postIds()).toEqual(['y', 'x', 'a']);
        });
    });

    describe('when the whole page is new', () => {
        function wholePageOfNewPosts(): TweetItem[] {
            return Array.from(
                { length: 20 },
                (_, index) => newer(
                    `n${String(index).padStart(2, '0')}`,
                    `2026-10-04T13:${String(59 - index).padStart(2, '0')}:00.000Z`,
                ),
            );
        }

        it('should_reload_the_list_from_the_top_when_the_button_is_pressed', async () => {
            fetchPage
                .mockResolvedValueOnce(page(
                    posts('a', 'b'),
                    'c1',
                ))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'));
            await renderList(true);
            await tick();

            await userEvent.click(newPostsButton());

            expect(fetchPage).toHaveBeenCalledTimes(3);
            expect(fetchPage).toHaveBeenLastCalledWith({
                cursor: null,
                size: 20,
            });
            expect(renderedPostTexts()).toHaveLength(20);
            expect(screen.queryByText('content of a', { exact: false })).toBeNull();
        });

        it('should_continue_from_the_new_cursor_after_the_reload', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'old-cursor'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'))
                .mockResolvedValueOnce(page(posts('b'), null));
            await renderList(true);
            await tick();
            await userEvent.click(newPostsButton());

            await intersect(currentSentinel());

            expect(fetchPage).toHaveBeenLastCalledWith({
                cursor: 'fresh-cursor',
                size: 20,
            });
        });

        it('should_scroll_to_the_top_after_a_reload_too', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'));
            await renderList(true);
            await tick();

            await userEvent.click(newPostsButton());

            expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 0 });
        });

        it('should_not_announce_the_reloaded_posts_as_more_posts_loaded', async () => {
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'));
            await renderList(true);
            await tick();

            await userEvent.click(newPostsButton());

            expect(liveRegion().textContent).toBe('');
        });

        it('should_drop_a_page_that_was_still_on_its_way_when_the_list_was_reloaded', async () => {
            const stalePage = deferred<TweetPage>();
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockReturnValueOnce(stalePage.promise)
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'));
            await renderList(true);
            await intersect(currentSentinel());
            await tick();

            await userEvent.click(newPostsButton());
            await act(async () => {
                stalePage.resolve(page(posts('stale'), null));
            });

            expect(screen.queryByText('content of stale')).toBeNull();
            expect(renderedPostTexts()).toHaveLength(20);
        });

        it('should_keep_my_own_new_post_in_its_place_after_the_reload', async () => {
            const mine = newer('mine', '2026-10-04T14:30:00.000Z');
            fetchPage
                .mockResolvedValueOnce(page(posts('a'), 'c1'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'))
                .mockResolvedValueOnce(page(wholePageOfNewPosts(), 'fresh-cursor'));
            await act(async () => {
                render(
                    <PostList
                        fetchPage={fetchPage}
                        endText={END_TEXT}
                        emptyText={EMPTY_TEXT}
                        ownPosts={[mine]}
                        shouldCheckForNewPosts
                    />,
                );
            });
            await tick();

            await userEvent.click(newPostsButton());

            expect(renderedPostTexts()[0]).toContain('content of mine');
        });
    });
});

describe('the empty action', () => {
    const EMPTY_ACTION_LABEL = 'FIND PEOPLE';

    async function renderEmptyList(onEmptyAction = vi.fn()) {
        await act(async () => {
            render(
                <PostList
                    fetchPage={fetchPage}
                    endText={END_TEXT}
                    emptyText={EMPTY_TEXT}
                    emptyAction={{
                        label: EMPTY_ACTION_LABEL,
                        onClick: onEmptyAction,
                    }}
                />,
            );
        });

        return { onEmptyAction };
    }

    it('should_show_the_action_button_beside_the_empty_text_when_the_list_is_empty', async () => {
        fetchPage.mockResolvedValue(page([], null));

        await renderEmptyList();

        expect(screen.getByText(EMPTY_TEXT)).toBeTruthy();
        expect(screen.getByRole('button', { name: EMPTY_ACTION_LABEL })).toBeTruthy();
    });

    it('should_call_the_action_when_the_button_is_pressed', async () => {
        fetchPage.mockResolvedValue(page([], null));
        const { onEmptyAction } = await renderEmptyList();

        await act(async () => {
            await userEvent.click(screen.getByRole('button', { name: EMPTY_ACTION_LABEL }));
        });

        expect(onEmptyAction).toHaveBeenCalledTimes(1);
    });

    it('should_not_show_the_action_when_the_list_has_posts', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), null));

        await renderEmptyList();

        expect(screen.queryByRole('button', { name: EMPTY_ACTION_LABEL })).toBeNull();
    });

    it('should_not_show_the_action_while_the_first_page_is_loading', async () => {
        fetchPage.mockReturnValue(new Promise(() => undefined));

        await renderEmptyList();

        expect(screen.queryByRole('button', { name: EMPTY_ACTION_LABEL })).toBeNull();
    });

    it('should_not_show_a_button_when_no_action_is_given', async () => {
        fetchPage.mockResolvedValue(page([], null));

        await renderList();

        expect(screen.getByText(EMPTY_TEXT)).toBeTruthy();
        expect(screen.queryByRole('button')).toBeNull();
    });
});

describe('reloading an empty list', () => {
    function listWithKey(reloadEmptyKey: number) {
        return (
            <PostList
                fetchPage={fetchPage}
                endText={END_TEXT}
                emptyText={EMPTY_TEXT}
                reloadEmptyKey={reloadEmptyKey}
            />
        );
    }

    async function renderListWithKey(reloadEmptyKey: number) {
        let rendered: ReturnType<typeof render> | null = null;

        await act(async () => {
            rendered = render(listWithKey(reloadEmptyKey));
        });

        return async (nextKey: number) => {
            await act(async () => {
                rendered?.rerender(listWithKey(nextKey));
            });
        };
    }

    it('should_not_read_again_when_the_list_is_first_shown', async () => {
        fetchPage.mockResolvedValue(page([], null));

        await renderListWithKey(3);

        expect(fetchPage).toHaveBeenCalledTimes(1);
    });

    it('should_read_the_first_page_again_when_the_key_changes_and_the_list_is_empty', async () => {
        fetchPage
            .mockResolvedValueOnce(page([], null))
            .mockResolvedValueOnce(page(posts('a'), null));
        const changeKey = await renderListWithKey(0);

        await changeKey(1);

        expect(fetchPage).toHaveBeenCalledTimes(2);
        expect(vi.mocked(fetchPage).mock.calls[1][0]).toEqual({
            cursor: null,
            size: 20,
        });
        expect(postIds()).toEqual(['a']);
        expect(screen.queryByText(EMPTY_TEXT)).toBeNull();
    });

    it('should_keep_the_posts_and_read_nothing_when_the_key_changes_and_the_list_is_not_empty', async () => {
        fetchPage.mockResolvedValue(page(posts('a'), null));
        const changeKey = await renderListWithKey(0);

        await changeKey(1);

        expect(fetchPage).toHaveBeenCalledTimes(1);
        expect(postIds()).toEqual(['a']);
    });

    it('should_read_nothing_when_the_key_stays_the_same_on_a_new_render', async () => {
        fetchPage.mockResolvedValue(page([], null));
        const changeKey = await renderListWithKey(2);

        await changeKey(2);

        expect(fetchPage).toHaveBeenCalledTimes(1);
    });

    it('should_not_start_a_second_read_when_the_first_page_is_still_on_its_way', async () => {
        fetchPage.mockReturnValue(new Promise(() => undefined));
        const changeKey = await renderListWithKey(0);

        await changeKey(1);

        expect(fetchPage).toHaveBeenCalledTimes(1);
    });

    it('should_not_read_again_when_the_first_page_failed', async () => {
        fetchPage.mockRejectedValue(new Error('The request failed.'));
        const changeKey = await renderListWithKey(0);

        await changeKey(1);

        expect(fetchPage).toHaveBeenCalledTimes(1);
        expect(screen.getByText('SIGNAL LOST')).toBeTruthy();
    });

    it('should_read_again_for_every_change_while_the_list_stays_empty', async () => {
        fetchPage.mockResolvedValue(page([], null));
        const changeKey = await renderListWithKey(0);

        await changeKey(1);
        await changeKey(2);

        expect(fetchPage).toHaveBeenCalledTimes(3);
    });
});


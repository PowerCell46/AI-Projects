import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IntersectionObserverDouble, intersect, observersWatching } from '../../../test/intersectionObserver';
import { VIEW_BATCH_INTERVAL_MS, ViewReporter } from '../../../utils/viewReporter';
import { useViewTracking } from './useViewTracking';


interface TrackedPostsProps {
    reporter: ViewReporter;
    tweetIds: string[];
}

const DWELL_MS = 1000;

const sendViews = vi.fn<(tweetIds: string[], options: { isKeepalive?: boolean }) => Promise<void>>();

function TrackedPosts({ reporter, tweetIds }: TrackedPostsProps) {
    const trackView = useViewTracking(reporter);

    return (
        <ul>
            {tweetIds.map((tweetId) => (
                <li key={tweetId} data-tweet-id={tweetId} ref={trackView}>{tweetId}</li>
            ))}
        </ul>
    );
}

let reporter: ViewReporter;

function post(tweetId: string): HTMLElement {
    return screen.getByText(tweetId);
}

async function renderPosts(...tweetIds: string[]) {
    return render(<TrackedPosts reporter={reporter} tweetIds={tweetIds} />);
}

async function advance(milliseconds: number) {
    await act(async () => {
        vi.advanceTimersByTime(milliseconds);
    });
}

async function reportedIds(): Promise<string[]> {
    await advance(VIEW_BATCH_INTERVAL_MS);

    return sendViews.mock.calls.flatMap((call) => call[0]);
}

function setVisibility(state: DocumentVisibilityState) {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state);
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    sendViews
        .mockReset()
        .mockResolvedValue(undefined);
    reporter = new ViewReporter(sendViews);
});

afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
});

describe('watching', () => {
    it('should_watch_each_post_for_being_half_visible', async () => {
        await renderPosts('a', 'b');

        expect(observersWatching(post('a'))[0].thresholds).toEqual([0.5]);
        expect(observersWatching(post('b'))[0].thresholds).toEqual([0.5]);
    });

    it('should_use_one_observer_for_all_the_posts', async () => {
        await renderPosts('a', 'b', 'c');

        expect(IntersectionObserverDouble.active).toHaveLength(1);
    });

    it('should_stop_watching_a_post_that_is_removed', async () => {
        const { rerender } = await renderPosts('a', 'b');
        const removed = post('b');

        rerender(<TrackedPosts reporter={reporter} tweetIds={['a']} />);

        expect(observersWatching(removed)).toEqual([]);
        expect(observersWatching(post('a'))).toHaveLength(1);
    });

    it('should_keep_watching_a_post_that_stays_when_the_list_changes', async () => {
        const { rerender } = await renderPosts('a');
        const observerBefore = observersWatching(post('a'))[0];

        rerender(<TrackedPosts reporter={reporter} tweetIds={['b', 'a']} />);

        expect(observersWatching(post('a'))[0]).toBe(observerBefore);
    });
});

describe('dwelling', () => {
    it('should_count_a_post_that_stays_half_visible_for_a_second', async () => {
        await renderPosts('a');

        await intersect(post('a'), 0.5);
        await advance(DWELL_MS);

        expect(await reportedIds()).toEqual(['a']);
    });

    it('should_not_count_a_post_before_a_second_has_passed', async () => {
        await renderPosts('a');

        await intersect(post('a'), 1);
        await advance(DWELL_MS - 1);

        expect(await reportedIds()).toEqual([]);
    });

    it('should_not_count_a_post_that_is_less_than_half_visible', async () => {
        await renderPosts('a');

        await intersect(post('a'), 0.49);
        await advance(DWELL_MS * 3);

        expect(await reportedIds()).toEqual([]);
    });

    it('should_not_count_a_post_that_scrolled_away_before_the_second_was_up', async () => {
        await renderPosts('a');
        await intersect(post('a'), 0.8);
        await advance(DWELL_MS - 100);

        await intersect(post('a'), 0.2);
        await advance(DWELL_MS * 3);

        expect(await reportedIds()).toEqual([]);
    });

    it('should_start_a_full_second_again_when_a_post_comes_back_into_view', async () => {
        await renderPosts('a');
        await intersect(post('a'), 0.8);
        await advance(DWELL_MS - 100);
        await intersect(post('a'), 0);
        await intersect(post('a'), 0.8);

        await advance(DWELL_MS - 1);
        expect(reporter.hasReported('a')).toBe(false);

        await advance(1);
        expect(reporter.hasReported('a')).toBe(true);
    });

    it('should_not_restart_the_second_when_the_observer_reports_the_same_post_again', async () => {
        await renderPosts('a');
        await intersect(post('a'), 0.8);
        await advance(DWELL_MS - 500);

        await intersect(post('a'), 0.9);
        await advance(500);

        expect(reporter.hasReported('a')).toBe(true);
    });

    it('should_time_each_post_on_its_own', async () => {
        await renderPosts('a', 'b');
        await intersect(post('a'), 1);
        await advance(600);
        await intersect(post('b'), 1);
        await advance(400);

        expect(reporter.hasReported('a')).toBe(true);
        expect(reporter.hasReported('b')).toBe(false);

        await advance(600);
        expect(reporter.hasReported('b')).toBe(true);
    });

    it('should_not_time_a_post_again_once_it_was_reported', async () => {
        await renderPosts('a');
        await intersect(post('a'), 1);
        await advance(DWELL_MS);
        await intersect(post('a'), 0);

        await intersect(post('a'), 1);
        await advance(DWELL_MS);

        expect(await reportedIds()).toEqual(['a']);
        expect(sendViews).toHaveBeenCalledTimes(1);
    });

    it('should_not_count_a_post_that_is_removed_during_its_second', async () => {
        const { rerender } = await renderPosts('a');
        await intersect(post('a'), 1);
        await advance(DWELL_MS - 100);

        rerender(<TrackedPosts reporter={reporter} tweetIds={[]} />);
        await advance(DWELL_MS * 2);

        expect(reporter.hasReported('a')).toBe(false);
    });

    it('should_not_count_posts_after_the_list_is_unmounted', async () => {
        const { unmount } = await renderPosts('a');
        await intersect(post('a'), 1);

        unmount();
        await advance(DWELL_MS * 2);

        expect(reporter.hasReported('a')).toBe(false);
    });
});

describe('reporting', () => {
    it('should_send_the_counted_posts_in_one_request_five_seconds_after_the_first', async () => {
        await renderPosts('a', 'b');
        await intersect(post('a'), 1);
        await advance(DWELL_MS);
        await intersect(post('b'), 1);
        await advance(DWELL_MS);

        await advance(VIEW_BATCH_INTERVAL_MS - DWELL_MS);

        expect(sendViews).toHaveBeenCalledTimes(1);
        expect(sendViews.mock.calls[0][0]).toEqual(['a', 'b']);
    });

    it('should_send_nothing_when_no_post_was_counted', async () => {
        await renderPosts('a');

        await advance(VIEW_BATCH_INTERVAL_MS * 3);

        expect(sendViews).not.toHaveBeenCalled();
    });
});

describe('closing the page', () => {
    async function renderWithOneCountedPost() {
        await renderPosts('a');
        await intersect(post('a'), 1);
        await advance(DWELL_MS);
    }

    it('should_send_at_once_with_keepalive_when_the_tab_is_hidden', async () => {
        await renderWithOneCountedPost();
        setVisibility('hidden');

        await act(async () => {
            document.dispatchEvent(new Event('visibilitychange'));
        });

        expect(sendViews).toHaveBeenCalledExactlyOnceWith(
            ['a'],
            { isKeepalive: true },
        );
    });

    it('should_send_nothing_when_the_tab_becomes_visible_again', async () => {
        await renderWithOneCountedPost();
        setVisibility('visible');

        await act(async () => {
            document.dispatchEvent(new Event('visibilitychange'));
        });

        expect(sendViews).not.toHaveBeenCalled();
    });

    it('should_send_at_once_with_keepalive_when_the_page_is_hidden_by_navigation', async () => {
        await renderWithOneCountedPost();

        await act(async () => {
            window.dispatchEvent(new Event('pagehide'));
        });

        expect(sendViews).toHaveBeenCalledExactlyOnceWith(
            ['a'],
            { isKeepalive: true },
        );
    });

    it('should_not_send_the_batch_again_when_the_interval_ends_after_a_hide_flush', async () => {
        await renderWithOneCountedPost();
        await act(async () => {
            window.dispatchEvent(new Event('pagehide'));
        });

        await advance(VIEW_BATCH_INTERVAL_MS * 2);

        expect(sendViews).toHaveBeenCalledTimes(1);
    });

    it('should_stop_listening_for_the_page_closing_when_the_list_is_unmounted', async () => {
        const flush = vi.spyOn(reporter, 'flush');
        const { unmount } = await renderPosts('a');

        unmount();
        await act(async () => {
            window.dispatchEvent(new Event('pagehide'));
            setVisibility('hidden');
            document.dispatchEvent(new Event('visibilitychange'));
        });

        expect(flush).not.toHaveBeenCalled();
    });
});

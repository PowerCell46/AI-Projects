import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MAX_VIEWS_PER_REQUEST, VIEW_BATCH_INTERVAL_MS, ViewReporter } from './viewReporter';


const sendViews = vi.fn<(tweetIds: string[], options: { isKeepalive?: boolean }) => Promise<void>>();

let reporter: ViewReporter;

function idsSent(callIndex: number): string[] {
    return sendViews.mock.calls[callIndex][0];
}

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    sendViews
        .mockReset()
        .mockResolvedValue(undefined);
    reporter = new ViewReporter(sendViews);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('batching', () => {
    it('should_send_nothing_before_the_batch_interval_has_passed', () => {
        reporter.record('a');

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS - 1);

        expect(sendViews).not.toHaveBeenCalled();
    });

    it('should_send_everything_recorded_in_one_request_when_the_interval_ends', () => {
        reporter.record('a');
        vi.advanceTimersByTime(2000);
        reporter.record('b');
        reporter.record('c');

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS - 2000);

        expect(sendViews).toHaveBeenCalledTimes(1);
        expect(idsSent(0)).toEqual(['a', 'b', 'c']);
    });

    it('should_send_with_no_keepalive_when_the_interval_ends', () => {
        reporter.record('a');

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        expect(sendViews.mock.calls[0][1]).toEqual({});
    });

    it('should_start_a_new_interval_when_a_post_is_recorded_after_a_batch_was_sent', () => {
        reporter.record('a');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);
        reporter.record('b');

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS - 1);
        expect(sendViews).toHaveBeenCalledTimes(1);

        vi.advanceTimersByTime(1);
        expect(sendViews).toHaveBeenCalledTimes(2);
        expect(idsSent(1)).toEqual(['b']);
    });

    it('should_send_nothing_when_the_queue_is_empty_at_the_end_of_the_interval', () => {
        reporter.record('a');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS * 3);

        expect(sendViews).toHaveBeenCalledTimes(1);
    });

    it.each([
        [50, [50]],
        [51, [50, 1]],
        [120, [50, 50, 20]],
    ])('should_split_%i_queued_posts_into_requests_of_%j', (count, expectedSizes) => {
        Array.from(
            { length: count },
            (_, index) => `tweet-${index}`,
        ).forEach((id) => reporter.record(id));

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        expect(sendViews.mock.calls.map((call) => call[0].length)).toEqual(expectedSizes);
        expect(MAX_VIEWS_PER_REQUEST).toBe(50);
    });

    it('should_keep_the_order_of_the_posts_across_the_requests', () => {
        const ids = Array.from(
            { length: 60 },
            (_, index) => `tweet-${index}`,
        );
        ids.forEach((id) => reporter.record(id));

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        expect([...idsSent(0), ...idsSent(1)]).toEqual(ids);
    });
});

describe('once per post', () => {
    it('should_report_a_post_once_when_it_is_recorded_twice_in_one_batch', () => {
        reporter.record('a');
        reporter.record('a');

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        expect(idsSent(0)).toEqual(['a']);
    });

    it('should_not_report_a_post_again_when_it_is_recorded_after_its_batch_was_sent', () => {
        reporter.record('a');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        reporter.record('a');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        expect(sendViews).toHaveBeenCalledTimes(1);
    });

    it('should_know_which_posts_it_has_reported', () => {
        reporter.record('a');

        expect(reporter.hasReported('a')).toBe(true);
        expect(reporter.hasReported('b')).toBe(false);
    });
});

describe('flushing', () => {
    it('should_send_at_once_with_keepalive_when_flushed_for_a_closing_page', () => {
        reporter.record('a');
        reporter.record('b');

        reporter.flush({ isKeepalive: true });

        expect(sendViews).toHaveBeenCalledExactlyOnceWith(
            ['a', 'b'],
            { isKeepalive: true },
        );
    });

    it('should_not_send_the_batch_again_when_the_interval_ends_after_a_flush', () => {
        reporter.record('a');
        reporter.flush({ isKeepalive: true });

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS * 2);

        expect(sendViews).toHaveBeenCalledTimes(1);
    });

    it('should_send_nothing_when_flushed_with_an_empty_queue', () => {
        reporter.flush({ isKeepalive: true });

        expect(sendViews).not.toHaveBeenCalled();
    });

    it('should_start_a_new_interval_for_posts_recorded_after_a_flush', () => {
        reporter.record('a');
        reporter.flush({ isKeepalive: true });
        reporter.record('b');

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        expect(sendViews).toHaveBeenCalledTimes(2);
        expect(idsSent(1)).toEqual(['b']);
    });

    it('should_split_a_flush_into_requests_of_at_most_50_posts_with_keepalive_on_each', () => {
        Array.from(
            { length: 70 },
            (_, index) => `tweet-${index}`,
        ).forEach((id) => reporter.record(id));

        reporter.flush({ isKeepalive: true });

        expect(sendViews.mock.calls.map((call) => call[0].length)).toEqual([50, 20]);
        expect(sendViews.mock.calls.every((call) => call[1].isKeepalive === true)).toBe(true);
    });
});

describe('failures', () => {
    it('should_drop_a_failed_batch_without_throwing', async () => {
        sendViews.mockRejectedValue(new Error('The request failed.'));
        reporter.record('a');

        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);
        await Promise.resolve();

        expect(sendViews).toHaveBeenCalledTimes(1);
    });

    it('should_not_send_a_failed_batch_again_and_not_ask_for_its_posts_again', async () => {
        sendViews
            .mockRejectedValueOnce(new Error('The request failed.'))
            .mockResolvedValue(undefined);
        reporter.record('a');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);
        await Promise.resolve();

        reporter.record('a');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS * 2);

        expect(sendViews).toHaveBeenCalledTimes(1);
    });

    it('should_report_later_posts_after_an_earlier_batch_failed', async () => {
        sendViews
            .mockRejectedValueOnce(new Error('The request failed.'))
            .mockResolvedValue(undefined);
        reporter.record('a');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);
        await Promise.resolve();

        reporter.record('b');
        vi.advanceTimersByTime(VIEW_BATCH_INTERVAL_MS);

        expect(idsSent(1)).toEqual(['b']);
    });
});

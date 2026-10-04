import { describe, expect, it } from 'vitest';
import { formatPostTime } from './relativeTime';


const SECOND_MS = 1000;

const MINUTE_MS = 60 * SECOND_MS;

const HOUR_MS = 60 * MINUTE_MS;

const DAY_MS = 24 * HOUR_MS;

// A local-time date, so the calendar results do not depend on the machine's time zone.
const NOW = new Date(2026, 9, 4, 12, 0, 0);

function isoAgo(ageMs: number): string {
    return new Date(NOW.getTime() - ageMs).toISOString();
}

describe('formatPostTime', () => {
    describe('minutes, hours and days', () => {
        it.each([
            [0, 'NOW'],
            [59 * SECOND_MS, 'NOW'],
            [MINUTE_MS, '1M'],
            [5 * MINUTE_MS, '5M'],
            [59 * MINUTE_MS + 59 * SECOND_MS, '59M'],
            [HOUR_MS, '1H'],
            [3 * HOUR_MS, '3H'],
            [23 * HOUR_MS + 59 * MINUTE_MS, '23H'],
            [DAY_MS, '1D'],
            [2 * DAY_MS, '2D'],
            [6 * DAY_MS + 23 * HOUR_MS, '6D'],
        ])('should_show_the_age_of_%i_ms_as_%s', (ageMs, expected) => {
            expect(formatPostTime(isoAgo(ageMs), NOW)).toBe(expected);
        });
    });

    describe('calendar dates', () => {
        it('should_show_the_day_and_month_when_the_post_is_a_week_old', () => {
            expect(formatPostTime(isoAgo(7 * DAY_MS), NOW)).toBe('27 SEP');
        });

        it('should_show_the_day_and_month_without_a_padding_zero_when_the_day_is_below_10', () => {
            const postedAt = new Date(2026, 2, 5, 9, 0, 0).toISOString();

            expect(formatPostTime(postedAt, NOW)).toBe('5 MAR');
        });

        it('should_add_the_year_when_the_post_is_from_another_year', () => {
            const postedAt = new Date(2025, 8, 12, 9, 0, 0).toISOString();

            expect(formatPostTime(postedAt, NOW)).toBe('12 SEP 2025');
        });

        it('should_add_the_year_when_the_post_is_a_week_old_but_crosses_new_year', () => {
            const newYear = new Date(2026, 0, 3, 12, 0, 0);
            const postedAt = new Date(2025, 11, 20, 12, 0, 0).toISOString();

            expect(formatPostTime(postedAt, newYear)).toBe('20 DEC 2025');
        });
    });

    describe('clock skew', () => {
        it('should_show_now_when_the_post_is_stamped_ahead_of_the_clock', () => {
            const postedAt = new Date(NOW.getTime() + 5 * MINUTE_MS).toISOString();

            expect(formatPostTime(postedAt, NOW)).toBe('NOW');
        });
    });

    describe('fraction digits', () => {
        it.each([
            '2026-10-04T11:55:00Z',
            '2026-10-04T11:55:00.000Z',
            '2026-10-04T11:55:00.000123Z',
        ])('should_read_the_timestamp_%s', (createdAt) => {
            const now = new Date('2026-10-04T12:00:00Z');

            expect(formatPostTime(createdAt, now)).toBe('5M');
        });
    });
});

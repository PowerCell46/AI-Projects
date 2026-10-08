import { describe, expect, it } from 'vitest';
import { formatFullCount, formatJoinDate, pluralLabel } from './profileFormat';


describe('formatJoinDate', () => {
    it.each([
        ['2026-10-04T10:00:00Z', 'JOINED OCT 2026'],
        ['2026-01-01T00:00:00Z', 'JOINED JAN 2026'],
        ['2025-12-31T23:59:59Z', 'JOINED DEC 2025'],
    ])('should_read_%s_as_%s', (createdAt, expected) => {
        expect(formatJoinDate(createdAt)).toBe(expected);
    });
});

describe('formatFullCount', () => {
    it.each([
        [0, '0'],
        [7, '7'],
        [999, '999'],
        [1204, '1,204'],
        [1234567, '1,234,567'],
    ])('should_write_%s_as_%s', (count, expected) => {
        expect(formatFullCount(count)).toBe(expected);
    });
});

describe('pluralLabel', () => {
    it('should_use_the_singular_only_for_one', () => {
        expect(pluralLabel(1, 'FOLLOWER', 'FOLLOWERS')).toBe('FOLLOWER');
        expect(pluralLabel(0, 'FOLLOWER', 'FOLLOWERS')).toBe('FOLLOWERS');
        expect(pluralLabel(2, 'FOLLOWER', 'FOLLOWERS')).toBe('FOLLOWERS');
    });
});

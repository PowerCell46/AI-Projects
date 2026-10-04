import { describe, expect, it } from 'vitest';
import { formatFollowers } from './followers';


describe('formatFollowers', () => {
    it.each([
        [0, '0'],
        [1, '1'],
        [999, '999'],
        [1_000, '1K'],
        [1_240, '1.2K'],
        [1_999, '1.9K'],
        [2_000, '2K'],
        [9_999, '9.9K'],
        [10_000, '10K'],
        [12_400, '12K'],
        [999_999, '999K'],
        [1_000_000, '1M'],
        [1_250_000, '1.2M'],
        [9_999_999, '9.9M'],
        [10_000_000, '10M'],
        [34_000_000, '34M'],
    ])('should_format_%i_as_%s', (count, expected) => {
        expect(formatFollowers(count)).toBe(expected);
    });

    it('should_round_down_and_never_up', () => {
        expect(formatFollowers(1_299)).toBe('1.2K');
        expect(formatFollowers(12_999)).toBe('12K');
    });
});

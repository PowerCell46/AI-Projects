import { describe, expect, it } from 'vitest';
import { formatCount } from './count';


describe('formatCount', () => {
    it.each([
        [0, '0'],
        [1, '1'],
        [999, '999'],
        [1_000, '1K'],
        [1_240, '1.2K'],
        [1_299, '1.2K'],
        [1_999, '1.9K'],
        [2_000, '2K'],
        [9_999, '9.9K'],
        [10_000, '10K'],
        [12_400, '12K'],
        [12_999, '12K'],
        [999_999, '999K'],
        [1_000_000, '1M'],
        [1_250_000, '1.2M'],
        [2_340_000, '2.3M'],
        [9_999_999, '9.9M'],
        [10_000_000, '10M'],
        [34_000_000, '34M'],
    ])('should_format_%i_as_%s', (count, expected) => {
        expect(formatCount(count)).toBe(expected);
    });
});

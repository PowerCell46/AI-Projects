import { describe, expect, it } from 'vitest';
import { countCharacters, formatCount } from '../../../src/utils/text/count';


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

describe('countCharacters', () => {
    it.each([
        ['', 0],
        ['hello', 5],
        ['  hello  ', 5],
        ['\n\t ', 0],
        ['a b', 3],
        ['a\nb', 3],
    ])('should_count_%j_as_%i_characters', (text, expected) => {
        expect(countCharacters(text)).toBe(expected);
    });

    it('should_count_an_emoji_outside_the_basic_plane_as_one_character', () => {
        expect(countCharacters('😀')).toBe(1);
        expect('😀'.length).toBe(2);
    });

    it('should_count_a_letter_with_a_combining_mark_as_two_code_points', () => {
        expect(countCharacters('e\u0301')).toBe(2);
    });
});

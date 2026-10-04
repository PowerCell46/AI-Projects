import { describe, expect, it } from 'vitest';
import { truncateBio } from './bio';


const LONG_WORD = 'a'.repeat(60);

describe('truncateBio', () => {
    it('should_return_the_bio_as_it_is_when_it_is_shorter_than_the_limit', () => {
        expect(truncateBio('Builds boats.')).toBe('Builds boats.');
    });

    it('should_keep_a_bio_of_exactly_50_code_points_whole', () => {
        const bio = `${'word '.repeat(9)}12345`;

        expect(Array.from(bio)).toHaveLength(50);
        expect(truncateBio(bio)).toBe(bio);
    });

    it('should_cut_a_bio_of_51_code_points_at_the_last_space_and_add_an_ellipsis', () => {
        const bio = `${'word '.repeat(9)}123456`;

        expect(Array.from(bio)).toHaveLength(51);
        expect(truncateBio(bio)).toBe(`${'word '.repeat(8)}word…`);
    });

    it('should_cut_a_long_bio_at_the_last_space_within_the_first_50_code_points', () => {
        const bio = 'The quick brown fox jumps over the lazy dog and keeps running far away';

        expect(truncateBio(bio)).toBe('The quick brown fox jumps over the lazy dog and…');
    });

    it('should_cut_a_first_word_longer_than_the_limit_at_50_and_add_an_ellipsis', () => {
        expect(truncateBio(`${LONG_WORD} and more`)).toBe(`${'a'.repeat(50)}…`);
    });

    it('should_cut_one_long_word_with_no_space_at_50_and_add_an_ellipsis', () => {
        expect(truncateBio(LONG_WORD)).toBe(`${'a'.repeat(50)}…`);
    });

    it('should_count_an_emoji_as_one_code_point', () => {
        const bio = '😀'.repeat(50);

        expect(truncateBio(bio)).toBe(bio);
    });

    it('should_cut_after_50_emoji_when_the_bio_has_51_and_no_space', () => {
        expect(truncateBio('😀'.repeat(51))).toBe(`${'😀'.repeat(50)}…`);
    });

    it('should_not_split_an_emoji_in_the_middle_when_it_is_cut', () => {
        const bio = `${'a'.repeat(49)}😀😀`;

        expect(truncateBio(bio)).toBe(`${'a'.repeat(49)}😀…`);
    });

    it('should_strip_bidi_controls_before_counting', () => {
        const bio = `‮${'a'.repeat(50)}⁩`;

        expect(truncateBio(bio)).toBe('a'.repeat(50));
    });

    it('should_turn_line_breaks_into_single_spaces', () => {
        expect(truncateBio('first line\nsecond line\r\nthird')).toBe('first line second line third');
    });

    it('should_collapse_runs_of_whitespace_into_one_space', () => {
        expect(truncateBio('a \t  b\n\n\nc')).toBe('a b c');
    });

    it('should_trim_the_ends', () => {
        expect(truncateBio('  \n hello \t ')).toBe('hello');
    });

    it('should_return_null_when_the_bio_is_only_whitespace', () => {
        expect(truncateBio(' \n\t  ')).toBeNull();
    });

    it('should_return_null_when_the_bio_is_only_bidi_controls', () => {
        expect(truncateBio('‮⁦⁩')).toBeNull();
    });

    it('should_return_null_when_the_bio_is_empty', () => {
        expect(truncateBio('')).toBeNull();
    });

    it('should_return_null_when_there_is_no_bio', () => {
        expect(truncateBio(null)).toBeNull();
    });
});

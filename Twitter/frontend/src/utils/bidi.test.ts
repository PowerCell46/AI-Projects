import { describe, expect, it } from 'vitest';
import { stripBidiControls } from './bidi';


describe('stripBidiControls', () => {
    it.each([
        ['‪', 'left-to-right embedding'],
        ['‫', 'right-to-left embedding'],
        ['‬', 'pop directional formatting'],
        ['‭', 'left-to-right override'],
        ['‮', 'right-to-left override'],
        ['⁦', 'left-to-right isolate'],
        ['⁧', 'right-to-left isolate'],
        ['⁨', 'first strong isolate'],
        ['⁩', 'pop directional isolate'],
    ])('should_remove_%j_the_%s', (control) => {
        expect(stripBidiControls(`a${control}b`)).toBe('ab');
    });

    it('should_remove_every_control_when_several_are_mixed_into_the_text', () => {
        expect(stripBidiControls('‮evil‬ and ⁦more⁩')).toBe('evil and more');
    });

    it.each([
        ['‎', 'left-to-right mark'],
        ['‏', 'right-to-left mark'],
        ['‍', 'zero-width joiner'],
    ])('should_keep_the_%j_%s', (mark) => {
        expect(stripBidiControls(`a${mark}b`)).toBe(`a${mark}b`);
    });

    it('should_keep_right_to_left_scripts_emoji_and_line_breaks', () => {
        const text = 'שלום عالم 👨‍👩‍👧\nsecond line';

        expect(stripBidiControls(text)).toBe(text);
    });

    it('should_return_an_empty_string_when_the_text_is_empty', () => {
        expect(stripBidiControls('')).toBe('');
    });
});

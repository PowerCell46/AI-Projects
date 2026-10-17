import { describe, expect, it } from 'vitest';
import { wrapIndex } from '../../../src/utils/navigation/wrapIndex';


describe('wrapIndex', () => {
    it.each([
        [0, 1, 3, 1],
        [1, 1, 3, 2],
        [2, 1, 3, 0],
        [0, -1, 3, 2],
        [2, -1, 3, 1],
        [0, 1, 1, 0],
    ])('should_step_from_%i_by_%i_in_%i_items_to_%i', (currentIndex, step, itemCount, expected) => {
        expect(wrapIndex(currentIndex, step, itemCount)).toBe(expected);
    });
});

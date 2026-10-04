import { describe, expect, it } from 'vitest';
import { depthPositionPercent, isMajorTick, tickLabelMetres, tickPositionPercent } from './depth';


describe('tickPositionPercent', () => {
    it.each([
        [0, 6],
        [4, 25.9722],
        [26, 92],
    ])('should_place_tick_%i_at_%f_percent', (tickIndex, expectedPercent) => {
        expect(tickPositionPercent(tickIndex)).toBeCloseTo(expectedPercent, 3);
    });
});

describe('tickLabelMetres', () => {
    it.each([
        [0, 0],
        [4, 600],
        [26, 11000],
    ])('should_label_tick_%i_with_%i_metres', (tickIndex, expectedMetres) => {
        expect(tickLabelMetres(tickIndex)).toBe(expectedMetres);
    });
});

describe('isMajorTick', () => {
    it('should_mark_seven_of_the_27_ticks_as_major', () => {
        const ticks = Array.from(
            { length: 27 },
            (_, tickIndex) => tickIndex,
        );

        expect(ticks.filter(isMajorTick)).toEqual([0, 4, 8, 12, 16, 20, 24]);
    });
});

describe('depthPositionPercent', () => {
    it.each([
        [140, 4900, 20.3709],
        [3860, 4900, 82.2711],
        [140, 10910, 15.6061],
        [3860, 10910, 56.9829],
        [9720, 10910, 87.1442],
        [4900, 4900, 92],
        [0, 4900, 6],
    ])('should_place_depth_%i_of_%i_at_%f_percent', (depthMetres, seafloorDepthMetres, expectedPercent) => {
        expect(depthPositionPercent(depthMetres, seafloorDepthMetres)).toBeCloseTo(expectedPercent, 3);
    });
});

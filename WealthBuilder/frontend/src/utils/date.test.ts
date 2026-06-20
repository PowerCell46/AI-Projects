import { describe, it, expect } from 'vitest';
import { isoToDisplay, toIso } from './date';


describe('isoToDisplay', () => {
    it('rewrites an ISO date as dd/mm/yyyy', () => {
        expect(isoToDisplay('2026-06-10')).toBe('10/06/2026');
    });

    it('returns the input unchanged when it is not an ISO date', () => {
        expect(isoToDisplay('')).toBe('');
        expect(isoToDisplay('not-a-date')).toBe('not-a-date');
    });
});


describe('toIso', () => {
    it('builds a zero-padded ISO date from calendar parts (0-based month)', () => {
        expect(toIso(2026, 5, 10)).toBe('2026-06-10');
        expect(toIso(2026, 0, 1)).toBe('2026-01-01');
    });
});

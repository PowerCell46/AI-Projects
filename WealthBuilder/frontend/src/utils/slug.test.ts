import { describe, it, expect } from 'vitest';
import { slugify } from './slug';


describe('slugify', () => {
    it('lowercases a single-word name', () => {
        expect(slugify('Stocks')).toBe('stocks');
    });

    it('replaces spaces with dashes', () => {
        expect(slugify('Precious Metals')).toBe('precious-metals');
    });

    it('collapses runs of non-alphanumeric characters to one dash', () => {
        expect(slugify('S&P  500')).toBe('s-p-500');
    });

    it('trims leading and trailing separators', () => {
        expect(slugify('  Real Estate!  ')).toBe('real-estate');
    });
});

import { describe, it, expect } from 'vitest';
import { formatMoney, formatPrice, formatQuantity } from './format';


describe('formatMoney', () => {
    it('always shows two fraction digits', () => {
        expect(formatMoney(2)).toBe('2.00');
        expect(formatMoney(2.5)).toBe('2.50');
    });

    it('rounds to two fraction digits', () => {
        expect(formatMoney(1.234)).toBe('1.23');
        expect(formatMoney(1.236)).toBe('1.24');
        expect(formatMoney(1.999)).toBe('2.00');
    });
});


describe('formatQuantity', () => {
    it('drops trailing zeros but keeps significant fraction digits', () => {
        expect(formatQuantity(2)).toBe('2');
        expect(formatQuantity(1.5)).toBe('1.5');
        expect(formatQuantity(0.12345678)).toBe('0.12345678');
    });
});


describe('formatPrice', () => {
    it('shows between two and four fraction digits', () => {
        expect(formatPrice(3)).toBe('3.00');
        expect(formatPrice(3.12345)).toBe('3.1235');
    });
});

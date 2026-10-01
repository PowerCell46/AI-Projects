import { describe, expect, it } from 'vitest';
import {
    EMAIL_MESSAGE,
    EMPTY_FIELD_MESSAGE,
    PASSWORD_COMPOSITION_MESSAGE,
    PASSWORD_LENGTH_MESSAGE,
    USERNAME_MESSAGE,
    validateField,
} from './validation';


describe('validateField', () => {
    describe('empty values', () => {
        it.each([
            ['login', 'identifier'],
            ['login', 'password'],
            ['register', 'email'],
            ['register', 'username'],
            ['register', 'password'],
            ['resend', 'email'],
        ] as const)('should_hold_at_depth_when_%s_%s_is_empty', (flowName, field) => {
            expect(validateField(flowName, field, '')).toBe(EMPTY_FIELD_MESSAGE);
            expect(validateField(flowName, field, '   ')).toBe(EMPTY_FIELD_MESSAGE);
        });
    });

    describe('register email', () => {
        it.each([
            'plainaddress',
            'missing@tld',
            'two@@signs.com',
            'a@b.c',
            'space in@mail.com',
            'dotted@sub.example.com',
            `${'a'.repeat(250)}@b.com`,
        ])('should_reject_the_malformed_email_%s', (email) => {
            expect(validateField('register', 'email', email)).toBe(EMAIL_MESSAGE);
        });

        it.each([
            'peter@example.com',
            'first.last+tag@sub-domain.org',
        ])('should_accept_the_email_%s', (email) => {
            expect(validateField('register', 'email', email)).toBeNull();
        });
    });

    describe('register username', () => {
        it.each([
            'ab',
            'a'.repeat(16),
            'has space',
            'dash-ed',
            'ünïcode',
        ])('should_reject_the_username_%s', (username) => {
            expect(validateField('register', 'username', username)).toBe(USERNAME_MESSAGE);
        });

        it.each([
            'abc',
            'a'.repeat(15),
            'Peter_G_99',
        ])('should_accept_the_username_%s', (username) => {
            expect(validateField('register', 'username', username)).toBeNull();
        });
    });

    describe('register password', () => {
        it.each([
            ['Abcde1', 'shorter than 8 characters'],
            [`Aa1${'x'.repeat(70)}`, 'longer than 72 characters'],
            [`Aa1${'é'.repeat(35)}`, 'over 72 bytes with fewer than 72 characters'],
        ])('should_reject_the_length_of_%#_%s', (password) => {
            expect(validateField('register', 'password', password)).toBe(PASSWORD_LENGTH_MESSAGE);
        });

        it.each([
            ['ABCDEFG1', 'no lowercase letter'],
            ['abcdefg1', 'no uppercase letter'],
            ['Abcdefgh', 'no digit'],
        ])('should_reject_the_password_%s_with_%s', (password) => {
            expect(validateField('register', 'password', password)).toBe(PASSWORD_COMPOSITION_MESSAGE);
        });

        it('should_accept_a_password_of_exactly_72_characters_with_all_kinds', () => {
            expect(validateField('register', 'password', `Aa1${'x'.repeat(69)}`)).toBeNull();
        });

        it('should_accept_a_valid_password', () => {
            expect(validateField('register', 'password', 'Abcdefg1')).toBeNull();
        });
    });

    describe('login', () => {
        it('should_accept_any_non_empty_identifier_and_password', () => {
            expect(validateField('login', 'identifier', 'x')).toBeNull();
            expect(validateField('login', 'password', 'x')).toBeNull();
        });
    });

    describe('resend', () => {
        it('should_check_the_email_format', () => {
            expect(validateField('resend', 'email', 'nope')).toBe(EMAIL_MESSAGE);
            expect(validateField('resend', 'email', 'peter@example.com')).toBeNull();
        });
    });
});

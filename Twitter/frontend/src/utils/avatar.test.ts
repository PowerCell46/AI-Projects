import { describe, expect, it } from 'vitest';
import { AVATAR_TINT_COUNT, avatarTintOf, initialsOf } from './avatar';


describe('initialsOf', () => {
    it.each([
        ['peter_g', 'PE'],
        ['Ana', 'AN'],
        ['x9', 'X9'],
        ['_a_b', 'AB'],
        ['7up', '7U'],
        ['abc', 'AB'],
    ])('should_give_the_initials_of_%s_as_%s', (username, expected) => {
        expect(initialsOf(username)).toBe(expected);
    });

    it('should_give_one_letter_when_the_username_has_only_one_letter_or_digit', () => {
        expect(initialsOf('__a__')).toBe('A');
    });

    it('should_give_a_question_mark_when_the_username_has_no_letters_or_digits', () => {
        expect(initialsOf('___')).toBe('?');
    });
});

describe('avatarTintOf', () => {
    const USER_ID = '6f1c2a3e-0000-4000-8000-000000000001';

    it('should_give_the_same_tint_when_the_same_id_is_asked_twice', () => {
        expect(avatarTintOf(USER_ID)).toBe(avatarTintOf(USER_ID));
    });

    it('should_stay_inside_the_eight_tints_for_every_id', () => {
        const tints = Array
            .from(
            { length: 500 },
            (_, index) => avatarTintOf(`user-${index}`),
        );

        expect(Math.min(...tints)).toBeGreaterThanOrEqual(0);
        expect(Math.max(...tints)).toBeLessThan(AVATAR_TINT_COUNT);
    });

    it('should_use_every_tint_when_many_different_ids_are_hashed', () => {
        const tints = new Set(Array.from(
            { length: 500 },
            (_, index) => avatarTintOf(`user-${index}`),
        ));

        expect(tints.size).toBe(AVATAR_TINT_COUNT);
    });

    it.each([
        [USER_ID, 3],
        ['user-1', 4],
        ['', 5],
    ])('should_give_the_tint_of_%j_as_%i_so_a_change_of_the_hash_is_noticed', (userId, expected) => {
        expect(avatarTintOf(userId)).toBe(expected);
    });
});

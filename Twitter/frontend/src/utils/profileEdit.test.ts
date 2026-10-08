import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http';
import {
    changedFields,
    countProfileCharacters,
    describeSaveFailure,
    isBioOverLimit,
    isEmptyChange,
    isLocationOverLimit,
} from './profileEdit';
import { SIGNAL_LOST_MESSAGE } from './authErrors';


const SAVED = {
    bio: 'Builds boats.',
    location: 'Sofia',
};

describe('countProfileCharacters', () => {
    it.each([
        ['', 0],
        ['   ', 0],
        ['  abc  ', 3],
        ['\u{1F600}\u{1F600}', 2],
        ['a\nb', 3],
    ])('should_count_%j_as_%s', (text, expected) => {
        expect(countProfileCharacters(text)).toBe(expected);
    });
});

describe('the limits', () => {
    it('should_accept_160_emoji_in_the_bio_and_refuse_161', () => {
        expect(isBioOverLimit('\u{1F600}'.repeat(160))).toBe(false);
        expect(isBioOverLimit('\u{1F600}'.repeat(161))).toBe(true);
    });

    it('should_not_count_the_blanks_around_the_bio', () => {
        expect(isBioOverLimit(`  ${'a'.repeat(160)}  `)).toBe(false);
    });

    it('should_accept_60_characters_in_the_location_and_refuse_61', () => {
        expect(isLocationOverLimit('a'.repeat(60))).toBe(false);
        expect(isLocationOverLimit('a'.repeat(61))).toBe(true);
    });
});

describe('changedFields', () => {
    it('should_return_nothing_when_the_draft_equals_the_saved_text', () => {
        const change = changedFields(SAVED, SAVED);

        expect(change).toEqual({});
        expect(isEmptyChange(change)).toBe(true);
    });

    it('should_return_only_the_location_when_only_the_location_changed', () => {
        expect(changedFields(SAVED, {
            bio: 'Builds boats.',
            location: 'Plovdiv',
        })).toEqual({ location: 'Plovdiv' });
    });

    it('should_trim_the_values_it_returns', () => {
        expect(changedFields(SAVED, {
            bio: '  New bio  ',
            location: 'Sofia',
        })).toEqual({ bio: 'New bio' });
    });

    it('should_not_count_a_change_of_blanks_around_the_same_text', () => {
        expect(changedFields(SAVED, {
            bio: '  Builds boats.  ',
            location: ' Sofia',
        })).toEqual({});
    });

    it('should_return_an_empty_string_when_a_field_was_emptied', () => {
        expect(changedFields(SAVED, {
            bio: '   ',
            location: 'Sofia',
        })).toEqual({ bio: '' });
    });

    it('should_return_nothing_when_an_empty_field_stays_empty', () => {
        expect(changedFields({
            bio: null,
            location: null,
        }, {
            bio: '  ',
            location: '',
        })).toEqual({});
    });

    it('should_return_both_when_both_changed', () => {
        const change = changedFields(SAVED, {
            bio: 'x',
            location: 'y',
        });

        expect(change).toEqual({
            bio: 'x',
            location: 'y',
        });
        expect(isEmptyChange(change)).toBe(false);
    });
});

describe('describeSaveFailure', () => {
    it('should_put_a_bio_refusal_under_the_bio', () => {
        const errors = describeSaveFailure(new ApiError(400, ['bio must be at most 160 characters']));

        expect(errors).toEqual({
            bio: 'BIO MUST BE AT MOST 160 CHARACTERS',
            location: '',
            photo: '',
            general: '',
        });
    });

    it('should_put_a_location_refusal_under_the_location', () => {
        const errors = describeSaveFailure(new ApiError(400, ['location must be at most 60 characters']));

        expect(errors.location).toBe('LOCATION MUST BE AT MOST 60 CHARACTERS');
        expect(errors.bio).toBe('');
    });

    it('should_put_any_other_refusal_in_the_general_message', () => {
        const errors = describeSaveFailure(new ApiError(400, ['birthdate must be a past date.']));

        expect(errors.general).toBe('BIRTHDATE MUST BE A PAST DATE');
    });

    it.each([
        new ApiError(502, []),
        new ApiError(0, []),
        new ApiError(400, []),
        new ApiError(500, ['bio broke']),
        new Error('boom'),
    ])('should_read_%s_as_a_lost_signal', (failure) => {
        expect(describeSaveFailure(failure)).toEqual({
            bio: '',
            location: '',
            photo: '',
            general: SIGNAL_LOST_MESSAGE,
        });
    });
});

import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http';
import {
    canPublish,
    checkPickedImage,
    countCharacters,
    describeUploadFailure,
    IMAGE_TOO_LARGE_MESSAGE,
    isOverCharacterLimit,
    MAX_IMAGE_BYTES,
    MAX_TWEET_CHARACTERS,
    TOO_MANY_IMAGES_MESSAGE,
    UNSUPPORTED_IMAGE_TYPE_MESSAGE,
} from './composeChecks';
import { SIGNAL_LOST_MESSAGE } from './authErrors';


const VALID_IMAGE = {
    size: 1024,
    type: 'image/png',
};

describe('countCharacters', () => {
    it.each([
        ['', 0],
        ['hello', 5],
        ['  hello  ', 5],
        ['\n\t ', 0],
        ['a b', 3],
    ])('should_count_%j_as_%i_characters', (text, expected) => {
        expect(countCharacters(text)).toBe(expected);
    });

    it('should_count_an_emoji_outside_the_basic_plane_as_one_character', () => {
        expect(countCharacters('😀')).toBe(1);
        expect('😀'.length).toBe(2);
    });

    it('should_count_a_letter_with_a_combining_mark_as_two_code_points', () => {
        expect(countCharacters('é')).toBe(2);
    });
});

describe('isOverCharacterLimit', () => {
    it('should_allow_exactly_280_characters', () => {
        expect(isOverCharacterLimit('a'.repeat(MAX_TWEET_CHARACTERS))).toBe(false);
    });

    it('should_reject_281_characters', () => {
        expect(isOverCharacterLimit('a'.repeat(MAX_TWEET_CHARACTERS + 1))).toBe(true);
    });

    it('should_not_count_the_spaces_around_the_text', () => {
        expect(isOverCharacterLimit(`  ${'a'.repeat(MAX_TWEET_CHARACTERS)}  `)).toBe(false);
    });

    it('should_count_280_emoji_as_280_characters', () => {
        expect(isOverCharacterLimit('😀'.repeat(MAX_TWEET_CHARACTERS))).toBe(false);
        expect(isOverCharacterLimit('😀'.repeat(MAX_TWEET_CHARACTERS + 1))).toBe(true);
    });
});

describe('canPublish', () => {
    it('should_allow_publishing_when_there_is_text_and_no_image', () => {
        expect(canPublish('hello', 0)).toBe(true);
    });

    it('should_allow_publishing_when_there_is_an_image_and_no_text', () => {
        expect(canPublish('', 1)).toBe(true);
    });

    it('should_allow_publishing_when_the_text_is_only_whitespace_and_there_is_an_image', () => {
        expect(canPublish('   ', 1)).toBe(true);
    });

    it('should_refuse_publishing_when_there_is_no_text_and_no_image', () => {
        expect(canPublish('', 0)).toBe(false);
    });

    it('should_refuse_publishing_when_the_text_is_only_whitespace_and_there_is_no_image', () => {
        expect(canPublish(' \n ', 0)).toBe(false);
    });

    it('should_refuse_publishing_when_the_text_is_over_the_limit_even_with_an_image', () => {
        expect(canPublish('a'.repeat(MAX_TWEET_CHARACTERS + 1), 2)).toBe(false);
    });
});

describe('checkPickedImage', () => {
    it.each(['image/jpeg', 'image/png', 'image/webp'])('should_accept_the_%s_type', (type) => {
        const image = {
            size: 1024,
            type,
        };

        expect(checkPickedImage(image, 0)).toBeNull();
    });

    it.each(['image/gif', 'image/svg+xml', 'application/pdf', ''])('should_refuse_the_%j_type', (type) => {
        const image = {
            size: 1024,
            type,
        };

        expect(checkPickedImage(image, 0)).toBe(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
    });

    it('should_accept_a_file_of_exactly_the_size_limit', () => {
        const image = {
            size: MAX_IMAGE_BYTES,
            type: 'image/png',
        };

        expect(checkPickedImage(image, 0)).toBeNull();
    });

    it('should_refuse_a_file_one_byte_over_the_size_limit', () => {
        const image = {
            size: MAX_IMAGE_BYTES + 1,
            type: 'image/png',
        };

        expect(checkPickedImage(image, 0)).toBe(IMAGE_TOO_LARGE_MESSAGE);
    });

    it('should_accept_the_fourth_image', () => {
        expect(checkPickedImage(VALID_IMAGE, 3)).toBeNull();
    });

    it('should_refuse_the_fifth_image', () => {
        expect(checkPickedImage(VALID_IMAGE, 4)).toBe(TOO_MANY_IMAGES_MESSAGE);
    });

    it('should_name_the_image_count_first_when_the_fifth_image_is_also_the_wrong_type', () => {
        const image = {
            size: 1024,
            type: 'image/gif',
        };

        expect(checkPickedImage(image, 4)).toBe(TOO_MANY_IMAGES_MESSAGE);
    });

    it('should_name_the_type_first_when_the_image_is_the_wrong_type_and_too_large', () => {
        const image = {
            size: MAX_IMAGE_BYTES + 1,
            type: 'image/gif',
        };

        expect(checkPickedImage(image, 0)).toBe(UNSUPPORTED_IMAGE_TYPE_MESSAGE);
    });

    it('should_use_the_servers_wording_in_capitals_without_a_full_stop', () => {
        expect(TOO_MANY_IMAGES_MESSAGE).toBe('A TWEET CAN HAVE AT MOST 4 IMAGES');
        expect(UNSUPPORTED_IMAGE_TYPE_MESSAGE).toBe('UNSUPPORTED IMAGE TYPE');
        expect(IMAGE_TOO_LARGE_MESSAGE).toBe('THE UPLOADED FILE IS TOO LARGE');
    });
});

describe('describeUploadFailure', () => {
    it('should_use_the_servers_message_in_capitals_without_the_final_period_when_the_post_is_refused', () => {
        const failure = new ApiError(400, ['A tweet can have at most 4 images.']);

        expect(describeUploadFailure(failure)).toBe('A TWEET CAN HAVE AT MOST 4 IMAGES');
    });

    it('should_join_several_messages_when_the_server_gives_more_than_one', () => {
        const failure = new ApiError(400, ['Content is too long.', 'Another problem.']);

        expect(describeUploadFailure(failure)).toBe('CONTENT IS TOO LONG. ANOTHER PROBLEM');
    });

    it.each([413, 415])('should_use_the_servers_message_when_the_status_is_%i', (status) => {
        expect(describeUploadFailure(new ApiError(status, ['The uploaded file is too large.'])))
            .toBe('THE UPLOADED FILE IS TOO LARGE');
    });

    it.each([0, 500, 502, 504])('should_say_signal_lost_when_the_status_is_%i', (status) => {
        const failure = new ApiError(status, ['Upstream service unavailable.']);

        expect(describeUploadFailure(failure)).toBe(SIGNAL_LOST_MESSAGE);
    });

    it('should_say_signal_lost_when_a_refusal_comes_without_a_message', () => {
        expect(describeUploadFailure(new ApiError(400, []))).toBe(SIGNAL_LOST_MESSAGE);
    });

    it('should_say_signal_lost_when_the_failure_is_not_an_api_error', () => {
        expect(describeUploadFailure(new TypeError('Failed to fetch'))).toBe(SIGNAL_LOST_MESSAGE);
    });
});

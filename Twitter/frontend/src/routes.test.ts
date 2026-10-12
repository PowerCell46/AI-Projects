import { describe, expect, it } from 'vitest';
import { profilePath, tweetPath } from './routes';


describe('tweetPath', () => {
    it('should_leave_a_plain_id_alone', () => {
        expect(tweetPath('6f1c2a3e-0000-4000-8000-000000000001')).toBe('/tweets/6f1c2a3e-0000-4000-8000-000000000001');
    });

    it('should_encode_an_id_that_would_climb_out_of_its_segment', () => {
        expect(tweetPath('../users')).toBe('/tweets/..%2Fusers');
    });
});

describe('profilePath', () => {
    it('should_encode_a_name_that_would_climb_out_of_its_segment', () => {
        expect(profilePath('../users')).toBe('/users/..%2Fusers');
    });
});

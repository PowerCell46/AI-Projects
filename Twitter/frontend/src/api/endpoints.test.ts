import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { profilePath } from '../routes';


const TRAVERSAL_ID = '../../auth/logout';

describe('path segments', () => {
    it('should_keep_a_uuid_as_it_is', () => {
        expect(ENDPOINTS.like('6f1c2a3e-0000-4000-8000-000000000001')).toBe('/api/v1/likes/6f1c2a3e-0000-4000-8000-000000000001');
    });

    it('should_encode_the_slashes_of_a_traversal_id_in_every_id_endpoint', () => {
        const urls = [
            ENDPOINTS.savedTweet(TRAVERSAL_ID),
            ENDPOINTS.like(TRAVERSAL_ID),
            ENDPOINTS.tweetImage(TRAVERSAL_ID, TRAVERSAL_ID),
            ENDPOINTS.replies(TRAVERSAL_ID),
            ENDPOINTS.reply(TRAVERSAL_ID, TRAVERSAL_ID),
            ENDPOINTS.tweetDetails(TRAVERSAL_ID),
            ENDPOINTS.authorTweets(TRAVERSAL_ID),
            ENDPOINTS.user(TRAVERSAL_ID),
            ENDPOINTS.follow(TRAVERSAL_ID),
        ];

        urls.forEach((url) => {
            expect(url).not.toContain(TRAVERSAL_ID);
            expect(new URL(url, 'http://localhost').pathname.startsWith('/api/v1/')).toBe(true);
        });
    });
});

describe('profile addresses', () => {
    it('should_build_the_own_profile_endpoints_without_a_path_parameter', () => {
        expect(ENDPOINTS.me).toBe('/api/v1/users/me');
        expect(ENDPOINTS.profilePicture).toBe('/api/v1/users/me/profile-picture');
    });

    it('should_encode_the_username_in_the_profile_path', () => {
        expect(profilePath('peter_g')).toBe('/users/peter_g');
        expect(profilePath('a b/c')).toBe('/users/a%20b%2Fc');
    });
});

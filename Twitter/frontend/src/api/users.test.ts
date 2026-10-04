import { describe, expect, it } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { fetchUserProfile } from './users';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';


installFetchStub();

describe('fetchUserProfile', () => {
    it('should_get_the_profile_of_the_username', async () => {
        respondWith(
            200,
            {
                id: 'user-1',
                username: 'peter_g',
                profilePictureUrl: null,
            },
        );

        await fetchUserProfile('peter_g');

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.user('peter_g'),
            {
                method: 'GET',
                credentials: 'include',
            },
        );
    });

    it('should_put_the_api_origin_before_the_picture_path_when_the_user_has_a_picture', async () => {
        respondWith(
            200,
            {
                id: 'user-1',
                username: 'peter_g',
                profilePictureUrl: '/api/v1/files/pic-9',
            },
        );

        const profile = await fetchUserProfile('peter_g');

        expect(profile.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-9'));
    });

    it('should_return_a_null_picture_url_when_the_user_has_no_picture', async () => {
        respondWith(
            200,
            {
                id: 'user-1',
                username: 'peter_g',
                profilePictureUrl: null,
            },
        );

        const profile = await fetchUserProfile('peter_g');

        expect(profile.profilePictureUrl).toBeNull();
    });

    it('should_encode_the_username_in_the_url', () => {
        expect(ENDPOINTS.user('a b/c')).toMatch(/\/users\/a%20b%2Fc$/);
    });

    it('should_reject_with_404_when_the_user_is_unknown', async () => {
        respondWith(
            404,
            { messages: ['User not found.'] },
        );

        await expect(fetchUserProfile('ghost')).rejects.toMatchObject({ status: 404 });
    });
});

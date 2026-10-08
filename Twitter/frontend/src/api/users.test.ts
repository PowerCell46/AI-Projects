import { afterEach, describe, expect, it, vi } from 'vitest';
import { ENDPOINTS } from './endpoints';
import { setUnauthorizedHandler } from './http';
import { fetchPeople, fetchUserProfile, followUser, unfollowUser, updateProfile, uploadProfilePicture } from './users';
import { fetchMock, installFetchStub, respondWith } from '../test/fetchStub';
import { userProfile } from '../test/userProfile';


installFetchStub();

function sentForm(): FormData {
    const init = fetchMock.mock.calls[0][1];

    if (!(init?.body instanceof FormData)) {
        throw new Error('Expected the request body to be FormData.');
    }

    return init.body;
}

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

describe('fetchUserProfile fields', () => {
    it('should_return_the_bio_location_join_date_counts_and_follow_state', async () => {
        const profile = userProfile({
            bio: 'Builds boats.',
            location: 'Sofia',
            followersCount: 1204,
            followingCount: 31,
            followedByMe: true,
        });
        respondWith(200, profile);

        await expect(fetchUserProfile('peter_g')).resolves.toEqual(profile);
    });
});

describe('updateProfile', () => {
    it('should_patch_only_the_given_fields_as_json', async () => {
        respondWith(200, userProfile({ location: 'Plovdiv' }));

        await updateProfile({ location: 'Plovdiv' });

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.me,
            {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ location: 'Plovdiv' }),
                credentials: 'include',
            },
        );
    });

    it('should_return_the_saved_profile_with_the_picture_on_the_api_origin', async () => {
        respondWith(200, userProfile({ profilePictureUrl: '/api/v1/files/pic-2' }));

        const profile = await updateProfile({ bio: 'hi' });

        expect(profile.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-2'));
    });

    it('should_reject_with_400_when_the_server_refuses_the_values', async () => {
        respondWith(
            400,
            { messages: ['bio must be at most 160 characters'] },
        );

        await expect(updateProfile({ bio: 'x' })).rejects.toMatchObject({
            status: 400,
            messages: ['bio must be at most 160 characters'],
        });
    });
});

describe('uploadProfilePicture', () => {
    it('should_put_the_file_as_multipart_without_a_content_type_header', async () => {
        respondWith(200, userProfile({ profilePictureUrl: '/api/v1/files/pic-3' }));
        const photo = new File(
            ['abc'],
            'me.png',
            { type: 'image/png' },
        );

        const profile = await uploadProfilePicture(photo);

        const init = fetchMock.mock.calls[0][1];
        expect(fetchMock.mock.calls[0][0]).toBe(ENDPOINTS.profilePicture);
        expect(init?.method).toBe('PUT');
        expect(init?.credentials).toBe('include');
        expect(init?.headers).toBeUndefined();
        expect(sentForm().get('file')).toBe(photo);
        expect(profile.profilePictureUrl).toBe(ENDPOINTS.backendPath('/api/v1/files/pic-3'));
    });

    it('should_reject_with_415_when_the_file_is_not_an_image', async () => {
        respondWith(
            415,
            { messages: ['Unsupported image type.'] },
        );

        await expect(uploadProfilePicture(new File([''], 'a.gif'))).rejects.toMatchObject({ status: 415 });
    });
});

describe('fetchPeople', () => {
    it('should_get_the_people_list_with_the_size_and_the_cursor', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchPeople({
            cursor: 'abc',
            size: 20,
        });

        expect(fetchMock).toHaveBeenCalledWith(
            `${ENDPOINTS.users}?size=20&cursor=abc`,
            {
                method: 'GET',
                credentials: 'include',
            },
        );
    });

    it('should_leave_the_cursor_out_of_the_url_when_it_is_the_first_page', async () => {
        respondWith(
            200,
            {
                items: [],
                nextCursor: null,
            },
        );

        await fetchPeople({
            cursor: null,
            size: 20,
        });

        expect(fetchMock).toHaveBeenCalledWith(`${ENDPOINTS.users}?size=20`, expect.anything());
    });

    it('should_return_the_people_and_the_next_cursor_with_the_picture_on_the_api_origin', async () => {
        respondWith(
            200,
            {
                items: [{
                    id: 'user-1',
                    username: 'bob',
                    bio: 'Builds boats.',
                    followersCount: 1240,
                    followedByMe: true,
                    profilePictureUrl: '/api/v1/files/pic-7',
                }],
                nextCursor: 'next',
            },
        );

        const page = await fetchPeople({
            cursor: null,
            size: 20,
        });

        expect(page.nextCursor).toBe('next');
        expect(page.items).toEqual([{
            id: 'user-1',
            username: 'bob',
            bio: 'Builds boats.',
            followersCount: 1240,
            followedByMe: true,
            profilePictureUrl: ENDPOINTS.backendPath('/api/v1/files/pic-7'),
        }]);
    });

    it('should_keep_a_null_picture_and_a_null_bio_when_the_person_has_neither', async () => {
        respondWith(
            200,
            {
                items: [{
                    id: 'user-2',
                    username: 'cy',
                    bio: null,
                    followersCount: 0,
                    followedByMe: false,
                    profilePictureUrl: null,
                }],
                nextCursor: null,
            },
        );

        const page = await fetchPeople({
            cursor: null,
            size: 20,
        });

        expect(page.items[0].profilePictureUrl).toBeNull();
        expect(page.items[0].bio).toBeNull();
    });

    it('should_reject_with_the_status_when_the_request_fails', async () => {
        respondWith(
            500,
            { messages: ['Something went wrong.'] },
        );

        await expect(fetchPeople({
            cursor: null,
            size: 20,
        })).rejects.toMatchObject({ status: 500 });
    });
});

describe('followUser', () => {
    afterEach(() => {
        setUnauthorizedHandler(null);
    });

    it('should_put_to_the_follow_url_of_the_username', async () => {
        respondWith(204);

        await followUser('bob');

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.follow('bob'),
            {
                method: 'PUT',
                credentials: 'include',
            },
        );
    });

    it('should_resolve_with_nothing_when_the_answer_is_204', async () => {
        respondWith(204);

        await expect(followUser('bob')).resolves.toBeUndefined();
    });

    it('should_encode_the_username_in_the_follow_url', () => {
        expect(ENDPOINTS.follow('a b/c')).toMatch(/\/users\/a%20b%2Fc\/follow$/);
    });

    it('should_reject_with_the_status_when_the_user_is_unknown', async () => {
        respondWith(
            404,
            { messages: ['User not found.'] },
        );

        await expect(followUser('ghost')).rejects.toMatchObject({ status: 404 });
    });

    it('should_reject_with_status_0_when_the_network_fails', async () => {
        fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

        await expect(followUser('bob')).rejects.toMatchObject({ status: 0 });
    });

    it('should_call_the_unauthorized_handler_when_the_session_has_expired', async () => {
        const onUnauthorized = vi.fn();
        setUnauthorizedHandler(onUnauthorized);
        respondWith(401);

        await expect(followUser('bob')).rejects.toMatchObject({ status: 401 });

        expect(onUnauthorized).toHaveBeenCalledOnce();
    });
});

describe('unfollowUser', () => {
    afterEach(() => {
        setUnauthorizedHandler(null);
    });

    it('should_delete_the_follow_url_of_the_username', async () => {
        respondWith(204);

        await unfollowUser('bob');

        expect(fetchMock).toHaveBeenCalledWith(
            ENDPOINTS.follow('bob'),
            {
                method: 'DELETE',
                credentials: 'include',
            },
        );
    });

    it('should_resolve_with_nothing_when_the_answer_is_204', async () => {
        respondWith(204);

        await expect(unfollowUser('bob')).resolves.toBeUndefined();
    });

    it('should_reject_with_the_status_when_the_user_is_unknown', async () => {
        respondWith(
            404,
            { messages: ['User not found.'] },
        );

        await expect(unfollowUser('ghost')).rejects.toMatchObject({ status: 404 });
    });

    it('should_call_the_unauthorized_handler_when_the_session_has_expired', async () => {
        const onUnauthorized = vi.fn();
        setUnauthorizedHandler(onUnauthorized);
        respondWith(401);

        await expect(unfollowUser('bob')).rejects.toMatchObject({ status: 401 });

        expect(onUnauthorized).toHaveBeenCalledOnce();
    });
});

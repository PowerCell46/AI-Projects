import type { UserProfile } from '../api/users';


export function userProfile(fields: Partial<UserProfile> = {}): UserProfile {
    return {
        id: 'user-1',
        username: 'peter_g',
        bio: null,
        location: null,
        createdAt: '2026-10-01T09:00:00Z',
        followersCount: 0,
        followingCount: 0,
        followedByMe: false,
        profilePictureUrl: null,
        ...fields,
    };
}

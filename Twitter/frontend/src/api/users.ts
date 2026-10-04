import { ENDPOINTS } from './endpoints';
import { readJson, sendAuthenticated } from './http';
import { pageUrl } from './paging';
import type { Page, PageRequest } from './paging';
import { toPictureUrl } from './pictureUrl';


export interface UserProfile {
    id: string;
    username: string;
    profilePictureUrl: string | null;
}

export interface Person {
    id: string;
    username: string;
    bio: string | null;
    followersCount: number;
    followedByMe: boolean;
    profilePictureUrl: string | null;
}

export type PeoplePage = Page<Person>;

export async function fetchUserProfile(username: string): Promise<UserProfile> {
    const response = await sendAuthenticated(
        ENDPOINTS.user(username),
        { method: 'GET' },
    );
    const profile = await readJson<UserProfile>(response);

    return {
        ...profile,
        profilePictureUrl: toPictureUrl(profile.profilePictureUrl),
    };
}

function withPictureUrl(person: Person): Person {
    return {
        ...person,
        profilePictureUrl: toPictureUrl(person.profilePictureUrl),
    };
}

export async function fetchPeople(request: PageRequest): Promise<PeoplePage> {
    const response = await sendAuthenticated(
        pageUrl(ENDPOINTS.users, request),
        { method: 'GET' },
    );
    const page = await readJson<PeoplePage>(response);

    return {
        ...page,
        items: page.items.map(withPictureUrl),
    };
}

export async function followUser(username: string): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.follow(username),
        { method: 'PUT' },
    );
}

export async function unfollowUser(username: string): Promise<void> {
    await sendAuthenticated(
        ENDPOINTS.follow(username),
        { method: 'DELETE' },
    );
}

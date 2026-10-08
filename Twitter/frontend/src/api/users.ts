import { ENDPOINTS } from './endpoints';
import { jsonRequest, readJson, sendAuthenticated } from './http';
import { pageUrl } from './paging';
import type { Page, PageRequest } from './paging';
import { toPictureUrl } from './pictureUrl';


export interface UserProfile {
    id: string;
    username: string;
    bio: string | null;
    location: string | null;
    createdAt: string;
    followersCount: number;
    followingCount: number;
    followedByMe: boolean;
    profilePictureUrl: string | null;
}

export interface ProfileChange {
    bio?: string;
    location?: string;
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

interface HasProfilePicture {
    profilePictureUrl: string | null;
}

function withPictureUrl<Owner extends HasProfilePicture>(owner: Owner): Owner {
    return {
        ...owner,
        profilePictureUrl: toPictureUrl(owner.profilePictureUrl),
    };
}

export async function fetchUserProfile(username: string): Promise<UserProfile> {
    const response = await sendAuthenticated(
        ENDPOINTS.user(username),
        { method: 'GET' },
    );
    const profile = await readJson<UserProfile>(response);

    return withPictureUrl(profile);
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

export async function updateProfile(change: ProfileChange): Promise<UserProfile> {
    const response = await sendAuthenticated(
        ENDPOINTS.me,
        jsonRequest('PATCH', change),
    );
    const profile = await readJson<UserProfile>(response);

    return withPictureUrl(profile);
}

export async function uploadProfilePicture(file: File): Promise<UserProfile> {
    const form = new FormData();

    form.append('file', file);

    // No Content-Type header: the browser adds it with the multipart boundary.
    const response = await sendAuthenticated(
        ENDPOINTS.profilePicture,
        {
            method: 'PUT',
            body: form,
        },
    );
    const profile = await readJson<UserProfile>(response);

    return withPictureUrl(profile);
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

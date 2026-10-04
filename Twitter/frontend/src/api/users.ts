import { ENDPOINTS } from './endpoints';
import { readJson, sendAuthenticated } from './http';
import { toPictureUrl } from './pictureUrl';


export interface UserProfile {
    id: string;
    username: string;
    profilePictureUrl: string | null;
}

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

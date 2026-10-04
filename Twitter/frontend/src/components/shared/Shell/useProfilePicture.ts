import { useEffect, useState } from 'react';
import { fetchUserProfile } from '../../../api/users';


// The session's user has no picture field, so the picture comes from the profile, read once per mount.
export function useProfilePicture(username: string | undefined): string | null {
    const [pictureUrl, setPictureUrl] = useState<string | null>(null);

    useEffect(() => {
        if (!username) {
            return;
        }

        let isCancelled = false;

        fetchUserProfile(username)
            .then((profile) => {
                if (!isCancelled) {
                    setPictureUrl(profile.profilePictureUrl);
                }
            })
            .catch(() => undefined);

        return () => {
            isCancelled = true;
        };
    }, [username]);

    return pictureUrl;
}

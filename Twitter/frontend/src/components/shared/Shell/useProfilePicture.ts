import { useEffect, useState } from 'react';
import { fetchUserProfile } from '../../../api/users';


interface ProfilePicture {
    pictureUrl: string | null;
    setPictureUrl: (pictureUrl: string | null) => void;
}

// The session's user has no picture field, so the picture comes from the profile, read once per mount. A new photo
// saved on the profile page replaces it through the setter, so the header follows without another read.
export function useProfilePicture(username: string | undefined): ProfilePicture {
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

    return {
        pictureUrl,
        setPictureUrl,
    };
}

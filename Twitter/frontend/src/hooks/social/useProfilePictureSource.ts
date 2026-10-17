import { useState } from 'react';
import { ENDPOINTS } from '../../api/core/endpoints';


interface ProfilePictureSource {
    src: string;
    onError: () => void;
}

// A missing picture and one that fails to load both fall back to the default, so no avatar is ever broken.
export function useProfilePictureSource(pictureUrl: string | null): ProfilePictureSource {
    const [failedUrl, setFailedUrl] = useState<string | null>(null);

    function handleError() {
        setFailedUrl(pictureUrl);
    }

    const hasLoadableUrl = pictureUrl !== null && pictureUrl !== failedUrl;

    return {
        src: hasLoadableUrl ? pictureUrl : ENDPOINTS.defaultProfilePicture,
        onError: handleError,
    };
}

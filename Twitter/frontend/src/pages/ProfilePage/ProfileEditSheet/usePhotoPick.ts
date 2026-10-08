import { useEffect, useRef, useState } from 'react';
import { checkPickedImage } from '../../../utils/composeChecks';


const NO_IMAGES_YET = 0;

interface PhotoPick {
    file: File | null;
    previewUrl: string | null;
    errorMessage: string;
    pick: (files: File[]) => void;
    clearError: () => void;
}

// The one photo picked for the profile, with a preview URL. A refused pick (wrong type, too big) leaves the earlier
// pick as it was and says why; every URL is released when it is replaced and when the sheet closes, so the browser can
// free the picture.
export function usePhotoPick(): PhotoPick {
    const [file, setFile] = useState<File | null>(null);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [errorMessage, setErrorMessage] = useState('');
    const previewUrlRef = useRef<string | null>(null);

    useEffect(() => {
        return () => {
            if (previewUrlRef.current !== null) {
                URL.revokeObjectURL(previewUrlRef.current);
            }
        };
    }, []);

    function pick(files: File[]) {
        const pickedFile = files.at(0);

        if (!pickedFile) {
            return;
        }

        const problem = checkPickedImage(pickedFile, NO_IMAGES_YET);

        if (problem) {
            setErrorMessage(problem);

            return;
        }

        if (previewUrlRef.current !== null) {
            URL.revokeObjectURL(previewUrlRef.current);
        }

        previewUrlRef.current = URL.createObjectURL(pickedFile);
        setFile(pickedFile);
        setPreviewUrl(previewUrlRef.current);
        setErrorMessage('');
    }

    function clearError() {
        setErrorMessage('');
    }

    return {
        file,
        previewUrl,
        errorMessage,
        pick,
        clearError,
    };
}

import { useEffect, useRef, useState } from 'react';
import { checkPickedImage } from '../../../../utils/composeChecks';


export interface AttachedImage {
    id: number;
    file: File;
    previewUrl: string;
}

interface ImageAttachments {
    images: AttachedImage[];
    errorMessage: string;
    addFiles: (files: File[]) => void;
    removeImage: (imageId: number) => void;
}

// Keeps the picked files with a preview URL each. Every URL is released when its image is removed or the modal closes,
// so the browser can free the picture.
export function useImageAttachments(): ImageAttachments {
    const [images, setImages] = useState<AttachedImage[]>([]);
    const [errorMessage, setErrorMessage] = useState('');
    const imagesRef = useRef<AttachedImage[]>([]);
    const nextIdRef = useRef(0);

    useEffect(() => {
        return () => imagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl));
    }, []);

    function commit(nextImages: AttachedImage[]) {
        imagesRef.current = nextImages;
        setImages(nextImages);
    }

    // The files are taken in order until one is refused; the ones before it stay attached.
    function addFiles(files: File[]) {
        let nextImages = imagesRef.current;
        let refusal = '';

        for (const file of files) {
            const problem = checkPickedImage(file, nextImages.length);

            if (problem) {
                refusal = problem;
                break;
            }

            nextImages = [
                ...nextImages,
                {
                    id: nextIdRef.current++,
                    file,
                    previewUrl: URL.createObjectURL(file),
                },
            ];
        }

        commit(nextImages);
        setErrorMessage(refusal);
    }

    function removeImage(imageId: number) {
        const removed = imagesRef.current.find((image) => image.id === imageId);

        if (removed) {
            URL.revokeObjectURL(removed.previewUrl);
        }

        commit(imagesRef.current.filter((image) => image.id !== imageId));
        setErrorMessage('');
    }

    return {
        images,
        errorMessage,
        addFiles,
        removeImage,
    };
}

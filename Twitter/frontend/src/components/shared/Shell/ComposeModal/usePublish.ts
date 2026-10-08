import { useRef, useState } from 'react';
import { publishTweet } from '../../../../api/tweets';
import type { PublishedTweet, PublishRequest } from '../../../../api/tweets';
import { describeUploadFailure } from '../../../../utils/composeChecks';


interface Publishing {
    isPublishing: boolean;
    errorMessage: string;
    publish: (request: PublishRequest) => void;
}

// One publish at a time. On failure the message stays next to the button and the draft is left alone.
export function usePublish(onPublished: (tweet: PublishedTweet) => void): Publishing {
    const [isPublishing, setIsPublishing] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');
    const isPublishingRef = useRef(false);

    async function publish(request: PublishRequest) {
        if (isPublishingRef.current) {
            return;
        }

        isPublishingRef.current = true;
        setIsPublishing(true);
        setErrorMessage('');

        try {
            onPublished(await publishTweet(request));

        } catch (failure) {
            setErrorMessage(describeUploadFailure(failure));
            setIsPublishing(false);
            isPublishingRef.current = false;
        }
    }

    return {
        isPublishing,
        errorMessage,
        publish,
    };
}

import { useEffect, useState } from 'react';
import { ApiError } from '../../api/http';
import { fetchTweetDetails } from '../../api/tweetDetails';
import type { TweetItem } from '../../api/tweetPage';


const NOT_FOUND_STATUS = 404;

export type DetailsProblem = 'not-found' | 'failed';

export interface TweetDetails {
    post: TweetItem | null;
    problem: DetailsProblem | null;
    retry: () => void;
}

// The post of the details page: neither a post nor a problem means it is still loading. A `404` is a post that is
// gone; any other failure can be tried again.
export function useTweetDetails(tweetId: string): TweetDetails {
    const [post, setPost] = useState<TweetItem | null>(null);
    const [problem, setProblem] = useState<DetailsProblem | null>(null);
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let isCancelled = false;

        fetchTweetDetails(tweetId)
            .then((tweet) => {
                if (isCancelled) {
                    return;
                }

                setPost(tweet);
            })
            .catch((failure: unknown) => {
                if (isCancelled) {
                    return;
                }

                setProblem(failure instanceof ApiError && failure.status === NOT_FOUND_STATUS ? 'not-found' : 'failed');
            });

        return () => {
            isCancelled = true;
        };
    }, [tweetId, attempt]);

    function retry() {
        setProblem(null);
        setAttempt((currentAttempt) => currentAttempt + 1);
    }

    return {
        post,
        problem,
        retry,
    };
}

import { fetchTweetDetails } from '../../api/tweets/tweetDetails';
import type { TweetItem } from '../../api/tweets/tweetPage';
import { useDetailsLoader } from '../../hooks/lists/useDetailsLoader';
import type { DetailsProblem } from '../../hooks/lists/useDetailsLoader';


export type { DetailsProblem };

export interface TweetDetails {
    post: TweetItem | null;
    problem: DetailsProblem | null;
    retry: () => void;
}

// The post of the details page: neither a post nor a problem means it is still loading.
export function useTweetDetails(tweetId: string): TweetDetails {
    const { loaded, problem, retry } = useDetailsLoader(tweetId, fetchTweetDetails);

    return {
        post: loaded,
        problem,
        retry,
    };
}

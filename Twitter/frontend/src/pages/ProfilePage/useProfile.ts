import { useEffect, useState } from 'react';
import { fetchTweetCount } from '../../api/tweets/tweets';
import { fetchUserProfile } from '../../api/accounts/users';
import type { UserProfile } from '../../api/accounts/users';
import { useDetailsLoader } from '../../hooks/lists/useDetailsLoader';
import type { DetailsProblem } from '../../hooks/lists/useDetailsLoader';


export type { DetailsProblem as ProfileProblem };

export interface ProfileState {
    profile: UserProfile | null;
    tweetCount: number | null;
    problem: DetailsProblem | null;
    retry: () => void;
    replaceProfile: (savedProfile: UserProfile) => void;
}

// The profile of a page, with no request for a null username: neither a profile nor a problem means it is still
// loading. The tweet count is read once the profile is known, beside the first page of tweets; a count that cannot be
// read leaves the number out and nothing else.
export function useProfile(username: string | null): ProfileState {
    const {
        loaded: profile,
        problem,
        retry,
        replaceLoaded: replaceProfile,
    } = useDetailsLoader(username, fetchUserProfile);
    const [tweetCount, setTweetCount] = useState<number | null>(null);
    const profileId = profile?.id ?? null;

    useEffect(() => {
        if (profileId === null) {
            return;
        }

        let isCancelled = false;

        fetchTweetCount(profileId)
            .then((count) => {
                if (isCancelled) {
                    return;
                }

                setTweetCount(count);
            })
            .catch(() => {
                // Without a count the page shows no number; the tweets still list.
            });

        return () => {
            isCancelled = true;
        };
    }, [profileId]);

    return {
        profile,
        tweetCount,
        problem,
        retry,
        replaceProfile,
    };
}

import { useEffect, useState } from 'react';
import { ApiError } from '../../api/http';
import { fetchTweetCount } from '../../api/tweets';
import { fetchUserProfile } from '../../api/users';
import type { UserProfile } from '../../api/users';


const NOT_FOUND_STATUS = 404;

export type ProfileProblem = 'not-found' | 'failed';

export interface ProfileState {
    profile: UserProfile | null;
    tweetCount: number | null;
    problem: ProfileProblem | null;
    retry: () => void;
    replaceProfile: (savedProfile: UserProfile) => void;
}

// The profile of a page, with no request for a null username: neither a profile nor a problem means it is still
// loading. The tweet count is read once the profile is known, beside the first page of tweets; a count that cannot be
// read leaves the number out and nothing else.
export function useProfile(username: string | null): ProfileState {
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [tweetCount, setTweetCount] = useState<number | null>(null);
    const [problem, setProblem] = useState<ProfileProblem | null>(null);
    const [attempt, setAttempt] = useState(0);
    const profileId = profile?.id ?? null;

    useEffect(() => {
        if (username === null) {
            return;
        }

        let isCancelled = false;

        fetchUserProfile(username)
            .then((loadedProfile) => {
                if (isCancelled) {
                    return;
                }

                setProfile(loadedProfile);
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
    }, [username, attempt]);

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

    function retry() {
        setProblem(null);
        setAttempt((currentAttempt) => currentAttempt + 1);
    }

    function replaceProfile(savedProfile: UserProfile) {
        setProfile(savedProfile);
    }

    return {
        profile,
        tweetCount,
        problem,
        retry,
        replaceProfile,
    };
}

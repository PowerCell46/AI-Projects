import { useEffect } from 'react';
import type { BottomState } from '../../components/shared/PostListStatus/PostListStatus';
import { USERNAME_PATTERN } from '../../utils/validation';
import ProfileStatus from './ProfileStatus/ProfileStatus';
import ProfileView from './ProfileView/ProfileView';
import { useProfile } from './useProfile';
import type { ProfileProblem } from './useProfile';


const STATUS_OF_PROBLEM: Record<ProfileProblem, BottomState> = {
    'not-found': 'empty',
    failed: 'failed',
};

function noop() {
    // An empty answer has nothing to retry.
}

interface ProfilePageProps {
    username: string;
}

// A username that could never have been registered is not looked up: it goes into API paths, and the answer is known.
function ProfilePage({ username }: ProfilePageProps) {
    const isPossibleUsername = USERNAME_PATTERN.test(username);
    const { profile, tweetCount, problem, retry, replaceProfile } = useProfile(isPossibleUsername ? username : null);

    useEffect(() => {
        window.scrollTo({ top: 0 });
    }, [username]);

    const bottomState = problem ? STATUS_OF_PROBLEM[problem] : 'loading';

    if (!isPossibleUsername) {
        return <ProfileStatus state="empty" onRetry={noop} />;
    }

    if (!profile) {
        return <ProfileStatus state={bottomState} onRetry={retry} />;
    }

    return <ProfileView profile={profile} serverTweetCount={tweetCount} onProfileSaved={replaceProfile} />;
}

export default ProfilePage;

import { useState } from 'react';
import { followUser, unfollowUser } from '../../../../api/users';
import type { Person } from '../../../../api/users';
import { useOptimisticToggle } from '../../../../hooks/useOptimisticToggle';
import { useFollowPhase } from './useFollowPhase';
import type { FollowPhase } from './useFollowPhase';


interface PersonFollow {
    isFollowing: boolean;
    followersCount: number;
    phase: FollowPhase;
    announcement: string;
    handleClick: () => void;
    handleDisarm: () => void;
}

// The count moves by one from what the server sent, in whichever direction the reader's follow now differs from it.
function followersCountOf(person: Person, isFollowing: boolean): number {
    const wasFollowing = person.followedByMe;

    if (isFollowing === wasFollowing) {
        return person.followersCount;
    }

    return person.followersCount + (isFollowing ? 1 : -1);
}

// Following flips at once and is sent afterwards. Unfollowing takes two taps: the first arms the button, the second
// sends. A failed change goes back, shows TRY AGAIN for a while, and a tap on that repeats it without arming again.
export function usePersonFollow(person: Person, onFollowChanged: () => void): PersonFollow {
    const { phase, arm, markFailed, reset } = useFollowPhase();
    const [announcement, setAnnouncement] = useState('');

    async function sendChange(shouldFollow: boolean) {
        if (shouldFollow) {
            await followUser(person.username);

        } else {
            await unfollowUser(person.username);
        }

        onFollowChanged();
    }

    function handleFailure(failedValue: boolean) {
        markFailed();
        setAnnouncement(`Couldn't ${failedValue ? 'follow' : 'unfollow'} ${person.username}`);
    }

    const following = useOptimisticToggle(person.followedByMe, sendChange, handleFailure);

    function handleClick() {
        if (following.isOn && phase === 'idle') {
            arm();
            setAnnouncement(`Press again to unfollow ${person.username}`);

            return;
        }

        reset();
        setAnnouncement('');
        following.toggle();
    }

    function handleDisarm() {
        if (phase === 'armed') {
            reset();
        }
    }

    return {
        isFollowing: following.isOn,
        followersCount: followersCountOf(person, following.isOn),
        phase,
        announcement,
        handleClick,
        handleDisarm,
    };
}

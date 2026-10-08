import { useState } from 'react';
import { followUser, unfollowUser } from '../api/users';
import { useArmPhase } from './useArmPhase';
import type { ArmPhase } from './useArmPhase';
import { useOptimisticToggle } from './useOptimisticToggle';


// What the follow state starts from: any person's card or profile carries these three.
export interface FollowSubject {
    username: string;
    followedByMe: boolean;
    followersCount: number;
}

interface PersonFollow {
    isFollowing: boolean;
    followersCount: number;
    phase: ArmPhase;
    announcement: string;
    handleClick: () => void;
    handleDisarm: () => void;
}

// The count moves by one from what the server sent, in whichever direction the reader's follow now differs from it.
function followersCountOf(person: FollowSubject, isFollowing: boolean): number {
    const wasFollowing = person.followedByMe;

    if (isFollowing === wasFollowing) {
        return person.followersCount;
    }

    return person.followersCount + (isFollowing ? 1 : -1);
}

// Following flips at once and is sent afterwards. Unfollowing takes two taps: the first arms the button, the second
// sends. A failed change goes back, shows TRY AGAIN for a while, and a tap on that repeats it without arming again.
export function usePersonFollow(person: FollowSubject, onFollowChanged: () => void): PersonFollow {
    const { phase, arm, markFailed, reset } = useArmPhase();
    const [announcement, setAnnouncement] = useState('');
    const following = useOptimisticToggle(person.followedByMe, sendChange, handleFailure);

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

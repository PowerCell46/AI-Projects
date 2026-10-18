import { useRef, useState } from 'react';
import type { Person, UserProfile } from '../../../api/accounts/users';
import FollowButton from '../../../components/shared/FollowButton/FollowButton';
import { useShellContext } from '../../../components/shared/Shell/useShellContext';
import { useAuth } from '../../../contexts/AuthContext';
import { useFollowUpdates } from '../../../contexts/FollowUpdatesContext';
import { usePersonFollow } from '../../../hooks/social/usePersonFollow';
import FollowListModal from '../FollowListModal/FollowListModal';
import ProfileEditButton from '../ProfileEditButton/ProfileEditButton';
import ProfileEditSheet from '../ProfileEditSheet/ProfileEditSheet';
import ProfileMasthead from '../ProfileMasthead/ProfileMasthead';
import ProfileStats from '../ProfileStats/ProfileStats';
import ProfileTweets from '../ProfileTweets/ProfileTweets';
import { useOpenFollowList } from '../useOpenFollowList';
import { useOwnProfilePosts } from '../useOwnProfilePosts';


interface ProfileViewProps {
    profile: UserProfile;
    serverTweetCount: number | null;
    onProfileSaved: (savedProfile: UserProfile) => void;
}

// A loaded profile. Someone else's profile has the follow button in the top right corner, the reader's own the edit
// button, which opens the edit sheet over the page. The followers count is the server's plus the reader's own change,
// so a follow or an undo shows at once. On the reader's own profile their new posts show at the top and in the count,
// and following or unfollowing someone from the follower or following list moves the following count.
function ProfileView({ profile, serverTweetCount, onProfileSaved }: ProfileViewProps) {
    const { user } = useAuth();
    const { onFollowChanged, onProfilePictureChanged } = useShellContext();
    const { reportFollowUpdate } = useFollowUpdates();
    const isOwnProfile = user?.username.toLowerCase() === profile.username.toLowerCase();
    const follow = usePersonFollow(profile, handleFollowChanged);
    const { ownPosts, tweetCount } = useOwnProfilePosts(isOwnProfile, serverTweetCount);
    const followList = useOpenFollowList();
    const [isEditing, setIsEditing] = useState(false);
    const [followingCountChange, setFollowingCountChange] = useState(0);
    const editButtonRef = useRef<HTMLButtonElement>(null);

    function handleFollowChanged(followedByMe: boolean, followersCount: number) {
        onFollowChanged();
        reportFollowUpdate(profile.username, {
            followedByMe,
            followersCount,
        });
    }

    // A card of the list is another person's follow: the shell and the People cards learn of it, and on the reader's own
    // profile the following count moves with it.
    function handleListFollowChanged(person: Person, followedByMe: boolean) {
        onFollowChanged();
        reportFollowUpdate(person.username, { followedByMe });

        if (isOwnProfile) {
            setFollowingCountChange((currentChange) => currentChange + (followedByMe ? 1 : -1));
        }
    }

    // Every answer of the edit sheet is the saved profile: the page takes it, and a new picture goes to the header too.
    function handleSaved(savedProfile: UserProfile) {
        onProfileSaved(savedProfile);

        if (savedProfile.profilePictureUrl !== profile.profilePictureUrl) {
            onProfilePictureChanged(savedProfile.profilePictureUrl);
        }
    }

    function handleOpenEditing() {
        setIsEditing(true);
    }

    function handleCloseEditing() {
        setIsEditing(false);
        editButtonRef.current?.focus();
    }

    const action = isOwnProfile ? <ProfileEditButton ref={editButtonRef} onClick={handleOpenEditing} /> : (
        <>
            <FollowButton
                username={profile.username}
                isFollowing={follow.isFollowing}
                phase={follow.phase}
                onClick={follow.handleClick}
                onDisarm={follow.handleDisarm}
            />
            <p className="sr-only" aria-live="polite">{follow.announcement}</p>
        </>
    );

    return (
        <>
            <ProfileMasthead profile={profile} action={action} />
            <ProfileStats
                followersCount={follow.followersCount}
                followingCount={profile.followingCount + followingCountChange}
                tweetCount={tweetCount}
                openListKind={followList.openKind}
                onOpenList={followList.open}
            />
            <ProfileTweets authorId={profile.id} tweetCount={tweetCount} ownPosts={ownPosts} />
            {followList.openKind && (
                <FollowListModal
                    key={followList.openKind}
                    kind={followList.openKind}
                    username={profile.username}
                    onFollowChanged={handleListFollowChanged}
                    onClose={followList.close}
                />
            )}
            {isOwnProfile && isEditing && user && (
                <ProfileEditSheet
                    profile={profile}
                    email={user.email}
                    onSaved={handleSaved}
                    onClose={handleCloseEditing}
                />
            )}
        </>
    );
}

export default ProfileView;

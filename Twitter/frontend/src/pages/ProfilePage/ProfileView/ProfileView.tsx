import { useRef, useState } from 'react';
import type { UserProfile } from '../../../api/users';
import FollowButton from '../../../components/shared/FollowButton/FollowButton';
import { useShellContext } from '../../../components/shared/Shell/useShellContext';
import { useAuth } from '../../../contexts/AuthContext';
import { usePersonFollow } from '../../../hooks/usePersonFollow';
import ProfileEditButton from '../ProfileEditButton/ProfileEditButton';
import ProfileEditSheet from '../ProfileEditSheet/ProfileEditSheet';
import ProfileMasthead from '../ProfileMasthead/ProfileMasthead';
import ProfileStats from '../ProfileStats/ProfileStats';
import ProfileTweets from '../ProfileTweets/ProfileTweets';
import { useOwnProfilePosts } from '../useOwnProfilePosts';


interface ProfileViewProps {
    profile: UserProfile;
    serverTweetCount: number | null;
    onProfileSaved: (savedProfile: UserProfile) => void;
}

// A loaded profile. Someone else's profile has the follow button in the top right corner, the reader's own the edit
// button, which opens the edit sheet over the page. The followers count is the server's plus the reader's own change,
// so a follow or an undo shows at once. On the reader's own profile their new posts show at the top and in the count.
function ProfileView({ profile, serverTweetCount, onProfileSaved }: ProfileViewProps) {
    const { user } = useAuth();
    const { onFollowChanged, onProfilePictureChanged } = useShellContext();
    const isOwnProfile = user?.username.toLowerCase() === profile.username.toLowerCase();
    const follow = usePersonFollow(profile, onFollowChanged);
    const { ownPosts, tweetCount } = useOwnProfilePosts(isOwnProfile, serverTweetCount);
    const [isEditing, setIsEditing] = useState(false);
    const editButtonRef = useRef<HTMLButtonElement>(null);

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
                followingCount={profile.followingCount}
                tweetCount={tweetCount}
            />
            <ProfileTweets authorId={profile.id} tweetCount={tweetCount} ownPosts={ownPosts} />
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

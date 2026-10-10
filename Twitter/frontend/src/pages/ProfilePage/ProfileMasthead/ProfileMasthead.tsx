import type { ReactNode } from 'react';
import type { UserProfile } from '../../../api/users';
import Avatar from '../../../components/shared/Avatar/Avatar';
import { useFocusOnMount } from '../../../hooks/useFocusOnMount';
import { stripBidiControls } from '../../../utils/bidi';
import { formatJoinDate } from '../../../utils/profileFormat';
import './ProfileMasthead.css';


function visibleText(text: string | null): string {
    if (text === null) {
        return '';
    }

    return stripBidiControls(text)
        .trim();
}

interface ProfileMastheadProps {
    profile: UserProfile;
    action: ReactNode;
}

// An empty bio or location is left out altogether, not shown as a placeholder.
function ProfileMasthead({ profile, action }: ProfileMastheadProps) {
    const bio = visibleText(profile.bio);
    const location = visibleText(profile.location);
    const nameRef = useFocusOnMount<HTMLHeadingElement>();

    return (
        <header className="profile-masthead">
            <Avatar pictureUrl={profile.profilePictureUrl} size="huge" />
            <div className="profile-masthead-text">
                <h1 ref={nameRef} className="profile-masthead-name" tabIndex={-1}>{profile.username}</h1>
                {bio !== '' && <p className="profile-masthead-bio">{bio}</p>}
                <p className="profile-masthead-meta">
                    {location !== '' && <span>{location.toUpperCase()}</span>}
                    <span>{formatJoinDate(profile.createdAt)}</span>
                </p>
            </div>
            {action && <div className="profile-masthead-action">{action}</div>}
        </header>
    );
}

export default ProfileMasthead;

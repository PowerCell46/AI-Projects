import { avatarTintOf, initialsOf } from '../../../../../utils/avatar';
import './Avatar.css';


interface AvatarProps {
    userId: string;
    username: string;
    pictureUrl: string | null;
}

function Avatar({ userId, username, pictureUrl }: AvatarProps) {
    if (pictureUrl) {
        return <img className="avatar" src={pictureUrl} alt="" />;
    }

    return (
        <span className="avatar" data-tint={avatarTintOf(userId)} aria-hidden="true">
            {initialsOf(username)}
        </span>
    );
}

export default Avatar;

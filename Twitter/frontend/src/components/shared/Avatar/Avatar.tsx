import { avatarTintOf, initialsOf } from '../../../utils/avatar';
import './Avatar.css';


export type AvatarSize = 'small' | 'large';

interface AvatarProps {
    userId: string;
    username: string;
    pictureUrl: string | null;
    size?: AvatarSize;
}

function Avatar({ userId, username, pictureUrl, size = 'small' }: AvatarProps) {
    if (pictureUrl) {
        return <img className="avatar" data-size={size} src={pictureUrl} alt="" />;
    }

    return (
        <span className="avatar" data-size={size} data-tint={avatarTintOf(userId)} aria-hidden="true">
            {initialsOf(username)}
        </span>
    );
}

export default Avatar;

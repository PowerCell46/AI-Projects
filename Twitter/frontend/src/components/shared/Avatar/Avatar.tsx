import { useProfilePictureSource } from '../../../hooks/useProfilePictureSource';
import './Avatar.css';


export type AvatarSize = 'small' | 'large';

interface AvatarProps {
    pictureUrl: string | null;
    size?: AvatarSize;
}

function Avatar({ pictureUrl, size = 'small' }: AvatarProps) {
    const picture = useProfilePictureSource(pictureUrl);

    return <img className="avatar" data-size={size} src={picture.src} onError={picture.onError} alt="" />;
}

export default Avatar;

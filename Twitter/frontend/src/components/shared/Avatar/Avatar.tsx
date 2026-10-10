import { useProfilePictureSource } from '../../../hooks/useProfilePictureSource';
import './Avatar.css';


export type AvatarSize = 'tiny' | 'small' | 'large' | 'huge';

interface AvatarProps {
    pictureUrl: string | null;
    size?: AvatarSize;
}

function Avatar({ pictureUrl, size = 'small' }: AvatarProps) {
    const picture = useProfilePictureSource(pictureUrl);

    return (
        <img
            className="avatar"
            data-size={size}
            src={picture.src}
            alt=""
            loading="lazy"
            decoding="async"
            onError={picture.onError}
        />
    );
}

export default Avatar;

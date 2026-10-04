import { ENDPOINTS } from '../../../../../api/endpoints';
import type { TweetImage } from '../../../../../api/tweetPage';
import './PostImages.css';


interface PostImagesProps {
    tweetId: string;
    images: TweetImage[];
}

function PostImages({ tweetId, images }: PostImagesProps) {
    if (images.length === 0) {
        return null;
    }

    return (
        <ul className="post-images" data-count={images.length}>
            {images.map((image, index) => (
                <li key={image.id} className="post-images-item">
                    <img
                        className="post-images-picture"
                        src={ENDPOINTS.tweetImage(tweetId, image.id)}
                        alt={`Image ${index + 1} of ${images.length}`}
                        loading="lazy"
                    />
                </li>
            ))}
        </ul>
    );
}

export default PostImages;

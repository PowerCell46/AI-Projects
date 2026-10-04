import type { AttachedImage } from '../useImageAttachments';
import './ImagePreviews.css';


interface ImagePreviewsProps {
    images: AttachedImage[];
    onRemove: (imageId: number) => void;
}

function ImagePreviews({ images, onRemove }: ImagePreviewsProps) {
    if (images.length === 0) {
        return null;
    }

    return (
        <ul className="image-previews">
            {images.map((image, index) => (
                <li key={image.id} className="image-previews-item">
                    <img
                        className="image-previews-picture"
                        src={image.previewUrl}
                        alt={`Attached image ${index + 1}`}
                    />
                    <button
                        type="button"
                        className="image-previews-remove"
                        aria-label={`Remove image ${index + 1}`}
                        onClick={() => onRemove(image.id)}
                    >
                        ×
                    </button>
                </li>
            ))}
        </ul>
    );
}

export default ImagePreviews;

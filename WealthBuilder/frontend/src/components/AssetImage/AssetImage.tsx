import { useAssetImage } from '../../hooks/useAssetImage';
import styles from './AssetImage.module.css';


interface AssetImageProps {
    assetId: number;
    alt: string;
}


/**
 * Renders an asset's image, fetched through the API client so its bearer token is sent.
 * Shows a quiet terminal-style placeholder while loading or if the fetch fails.
 */
export const AssetImage = ({ assetId, alt }: AssetImageProps) => {
    const { objectUrl, loading, failed } = useAssetImage(assetId);

    if (objectUrl !== null) {
        return <img className={styles.image} src={objectUrl} alt={alt} />;
    }

    return (
        <div className={styles.placeholder} aria-hidden="true">
            {failed ? 'no signal' : loading ? '◌ loading' : ''}
        </div>
    );
};

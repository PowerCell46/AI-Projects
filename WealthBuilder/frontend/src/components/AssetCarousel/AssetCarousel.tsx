import { Link } from 'react-router-dom';
import { AssetImage } from '../AssetImage/AssetImage';
import { useAssets } from '../../hooks/useAssets';
import { buildAssetDetailPath } from '../../constants/routes';
import styles from './AssetCarousel.module.css';


/**
 * Horizontal, scroll-snapping rail of the asset catalog. Each tile lazy-loads its own image
 * and links through to the asset's detail screen. Read-only — the catalog is curated by
 * moderators on the admin screen.
 */
export const AssetCarousel = () => {
    const { assets, loading, error } = useAssets();

    if (loading) {
        return <p className={styles.status}>◌ loading assets…</p>;
    }

    if (error !== null) {
        return <p className={styles.statusError} role="alert">! {error}</p>;
    }

    if (assets.length === 0) {
        return <p className={styles.status}>No assets yet — check back once a moderator adds some.</p>;
    }

    return (
        <ul className={styles.rail}>
            {assets.map((asset) => (
                <li key={asset.id} className={styles.tile}>
                    <Link className={styles.link} to={buildAssetDetailPath(asset.name)}>
                        <div className={styles.thumb}>
                            <AssetImage assetId={asset.id} alt={asset.name} />
                        </div>

                        <span className={styles.name}>{asset.name}</span>
                    </Link>
                </li>
            ))}
        </ul>
    );
};

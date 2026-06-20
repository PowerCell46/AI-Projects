import { Link } from 'react-router-dom';
import { AssetImage } from '../AssetImage/AssetImage';
import { useAssets } from '../../hooks/useAssets';
import { buildAssetDetailPath } from '../../constants/routes';
import type { Asset } from '../../types/asset';
import styles from './AssetCarousel.module.css';


// Below this count the rail looks sparse, so we pad it with flickering skeletons.
const MIN_VISIBLE_TILES = 8;

// Above this count the rail scrolls on its own as a seamless, hover-pausing marquee.
const MARQUEE_THRESHOLD = 8;

// Seconds each tile spends crossing the viewport — keeps marquee speed constant for any count.
const SECONDS_PER_TILE = 4;

// Staggers each skeleton's flicker so the rail pulses unevenly and feels alive.
const FLICKER_STAGGER_SECONDS = 0.15;


/**
 * Home-screen asset rail. Renders one of three states once loaded: a seamless marquee when the
 * catalog is large, a skeleton-padded rail when it is sparse, or an empty-state message. While
 * loading it shows flickering skeletons so the page never looks dead.
 */
export const AssetCarousel = () => {
    const { assets, loading, error } = useAssets();

    if (loading) {
        return <SkeletonRail count={MIN_VISIBLE_TILES} />;
    }

    if (error !== null) {
        return <p className={styles.statusError} role="alert">! {error}</p>;
    }

    if (assets.length === 0) {
        return <p className={styles.status}>No assets yet — check back once a moderator adds some.</p>;
    }

    if (assets.length > MARQUEE_THRESHOLD) {
        return <MarqueeRail assets={assets} />;
    }

    return <PaddedRail assets={assets} />;
};


interface RailProps {
    assets: Asset[];
}


/**
 * Seamless marquee for a full catalog. The asset list is rendered twice and the track slid by
 * exactly one copy, so the loop point is invisible. Animation speed scales with the count, and
 * hovering or focusing a tile pauses it.
 */
const MarqueeRail = ({ assets }: RailProps) => {
    const durationSeconds = assets.length * SECONDS_PER_TILE;
    const loopedAssets = [...assets, ...assets];

    return (
        <div className={styles.marqueeViewport}>
            <ul
                className={styles.marqueeTrack}
                style={{ animationDuration: `${durationSeconds}s` }}
            >
                {loopedAssets.map((asset, index) => (
                    <li
                        key={`${asset.id}-${index}`}
                        className={styles.marqueeTile}
                        aria-hidden={index >= assets.length}
                    >
                        <AssetTile asset={asset} />
                    </li>
                ))}
            </ul>
        </div>
    );
};


/**
 * Static rail for a sparse catalog: the real assets followed by enough flickering skeletons to
 * reach {@link MIN_VISIBLE_TILES}, so a one- or two-asset account still looks substantial.
 */
const PaddedRail = ({ assets }: RailProps) => {
    const skeletonCount = Math.max(0, MIN_VISIBLE_TILES - assets.length);

    return (
        <ul className={styles.rail}>
            {assets.map((asset) => (
                <li key={asset.id} className={styles.tile}>
                    <AssetTile asset={asset} />
                </li>
            ))}

            {Array.from({ length: skeletonCount }).map((_, index) => (
                <li key={`skeleton-${index}`} className={styles.tile}>
                    <SkeletonTile delaySeconds={index * FLICKER_STAGGER_SECONDS} />
                </li>
            ))}
        </ul>
    );
};


interface SkeletonRailProps {
    count: number;
}


/** All-skeleton rail used while the catalog is still loading. */
const SkeletonRail = ({ count }: SkeletonRailProps) => (
    <ul className={styles.rail}>
        {Array.from({ length: count }).map((_, index) => (
            <li key={`skeleton-${index}`} className={styles.tile}>
                <SkeletonTile delaySeconds={index * FLICKER_STAGGER_SECONDS} />
            </li>
        ))}
    </ul>
);


interface AssetTileProps {
    asset: Asset;
}


/** Real asset tile: a lazy-loaded image that links through to the asset's detail screen. */
const AssetTile = ({ asset }: AssetTileProps) => (
    <Link className={styles.link} to={buildAssetDetailPath(asset.name)}>
        <div className={styles.thumb}>
            <AssetImage assetId={asset.id} alt={asset.name} />
        </div>

        <span className={styles.name}>{asset.name}</span>
    </Link>
);


interface SkeletonTileProps {
    delaySeconds: number;
}


/** Placeholder tile whose blocks flicker on a staggered delay to imply incoming content. */
const SkeletonTile = ({ delaySeconds }: SkeletonTileProps) => (
    <div className={styles.skeletonTile} aria-hidden="true">
        <div
            className={styles.skeletonThumb}
            style={{ animationDelay: `${delaySeconds}s` }}
        />

        <div
            className={styles.skeletonName}
            style={{ animationDelay: `${delaySeconds}s` }}
        />
    </div>
);

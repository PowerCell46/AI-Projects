import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetImage } from '../AssetImage/AssetImage';
import { AggregationPanel } from './AggregationPanel';
import { HoldingsTable } from './HoldingsTable';
import { HoldingForm } from './HoldingForm';
import { useAssets } from '../../hooks/useAssets';
import { useHoldings } from '../../hooks/useHoldings';
import { deleteHolding } from '../../services/holdingService';
import { APP_ROUTES } from '../../constants/routes';
import type { Asset } from '../../types/asset';
import type { Holding } from '../../types/holding';
import styles from './AssetDetailPage.module.css';


// null = no modal open; 'new' = adding; a Holding = editing that holding.
type Editing = Holding | 'new' | null;


/**
 * Single-asset screen: the catalog image and description plus the user's holdings — paginated
 * table, whole-set aggregation, and create/edit/delete. The asset is resolved by its (unique)
 * name from the loaded catalog, so the API stays id-based while the URL stays readable.
 */
export const AssetDetailPage = () => {
    const { name } = useParams();
    const { assets, loading: assetsLoading, error: assetsError } = useAssets();

    const asset = resolveAsset(assets, name);
    const holdings = useHoldings(asset?.id ?? null);

    const [editing, setEditing] = useState<Editing>(null);

    const handleSaved = (): void => {
        setEditing(null);
        holdings.reload();
    };

    const handleDelete = async (id: number): Promise<void> => {
        await deleteHolding(id);
        holdings.reload();
    };

    return (
        <div className={styles.page}>
            <AppHeader />

            <main className={styles.main}>
                <Link className={styles.back} to={APP_ROUTES.HOME}>← back</Link>

                {assetsLoading && <p className={styles.status}>◌ loading…</p>}

                {!assetsLoading && (assetsError !== null || asset === undefined) && (
                    <p className={styles.statusError} role="alert">! Asset not found.</p>
                )}

                {asset !== undefined && (
                    <>
                        <article className={styles.detail}>
                            <div className={styles.thumb}>
                                <AssetImage assetId={asset.id} alt={asset.name} />
                            </div>

                            <div className={styles.body}>
                                <h1 className={styles.name}>{asset.name}</h1>
                                <p className={styles.description}>{asset.description}</p>
                            </div>
                        </article>

                        <section className={styles.holdings} aria-label="Your holdings">
                            <div className={styles.holdingsHeader}>
                                <h2 className={styles.holdingsHeading}>YOUR HOLDINGS</h2>

                                <button
                                    type="button"
                                    className={styles.addButton}
                                    onClick={() => setEditing('new')}
                                >
                                    + add holding
                                </button>
                            </div>

                            <AggregationPanel summary={holdings.summary} />

                            {holdings.loading && <p className={styles.status}>◌ loading holdings…</p>}

                            {holdings.error !== null && (
                                <p className={styles.statusError} role="alert">! {holdings.error}</p>
                            )}

                            {holdings.holdings !== null && (
                                <HoldingsTable
                                    page={holdings.holdings}
                                    onEdit={(holding) => setEditing(holding)}
                                    onDelete={handleDelete}
                                    onPageChange={holdings.setPageIndex}
                                />
                            )}
                        </section>

                        {editing !== null && (
                            <HoldingForm
                                assetId={asset.id}
                                holding={editing === 'new' ? null : editing}
                                onSaved={handleSaved}
                                onClose={() => setEditing(null)}
                            />
                        )}
                    </>
                )}
            </main>
        </div>
    );
};


/**
 * Finds the asset whose name matches the route segment, case-insensitively (names are unique
 * that way). Returns undefined while the catalog is still loading or when nothing matches.
 */
const resolveAsset = (assets: Asset[], routeName: string | undefined): Asset | undefined => {
    if (routeName === undefined) {
        return undefined;
    }

    const target = routeName.toLowerCase();

    return assets.find((asset) => asset.name.toLowerCase() === target);
};

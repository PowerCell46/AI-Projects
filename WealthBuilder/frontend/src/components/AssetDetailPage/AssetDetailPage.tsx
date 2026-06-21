import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetImage } from '../AssetImage/AssetImage';
import { HoldingFilters } from './HoldingFilters';
import { HoldingsTable } from './HoldingsTable';
import { HoldingForm } from './HoldingForm';
import { useAssets } from '../../hooks/useAssets';
import { useHoldings } from '../../hooks/useHoldings';
import { deleteHolding } from '../../services/holdingService';
import { slugify } from '../../utils/slug';
import { APP_ROUTES } from '../../constants/routes';
import type { Asset } from '../../types/asset';
import type { Holding } from '../../types/holding';
import styles from './AssetDetailPage.module.css';


// null = no modal open; 'new' = adding; a Holding = editing that holding.
type Editing = Holding | 'new' | null;


/**
 * Single-asset screen: the catalog image and description plus the user's holdings — a filterable,
 * paginated table with create/edit/delete. The asset is resolved by its slug from the loaded
 * catalog, so the API stays id-based while the URL stays readable (e.g. /assets/precious-metals).
 */
export const AssetDetailPage = () => {
    const { slug } = useParams();
    const { assets, loading: assetsLoading, error: assetsError } = useAssets();

    const asset = resolveAsset(assets, slug);
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

    const isFiltered = holdings.filter.name.length > 0
        || holdings.filter.from.length > 0
        || holdings.filter.to.length > 0;

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
                        <article className={styles.header}>
                            <div className={styles.thumb}>
                                <AssetImage assetId={asset.id} alt={asset.name} />
                            </div>

                            <div className={styles.headerBody}>
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

                            <HoldingFilters filter={holdings.filter} onChange={holdings.setFilter} />

                            {holdings.error !== null && (
                                <p className={styles.statusError} role="alert">! {holdings.error}</p>
                            )}

                            {holdings.holdings !== null && (
                                <HoldingsTable
                                    page={holdings.holdings}
                                    summary={isFiltered ? holdings.summary : null}
                                    loading={holdings.loading}
                                    emptyLabel={isFiltered
                                        ? 'No holdings match your filters.'
                                        : 'No holdings yet. Add your first purchase.'}
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
 * Finds the asset whose slug matches the route segment. Slugifying both sides makes the match
 * case- and spacing-insensitive. Returns undefined while the catalog is loading or on no match.
 */
const resolveAsset = (assets: Asset[], routeSlug: string | undefined): Asset | undefined => {
    if (routeSlug === undefined) {
        return undefined;
    }

    return assets.find((asset) => slugify(asset.name) === routeSlug);
};

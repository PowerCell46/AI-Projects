import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetImage } from '../AssetImage/AssetImage';
import { HoldingFilters } from './HoldingFilters';
import { HoldingsTable } from './HoldingsTable';
import { HoldingForm } from './HoldingForm';
import { HoldingDetail } from './HoldingDetail';
import { useAsset } from '../../hooks/useAsset';
import { useHoldings } from '../../hooks/useHoldings';
import { deleteHolding } from '../../services/holdingService';
import { APP_ROUTES } from '../../constants/routes';
import type { Holding } from '../../types/holding';
import styles from './AssetDetailPage.module.css';


// null = no modal open; 'new' = adding; a Holding = editing that holding.
type Editing = Holding | 'new' | null;


/**
 * Single-asset screen: the catalog image and description plus the user's holdings — a filterable,
 * paginated table with create/edit/delete. The asset is resolved by its slug through a dedicated
 * endpoint, so the API stays id-based while the URL stays readable (e.g. /assets/precious-metals).
 */
export const AssetDetailPage = () => {
    const { slug } = useParams();
    const { asset, loading: assetLoading, error: assetError } = useAsset(slug);

    const holdings = useHoldings(asset?.id ?? null);

    const [editing, setEditing] = useState<Editing>(null);
    // The holding whose details are open in the read-only viewer, or null.
    const [viewing, setViewing] = useState<Holding | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const handleSaved = (): void => {
        setEditing(null);
        holdings.reload();
    };

    // From the detail viewer, hand the holding straight to the edit form.
    const editFromDetail = (holding: Holding): void => {
        setViewing(null);
        setEditing(holding);
    };

    const handleDelete = async (id: number): Promise<void> => {
        try {
            await deleteHolding(id);
            setDeleteError(null);
            holdings.reload();
        } catch {
            setDeleteError('Could not delete the holding. Try again.');
        }
    };

    const isFiltered = holdings.filter.name.length > 0
        || holdings.filter.from.length > 0
        || holdings.filter.to.length > 0;

    return (
        <div className={styles.page}>
            <AppHeader />

            <main className={styles.main}>
                <Link className={styles.back} to={APP_ROUTES.HOME}>← back</Link>

                {assetLoading && <p className={styles.status}>◌ loading…</p>}

                {!assetLoading && (assetError !== null || asset === null) && (
                    <p className={styles.statusError} role="alert">! Asset not found.</p>
                )}

                {asset !== null && (
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

                            {deleteError !== null && (
                                <p className={styles.statusError} role="alert">! {deleteError}</p>
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
                                    onRowClick={(holding) => setViewing(holding)}
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

                        {viewing !== null && (
                            <HoldingDetail
                                holding={viewing}
                                onEdit={() => editFromDetail(viewing)}
                                onClose={() => setViewing(null)}
                            />
                        )}
                    </>
                )}
            </main>
        </div>
    );
};

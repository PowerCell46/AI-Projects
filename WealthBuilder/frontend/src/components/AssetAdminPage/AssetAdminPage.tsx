import { useEffect, useRef, useState } from 'react';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetImage } from '../AssetImage/AssetImage';
import { AssetForm } from './AssetForm';
import { VhsBands } from '../VhsBands/VhsBands';
import { useAssets } from '../../hooks/useAssets';
import { useSweepClock } from '../../hooks/useSweepClock';
import { usePrefersReducedMotion } from '../../hooks/usePrefersReducedMotion';
import { deleteAsset } from '../../services/assetService';
import type { Asset } from '../../types/asset';
import { ApiError } from '../../types/problem';
import styles from './AssetAdminPage.module.css';


// null = list view; 'new' = creating; an Asset = editing that asset.
type Editing = Asset | 'new' | null;

// Shown when a delete is blocked because the asset still has holdings. The control is already
// disabled for in-use assets, so this also covers the race where an asset gains its first holding
// between the catalog loading and the moderator confirming the delete.
const IN_USE_MESSAGE = 'This asset has holdings and cannot be deleted.';

// Rows per page. The catalog lists asset *types* (stocks, crypto, …), so it stays small enough
// to paginate client-side rather than plumbing paging through the backend.
const PAGE_SIZE = 5;

// Matches the app-wide view-change sweep so the table reveal feels like the same animation.
const SWEEP_DURATION_MS = 1100;

const NO_OP = (): void => undefined;


/**
 * Moderator-only catalog management: list the assets and create, edit, or delete them. Route
 * access is already gated by ModeratorRoute; the backend independently enforces the role.
 */
export const AssetAdminPage = () => {
    const { assets, loading, error, reload } = useAssets();

    const [editing, setEditing] = useState<Editing>(null);
    const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);
    const [page, setPage] = useState(0);

    const prefersReducedMotion = usePrefersReducedMotion();
    const { progress, isRunning, start } = useSweepClock(NO_OP);

    const pageCount = Math.max(1, Math.ceil(assets.length / PAGE_SIZE));

    // Deleting the last asset on a page (or a shrinking list) can leave us past the end.
    useEffect(() => {
        if (page > pageCount - 1) {
            setPage(pageCount - 1);
        }
    }, [page, pageCount]);

    // Replay the scanline sweep over the table whenever the visible page changes, mirroring the
    // reveal on /assets/{name}. The ref guards against re-firing when `start` merely changes
    // identity as the clock starts/stops (which would loop forever).
    const previousPageRef = useRef(page);

    useEffect(() => {
        const pageChanged = previousPageRef.current !== page;
        previousPageRef.current = page;

        if (pageChanged && !prefersReducedMotion) {
            start(SWEEP_DURATION_MS);
        }
    }, [page, prefersReducedMotion, start]);

    const visibleAssets = assets.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

    // Pad short pages (typically the last one) with inert skeleton rows so the table keeps the
    // same height whether it holds 1 row or a full page — paging never makes the layout jump.
    const skeletonRowCount = Math.max(0, PAGE_SIZE - visibleAssets.length);

    const handleSaved = (): void => {
        setEditing(null);
        reload();
    };

    const handleDelete = async (id: number): Promise<void> => {
        setDeleteError(null);

        try {
            await deleteAsset(id);
            setPendingDeleteId(null);
            reload();

            // Replay the scanline sweep as the row drops out, so a delete reveals the new table
            // the same way paging does. A page-shrinking delete (last row on the final page) also
            // moves the page index, but the sweep is idempotent while running so it won't double up.
            if (!prefersReducedMotion) {
                start(SWEEP_DURATION_MS);
            }
        } catch (caught) {
            setPendingDeleteId(null);
            setDeleteError(
                caught instanceof ApiError && caught.isConflict
                    ? IN_USE_MESSAGE
                    : 'Could not delete the asset. Try again.',
            );
        }
    };

    return (
        <div className={styles.page}>
            <AppHeader />

            <main className={styles.main}>
                <div className={styles.toolbar}>
                    <h1 className={styles.title}>ASSET CATALOG</h1>

                    <button type="button" className={styles.newButton} onClick={() => setEditing('new')}>
                        + new asset
                    </button>
                </div>

                <p className={styles.intro}>
                    The catalog defines the asset types every user can track — each one becomes a
                    tile on the dashboard and a destination for holdings. Create a new type, or edit
                    and remove existing ones here. Changes apply across all accounts.
                </p>

                {loading && <p className={styles.status}>◌ loading…</p>}

                {error !== null && (
                    <p className={styles.statusError} role="alert">! {error}</p>
                )}

                {deleteError !== null && (
                    <p className={styles.statusError} role="alert">! {deleteError}</p>
                )}

                {!loading && error === null && assets.length === 0 && (
                    <p className={styles.status}>No assets yet. Create the first one.</p>
                )}

                {!loading && error === null && assets.length > 0 && (
                    <>
                        <div className={styles.tableArea}>
                            {isRunning && (
                                <>
                                    <div
                                        className={styles.sweepCover}
                                        style={{ clipPath: `inset(${progress * 100}% 0 0 0)` }}
                                        aria-hidden="true"
                                    />

                                    <VhsBands progress={progress} />
                                </>
                            )}

                            <div className={styles.tableWrap}>
                                <table className={styles.table}>
                                    <thead>
                                        <tr>
                                            <th className={styles.th}>ASSET</th>
                                            <th className={styles.th}>DESCRIPTION</th>
                                            <th className={`${styles.th} ${styles.actionsHead}`} aria-label="Actions" />
                                        </tr>
                                    </thead>

                                    <tbody>
                                        {visibleAssets.map((asset) => (
                                            <tr key={asset.id} className={styles.row}>
                                                <td className={styles.td}>
                                                    <div className={styles.asset}>
                                                        <div className={styles.thumb}>
                                                            <AssetImage assetId={asset.id} alt={asset.name} />
                                                        </div>

                                                        <span className={styles.name}>{asset.name}</span>
                                                    </div>
                                                </td>

                                                <td className={`${styles.td} ${styles.description}`}>
                                                    {asset.description}
                                                </td>

                                                <td className={`${styles.td} ${styles.actionsCell}`}>
                                                    {pendingDeleteId === asset.id ? (
                                                        <>
                                                            <span className={styles.confirmPrompt}>delete?</span>
                                                            <button
                                                                type="button"
                                                                className={styles.confirmYes}
                                                                onClick={() => handleDelete(asset.id)}
                                                            >
                                                                yes
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className={styles.action}
                                                                onClick={() => setPendingDeleteId(null)}
                                                            >
                                                                no
                                                            </button>
                                                        </>
                                                    ) : (
                                                        <>
                                                            <button
                                                                type="button"
                                                                className={styles.action}
                                                                onClick={() => setEditing(asset)}
                                                            >
                                                                edit
                                                            </button>
                                                            <button
                                                                type="button"
                                                                className={styles.action}
                                                                disabled={asset.inUse}
                                                                title={asset.inUse ? IN_USE_MESSAGE : undefined}
                                                                onClick={() => setPendingDeleteId(asset.id)}
                                                            >
                                                                delete
                                                            </button>
                                                        </>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}

                                        {Array.from({ length: skeletonRowCount }).map((_, index) => (
                                            <SkeletonRow key={`skeleton-${index}`} />
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        <div className={styles.pagination}>
                            <button
                                type="button"
                                className={styles.pageButton}
                                disabled={page === 0}
                                onClick={() => setPage(page - 1)}
                            >
                                ← prev
                            </button>

                            <span className={styles.pageStatus}>
                                page {page + 1} of {pageCount}
                            </span>

                            <button
                                type="button"
                                className={styles.pageButton}
                                disabled={page >= pageCount - 1}
                                onClick={() => setPage(page + 1)}
                            >
                                next →
                            </button>
                        </div>
                    </>
                )}

                {editing !== null && (
                    <AssetForm
                        asset={editing === 'new' ? null : editing}
                        onSaved={handleSaved}
                        onCancel={() => setEditing(null)}
                    />
                )}
            </main>
        </div>
    );
};


/**
 * Inert filler row that mirrors the cell layout (and the tall thumbnail) of a real asset so a
 * short page reads as a full-height table. Hidden from assistive tech — it carries no data.
 */
const SkeletonRow = () => (
    <tr className={styles.skeletonRow} aria-hidden="true">
        <td className={styles.td}>
            <div className={styles.asset}>
                <div className={styles.skeletonThumb} />
                <span className={styles.skeletonBar} />
            </div>
        </td>
        <td className={`${styles.td} ${styles.description}`}>
            <span className={styles.skeletonBar} />
        </td>
        <td className={`${styles.td} ${styles.actionsCell}`} />
    </tr>
);

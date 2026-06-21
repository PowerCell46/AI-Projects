import { useEffect, useState } from 'react';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetImage } from '../AssetImage/AssetImage';
import { AssetForm } from './AssetForm';
import { useAssets } from '../../hooks/useAssets';
import { deleteAsset } from '../../services/assetService';
import type { Asset } from '../../types/asset';
import styles from './AssetAdminPage.module.css';


// null = list view; 'new' = creating; an Asset = editing that asset.
type Editing = Asset | 'new' | null;

// Rows per page. The catalog lists asset *types* (stocks, crypto, …), so it stays small enough
// to paginate client-side rather than plumbing paging through the backend.
const PAGE_SIZE = 8;


/**
 * Moderator-only catalog management: list the assets and create, edit, or delete them. Route
 * access is already gated by ModeratorRoute; the backend independently enforces the role.
 */
export const AssetAdminPage = () => {
    const { assets, loading, error, reload } = useAssets();

    const [editing, setEditing] = useState<Editing>(null);
    const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
    const [page, setPage] = useState(0);

    const pageCount = Math.max(1, Math.ceil(assets.length / PAGE_SIZE));

    // Deleting the last asset on a page (or a shrinking list) can leave us past the end.
    useEffect(() => {
        if (page > pageCount - 1) {
            setPage(pageCount - 1);
        }
    }, [page, pageCount]);

    const visibleAssets = assets.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

    const handleSaved = (): void => {
        setEditing(null);
        reload();
    };

    const handleDelete = async (id: number): Promise<void> => {
        await deleteAsset(id);
        setPendingDeleteId(null);
        reload();
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

                {!loading && error === null && assets.length === 0 && (
                    <p className={styles.status}>No assets yet. Create the first one.</p>
                )}

                {!loading && error === null && assets.length > 0 && (
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
                                                        onClick={() => setPendingDeleteId(asset.id)}
                                                    >
                                                        delete
                                                    </button>
                                                </>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}

                {pageCount > 1 && (
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

import { useState } from 'react';
import { AppHeader } from '../AppHeader/AppHeader';
import { AssetImage } from '../AssetImage/AssetImage';
import { AssetForm } from './AssetForm';
import { useAssets } from '../../hooks/useAssets';
import { useViewTransition } from '../../hooks/useViewTransition';
import { deleteAsset } from '../../services/assetService';
import type { Asset } from '../../types/asset';
import styles from './AssetAdminPage.module.css';


// null = list view; 'new' = creating; an Asset = editing that asset.
type Editing = Asset | 'new' | null;


/**
 * Moderator-only catalog management: list the assets and create, edit, or delete them. Route
 * access is already gated by ModeratorRoute; the backend independently enforces the role.
 */
export const AssetAdminPage = () => {
    const { assets, loading, error, reload } = useAssets();
    const { play } = useViewTransition();

    const [editing, setEditing] = useState<Editing>(null);
    const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);

    // The list <-> form swap keeps the same URL, so trigger the sweep imperatively.
    const startEditing = (target: Asset | 'new'): void => {
        play();
        setEditing(target);
    };

    const stopEditing = (): void => {
        play();
        setEditing(null);
    };

    const handleSaved = (): void => {
        stopEditing();
        reload();
    };

    const handleDelete = async (id: number): Promise<void> => {
        await deleteAsset(id);
        setPendingDeleteId(null);
        reload();
    };

    if (editing !== null) {
        return (
            <div className={styles.page}>
                <AppHeader />

                <main className={styles.main}>
                    <AssetForm
                        asset={editing === 'new' ? null : editing}
                        onSaved={handleSaved}
                        onCancel={stopEditing}
                    />
                </main>
            </div>
        );
    }

    return (
        <div className={styles.page}>
            <AppHeader />

            <main className={styles.main}>
                <div className={styles.toolbar}>
                    <h1 className={styles.title}>ASSET CATALOG</h1>

                    <button type="button" className={styles.newButton} onClick={() => startEditing('new')}>
                        + new asset
                    </button>
                </div>

                {loading && <p className={styles.status}>◌ loading…</p>}

                {error !== null && (
                    <p className={styles.statusError} role="alert">! {error}</p>
                )}

                {!loading && error === null && assets.length === 0 && (
                    <p className={styles.status}>No assets yet. Create the first one.</p>
                )}

                <ul className={styles.list}>
                    {assets.map((asset) => (
                        <li key={asset.id} className={styles.row}>
                            <div className={styles.thumb}>
                                <AssetImage assetId={asset.id} alt={asset.name} />
                            </div>

                            <div className={styles.info}>
                                <span className={styles.name}>{asset.name}</span>
                                <span className={styles.description}>{asset.description}</span>
                            </div>

                            <div className={styles.rowActions}>
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
                                            onClick={() => startEditing(asset)}
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
                            </div>
                        </li>
                    ))}
                </ul>
            </main>
        </div>
    );
};

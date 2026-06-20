import { useState } from 'react';
import { formatMoney, formatPrice, formatQuantity } from '../../utils/format';
import { isoToDisplay } from '../../utils/date';
import type { Holding } from '../../types/holding';
import type { PageResponse } from '../../types/page';
import styles from './HoldingsTable.module.css';


interface HoldingsTableProps {
    page: PageResponse<Holding>;
    loading: boolean;
    emptyLabel: string;
    onEdit: (holding: Holding) => void;
    onDelete: (id: number) => void;
    onPageChange: (page: number) => void;
}


/**
 * Server-paginated table of the user's holdings, newest first. Delete is a two-step inline
 * confirm so a stray click can't drop a record; edit hands the holding back to the parent. The
 * pager is always shown so the table's position in the set is visible even on a single page.
 */
export const HoldingsTable = ({ page, loading, emptyLabel, onEdit, onDelete, onPageChange }: HoldingsTableProps) => {
    const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);

    if (page.content.length === 0) {
        return <p className={styles.empty}>{loading ? '◌ loading…' : emptyLabel}</p>;
    }

    const confirmDelete = (id: number): void => {
        onDelete(id);
        setPendingDeleteId(null);
    };

    return (
        <div className={`${styles.wrapper} ${loading ? styles.loading : ''}`}>
            <div className={styles.scroll}>
                <table className={styles.table}>
                    <thead>
                        <tr>
                            <th className={styles.th}>NAME</th>
                            <th className={styles.th}>DATE</th>
                            <th className={`${styles.th} ${styles.numeric}`}>QUANTITY</th>
                            <th className={`${styles.th} ${styles.numeric}`}>PRICE</th>
                            <th className={`${styles.th} ${styles.numeric}`}>AMOUNT</th>
                            <th className={styles.th} aria-label="Actions" />
                        </tr>
                    </thead>

                    <tbody>
                        {page.content.map((holding) => (
                            <tr key={holding.id} className={styles.row}>
                                <td className={styles.td}>
                                    <span className={styles.name}>{holding.name}</span>
                                    {holding.note !== null && holding.note.length > 0 && (
                                        <span className={styles.note}>{holding.note}</span>
                                    )}
                                </td>
                                <td className={styles.td}>{isoToDisplay(holding.date)}</td>
                                <td className={`${styles.td} ${styles.numeric}`}>
                                    {formatQuantity(holding.quantity)}
                                </td>
                                <td className={`${styles.td} ${styles.numeric}`}>
                                    {formatPrice(holding.price)}
                                </td>
                                <td className={`${styles.td} ${styles.numeric}`}>
                                    {formatMoney(holding.boughtForAmount)}
                                </td>
                                <td className={`${styles.td} ${styles.actionsCell}`}>
                                    {pendingDeleteId === holding.id ? (
                                        <>
                                            <span className={styles.confirmPrompt}>delete?</span>
                                            <button
                                                type="button"
                                                className={styles.confirmYes}
                                                onClick={() => confirmDelete(holding.id)}
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
                                                onClick={() => onEdit(holding)}
                                            >
                                                edit
                                            </button>
                                            <button
                                                type="button"
                                                className={styles.action}
                                                onClick={() => setPendingDeleteId(holding.id)}
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

            <div className={styles.pagination}>
                <button
                    type="button"
                    className={styles.pageButton}
                    disabled={page.page === 0}
                    onClick={() => onPageChange(page.page - 1)}
                >
                    ← prev
                </button>

                <span className={styles.pageStatus}>
                    page {page.page + 1} of {Math.max(page.totalPages, 1)}
                </span>

                <button
                    type="button"
                    className={styles.pageButton}
                    disabled={page.page >= page.totalPages - 1}
                    onClick={() => onPageChange(page.page + 1)}
                >
                    next →
                </button>
            </div>
        </div>
    );
};

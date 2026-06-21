import { useEffect, useRef, useState } from 'react';
import { formatMoney, formatPrice, formatQuantity } from '../../utils/format';
import { isoToDisplay } from '../../utils/date';
import { useSweepClock } from '../../hooks/useSweepClock';
import { usePrefersReducedMotion } from '../../hooks/usePrefersReducedMotion';
import { VhsBands } from '../VhsBands/VhsBands';
import type { Holding, HoldingSummary } from '../../types/holding';
import type { PageResponse } from '../../types/page';
import styles from './HoldingsTable.module.css';


// Matches the app-wide view-change sweep so the table reveal feels like the same animation.
const SWEEP_DURATION_MS = 1100;

const NO_OP = (): void => undefined;


interface HoldingsTableProps {
    page: PageResponse<Holding>;
    // Totals for the active filter, rendered as a band above the rows; null when unfiltered.
    summary: HoldingSummary | null;
    loading: boolean;
    emptyLabel: string;
    onEdit: (holding: Holding) => void;
    onDelete: (id: number) => void;
    onPageChange: (page: number) => void;
    onRowClick: (holding: Holding) => void;
}


/**
 * Server-paginated table of the user's holdings, newest first. Delete is a two-step inline
 * confirm so a stray click can't drop a record; edit hands the holding back to the parent. The
 * pager is always shown so the table's position in the set is visible even on a single page.
 */
export const HoldingsTable = ({ page, summary, loading, emptyLabel, onEdit, onDelete, onPageChange, onRowClick }: HoldingsTableProps) => {
    const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);

    const prefersReducedMotion = usePrefersReducedMotion();
    const { progress, isRunning, start } = useSweepClock(NO_OP);

    // Replay the scanline sweep over the table whenever a fresh page arrives — paging and
    // filtering both hand down a new `page` object. The ref guards against re-firing when
    // `start` merely changes identity as the clock starts/stops (which would loop forever).
    const previousPageRef = useRef(page);

    useEffect(() => {
        const pageChanged = previousPageRef.current !== page;
        previousPageRef.current = page;

        if (pageChanged && !prefersReducedMotion) {
            start(SWEEP_DURATION_MS);
        }
    }, [page, prefersReducedMotion, start]);

    if (page.content.length === 0) {
        return <p className={styles.empty}>{loading ? '◌ loading…' : emptyLabel}</p>;
    }

    const confirmDelete = (id: number): void => {
        onDelete(id);
        setPendingDeleteId(null);
    };

    // Pad short pages (typically the last one) with inert skeleton rows so the table keeps the
    // same height whether it holds 1 row or a full page — paging never makes the layout jump.
    const skeletonRowCount = Math.max(0, page.size - page.content.length);

    return (
        <div className={`${styles.wrapper} ${loading ? styles.loading : ''}`}>
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

                <div className={styles.scroll}>
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>NAME</th>
                                <th className={styles.th}>DATE</th>
                                <th className={styles.th}>UNIT</th>
                                <th className={`${styles.th} ${styles.numeric}`}>QUANTITY</th>
                                <th className={`${styles.th} ${styles.numeric}`}>BOUGHT AT PRICE</th>
                                <th className={`${styles.th} ${styles.numeric}`}>TOTAL COST</th>
                                <th className={`${styles.th} ${styles.actionsHead}`} aria-label="Actions" />
                            </tr>
                        </thead>

                        <tbody>
                            {page.content.map((holding) => (
                                <tr
                                    key={holding.id}
                                    className={styles.row}
                                    onClick={() => onRowClick(holding)}
                                    role="button"
                                    tabIndex={0}
                                    onKeyDown={(event) => {
                                        if (event.key === 'Enter' || event.key === ' ') {
                                            event.preventDefault();
                                            onRowClick(holding);
                                        }
                                    }}
                                >
                                    <td className={styles.td}>
                                        <span className={styles.name}>{holding.name}</span>
                                    </td>
                                    <td className={styles.td}>{isoToDisplay(holding.date)}</td>
                                    <td className={styles.td}>{holding.unit}</td>
                                    <td className={`${styles.td} ${styles.numeric}`}>
                                        {formatQuantity(holding.quantity)}
                                    </td>
                                    <td className={`${styles.td} ${styles.numeric}`}>
                                        {formatPrice(holding.price)}
                                    </td>
                                    <td className={`${styles.td} ${styles.numeric}`}>
                                        {formatMoney(holding.boughtForAmount)}
                                    </td>
                                    <td
                                        className={`${styles.td} ${styles.actionsCell}`}
                                        onClick={(event) => event.stopPropagation()}
                                        onKeyDown={(event) => event.stopPropagation()}
                                    >
                                        <div className={styles.actions}>
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
                                        </div>
                                    </td>
                                </tr>
                            ))}

                            {Array.from({ length: skeletonRowCount }).map((_, index) => (
                                <SkeletonRow key={`skeleton-${index}`} />
                            ))}
                        </tbody>

                        {summary !== null && summary.holdingCount > 0 && (
                            <tfoot>
                                <TotalsRow summary={summary} />
                            </tfoot>
                        )}
                    </table>
                </div>
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


/**
 * Inert filler row that mirrors the cell layout of a real holding so a short page reads as a
 * full-height table. Hidden from assistive tech — it carries no data, only keeps the size fixed.
 */
const SkeletonRow = () => (
    <tr className={styles.skeletonRow} aria-hidden="true">
        <td className={styles.td}>
            <span className={styles.skeletonBar} />
        </td>
        <td className={styles.td}>
            <span className={styles.skeletonBar} />
        </td>
        <td className={styles.td}>
            <span className={styles.skeletonBar} />
        </td>
        <td className={`${styles.td} ${styles.numeric}`}>
            <span className={styles.skeletonBar} />
        </td>
        <td className={`${styles.td} ${styles.numeric}`}>
            <span className={styles.skeletonBar} />
        </td>
        <td className={`${styles.td} ${styles.numeric}`}>
            <span className={styles.skeletonBar} />
        </td>
        <td className={`${styles.td} ${styles.actionsCell}`} />
    </tr>
);


/**
 * Aggregates for the active filter, rendered as the table's footer so each figure stays aligned
 * to its column (total quantity under QUANTITY, etc.) while reading unmistakably as a summary
 * rather than a data row. The average is the only non-additive figure, so it carries an "avg" tag.
 */
const TotalsRow = ({ summary }: { summary: HoldingSummary }) => (
    <tr className={styles.totalsRow}>
        <td className={`${styles.td} ${styles.totalsLabel}`} colSpan={3}>
            totals ({summary.holdingCount})
        </td>
        <td className={`${styles.td} ${styles.numeric} ${styles.totalsValue}`}>
            {formatQuantity(summary.quantitySum)}
        </td>
        <td className={`${styles.td} ${styles.numeric} ${styles.totalsValue}`}>
            <span className={styles.totalsTag}>avg</span>
            {summary.averagePrice === null ? '—' : formatPrice(summary.averagePrice)}
        </td>
        <td className={`${styles.td} ${styles.numeric} ${styles.totalsValue}`}>
            {formatMoney(summary.amountSum)}
        </td>
        <td className={styles.td} aria-hidden="true" />
    </tr>
);

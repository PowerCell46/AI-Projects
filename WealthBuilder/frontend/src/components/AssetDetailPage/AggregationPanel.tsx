import { formatMoney, formatPrice, formatQuantity } from '../../utils/format';
import type { HoldingSummary } from '../../types/holding';
import styles from './AggregationPanel.module.css';


interface AggregationPanelProps {
    summary: HoldingSummary | null;
}


/**
 * Read-only aggregation over all of the user's holdings for the asset: invested amount, total
 * quantity, the unweighted mean unit price, and the purchase-date span. Renders nothing until a
 * summary is available; shows zeros / em-dashes when there are no holdings yet.
 */
export const AggregationPanel = ({ summary }: AggregationPanelProps) => {
    if (summary === null) {
        return null;
    }

    return (
        <dl className={styles.panel}>
            <Stat label="HOLDINGS" value={String(summary.holdingCount)} />
            <Stat label="INVESTED" value={formatMoney(summary.amountSum)} />
            <Stat label="QUANTITY" value={formatQuantity(summary.quantitySum)} />
            <Stat label="AVG PRICE" value={summary.averagePrice === null ? '—' : formatPrice(summary.averagePrice)} />
            <Stat label="PERIOD" value={formatPeriod(summary.periodStart, summary.periodEnd)} />
        </dl>
    );
};


interface StatProps {
    label: string;
    value: string;
}


const Stat = ({ label, value }: StatProps) => (
    <div className={styles.stat}>
        <dt className={styles.label}>{label}</dt>
        <dd className={styles.value}>{value}</dd>
    </div>
);


const formatPeriod = (start: string | null, end: string | null): string => {
    if (start === null || end === null) {
        return '—';
    }

    return start === end ? start : `${start} → ${end}`;
};

import { useDistribution } from '../../hooks/useDistribution';
import { formatMoney } from '../../utils/format';
import type { AssetDistribution } from '../../types/dashboard';
import styles from './DistributionChart.module.css';


const CENTER = 100;
const RADIUS = 80;
const STROKE_WIDTH = 26;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

// Distinct but on-brand slice colors; the list cycles if there are more assets than entries.
const SLICE_COLORS = ['#7AFFA0', '#39C97E', '#C9A24B', '#5BAEA0', '#B26BD8', '#E0795B', '#4D89D6'];

// The modulo keeps the index in range, so this only ever satisfies the compiler under
// noUncheckedIndexedAccess — it isn't reachable at runtime.
const FALLBACK_COLOR = '#7AFFA0';


interface Slice {
    id: number;
    name: string;
    amount: number;
    fraction: number;
    color: string;
}


/**
 * Invested-per-asset donut for the home screen, drawn as a plain SVG (no chart library). Falls
 * back to an empty ring with guidance when the user has no holdings yet.
 */
export const DistributionChart = () => {
    const { distribution, loading, error } = useDistribution();

    if (loading) {
        return <p className={styles.status}>◌ loading distribution…</p>;
    }

    if (error !== null) {
        return <p className={styles.statusError} role="alert">! {error}</p>;
    }

    const total = distribution.reduce((sum, item) => sum + item.amountInvested, 0);

    if (distribution.length === 0 || total === 0) {
        return <EmptyDistribution />;
    }

    return <Donut slices={toSlices(distribution, total)} total={total} />;
};


interface DonutProps {
    slices: Slice[];
    total: number;
}


const Donut = ({ slices, total }: DonutProps) => (
    <div className={styles.chart}>
        <svg
            className={styles.ring}
            viewBox="0 0 200 200"
            role="img"
            aria-label="Invested amount per asset class"
        >
            {renderArcs(slices)}
        </svg>

        <ul className={styles.legend}>
            {slices.map((slice) => (
                <li key={slice.id} className={styles.legendItem}>
                    <span
                        className={styles.swatch}
                        style={{ background: slice.color }}
                        aria-hidden="true"
                    />
                    <span className={styles.legendName}>{slice.name}</span>
                    <span className={styles.legendValue}>
                        {formatMoney(slice.amount)} · {formatPercent(slice.fraction)}
                    </span>
                </li>
            ))}

            <li className={styles.legendTotal}>
                <span className={styles.legendName}>Total</span>
                <span className={styles.legendValue}>{formatMoney(total)}</span>
            </li>
        </ul>
    </div>
);


const EmptyDistribution = () => (
    <div className={styles.chart}>
        <svg className={styles.ring} viewBox="0 0 200 200" aria-hidden="true">
            <circle
                cx={CENTER}
                cy={CENTER}
                r={RADIUS}
                fill="none"
                stroke="var(--skeleton-bright)"
                strokeWidth={STROKE_WIDTH}
            />
        </svg>

        <p className={styles.emptyText}>
            No holdings yet. Once you record purchases, your invested amount per asset class
            will appear here.
        </p>
    </div>
);


/** Builds the SVG arcs, each offset so it begins where the previous slice ended. */
const renderArcs = (slices: Slice[]) => {
    let cumulative = 0;

    return slices.map((slice) => {
        const dash = slice.fraction * CIRCUMFERENCE;
        const offset = -cumulative * CIRCUMFERENCE;
        cumulative += slice.fraction;

        return (
            <circle
                key={slice.id}
                cx={CENTER}
                cy={CENTER}
                r={RADIUS}
                fill="none"
                stroke={slice.color}
                strokeWidth={STROKE_WIDTH}
                strokeDasharray={`${dash} ${CIRCUMFERENCE - dash}`}
                strokeDashoffset={offset}
                transform={`rotate(-90 ${CENTER} ${CENTER})`}
            />
        );
    });
};


const toSlices = (distribution: AssetDistribution[], total: number): Slice[] => {
    return [...distribution]
        .sort((a, b) => b.amountInvested - a.amountInvested)
        .map((item, index) => ({
            id: item.assetId,
            name: item.assetName,
            amount: item.amountInvested,
            fraction: item.amountInvested / total,
            color: SLICE_COLORS[index % SLICE_COLORS.length] ?? FALLBACK_COLOR,
        }));
};


const formatPercent = (fraction: number): string => `${Math.round(fraction * 100)}%`;

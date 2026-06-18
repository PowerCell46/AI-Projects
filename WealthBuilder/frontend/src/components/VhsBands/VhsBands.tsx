import styles from './VhsBands.module.css';


interface VhsBandsProps {
    progress: number;
}


// Staggered vertical offsets for the three tracking bands (lead first).
const BAND_OFFSETS = [0, 0.33, 0.66] as const;

const JITTER_AMPLITUDE_REM = 0.375;


/**
 * The three analog-noise bands that ride the reveal front. Each is positioned from the
 * shared sweep clock and given a deterministic sin()-driven horizontal wobble — organic
 * motion without random numbers, so it stays stable across re-renders.
 */
export const VhsBands = ({ progress }: VhsBandsProps) => {
    return (
        <div className={styles.bands} aria-hidden="true">
            {BAND_OFFSETS.map((offset, index) => {
                const topPercent = ((progress + offset) % 1) * 100;
                const jitterRem = Math.sin(progress * 40 + index) * JITTER_AMPLITUDE_REM;
                const isLead = index === 0;

                return (
                    <span
                        key={offset}
                        className={`${styles.band} ${isLead ? styles.lead : styles.trail}`}
                        style={{
                            top: `${topPercent}%`,
                            transform: `translateX(${jitterRem}rem)`,
                        }}
                    />
                );
            })}
        </div>
    );
};

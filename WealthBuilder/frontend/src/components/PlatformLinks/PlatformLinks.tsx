import { INVESTMENT_PLATFORMS } from '../../constants/platforms';
import styles from './PlatformLinks.module.css';


/**
 * Quick-access launchpad to the external brokers and exchanges a user trades on. Every card is
 * an outbound link opened in a new tab — WealthBuilder tracks holdings, it doesn't trade.
 */
export const PlatformLinks = () => (
    <ul className={styles.grid}>
        {INVESTMENT_PLATFORMS.map((platform) => (
            <li key={platform.name}>
                <a
                    className={styles.card}
                    href={platform.url}
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    <span className={styles.name}>{platform.name}</span>
                    <span className={styles.category}>{platform.category}</span>
                    <span className={styles.arrow} aria-hidden="true">↗</span>
                </a>
            </li>
        ))}
    </ul>
);

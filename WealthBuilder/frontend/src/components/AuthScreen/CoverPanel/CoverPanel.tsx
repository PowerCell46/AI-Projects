import { ASCII_CHART } from './asciiChart';
import styles from './CoverPanel.module.css';


/**
 * The signature visual half: logo mark, centered ASCII P/L chart, and the
 * END_OF_TRANSMISSION footer caption. Mode-agnostic — only its side changes.
 */
export const CoverPanel = () => {
    return (
        <div className={styles.cover}>
            <span className={styles.logo}>▮ WEALTHBUILDER v0.1</span>

            <pre className={styles.chart} aria-hidden="true">{ASCII_CHART}</pre>

            <span className={styles.footer}>[ END_OF_TRANSMISSION ]</span>
        </div>
    );
};

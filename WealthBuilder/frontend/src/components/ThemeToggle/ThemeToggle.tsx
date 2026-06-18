import { useTheme } from '../../context/ThemeContext/useTheme';
import styles from './ThemeToggle.module.css';


// 'app' tracks the app accent; 'phosphor' stays terminal-green for the fixed dark CRT.
type ThemeToggleVariant = 'app' | 'phosphor';


interface ThemeToggleProps {
    variant?: ThemeToggleVariant;
}


/**
 * Switches the app-level light/dark theme. (The CRT auth screen stays dark by design, so
 * its toggle uses the phosphor variant rather than the app accent.)
 */
export const ThemeToggle = ({ variant = 'app' }: ThemeToggleProps) => {
    const { theme, toggleTheme } = useTheme();

    const nextTheme = theme === 'dark' ? 'light' : 'dark';
    const toneClass = variant === 'phosphor' ? styles.phosphor : '';

    return (
        <button
            type="button"
            className={`${styles.toggle} ${toneClass}`}
            onClick={toggleTheme}
            aria-label={`Switch to ${nextTheme} theme`}
        >
            {theme === 'dark' ? '☾ dark' : '☀ light'}
        </button>
    );
};

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ThemeContext } from './ThemeContext';
import type { Theme, ThemeContextValue } from './ThemeContext';
import { STORAGE_KEYS } from '../../constants/storage';


interface ThemeProviderProps {
    children: ReactNode;
}


const THEME_TRANSITION_CLASS = 'theme-transitioning';


const resolveInitialTheme = (): Theme => {
    const stored = localStorage.getItem(STORAGE_KEYS.THEME);

    if (stored === 'light' || stored === 'dark') {
        return stored;
    }

    return 'dark';
};


/** Reads the `--theme-transition` token so the gating class is removed exactly when the cross-fade ends. */
const readThemeTransitionMs = (root: HTMLElement): number => {
    const raw = getComputedStyle(root)
        .getPropertyValue('--theme-transition')
        .trim();

    if (raw.endsWith('ms')) {
        return parseFloat(raw);
    }

    if (raw.endsWith('s')) {
        return parseFloat(raw) * 1000;
    }

    return 700;
};


/**
 * Light/dark theme provider. The active theme is written to `data-theme` on the root
 * element; theme.css keys its app-level variables off that attribute.
 */
export const ThemeProvider = ({ children }: ThemeProviderProps) => {
    const [theme, setTheme] = useState<Theme>(resolveInitialTheme);
    const isInitialMount = useRef(true);

    useEffect(() => {
        const root = document.documentElement;
        root.setAttribute('data-theme', theme);
        localStorage.setItem(STORAGE_KEYS.THEME, theme);

        // Don't animate the theme already in place on first paint — only actual swaps.
        if (isInitialMount.current) {
            isInitialMount.current = false;
            return;
        }

        root.classList.add(THEME_TRANSITION_CLASS);
        const timer = window.setTimeout(
            () => root.classList.remove(THEME_TRANSITION_CLASS),
            readThemeTransitionMs(root),
        );

        return () => window.clearTimeout(timer);
    }, [theme]);

    const toggleTheme = useCallback(() => {
        setTheme((current) => (current === 'dark' ? 'light' : 'dark'));
    }, []);

    const value = useMemo<ThemeContextValue>(() => ({
        theme,
        toggleTheme,
    }), [theme, toggleTheme]);

    return (
        <ThemeContext.Provider value={value}>
            {children}
        </ThemeContext.Provider>
    );
};

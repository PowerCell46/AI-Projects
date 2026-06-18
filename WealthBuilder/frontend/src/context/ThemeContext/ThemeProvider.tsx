import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { ThemeContext } from './ThemeContext';
import type { Theme, ThemeContextValue } from './ThemeContext';
import { STORAGE_KEYS } from '../../constants/storage';


interface ThemeProviderProps {
    children: ReactNode;
}


const resolveInitialTheme = (): Theme => {
    const stored = localStorage.getItem(STORAGE_KEYS.THEME);

    if (stored === 'light' || stored === 'dark') {
        return stored;
    }

    return 'dark';
};


/**
 * Light/dark theme provider. The active theme is written to `data-theme` on the root
 * element; theme.css keys its app-level variables off that attribute.
 */
export const ThemeProvider = ({ children }: ThemeProviderProps) => {
    const [theme, setTheme] = useState<Theme>(resolveInitialTheme);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem(STORAGE_KEYS.THEME, theme);
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

import { useContext } from 'react';
import { ThemeContext } from './ThemeContext';
import type { ThemeContextValue } from './ThemeContext';


export const useTheme = (): ThemeContextValue => {
    const context = useContext(ThemeContext);

    if (context === null) {
        throw new Error('useTheme must be used within a ThemeProvider.');
    }

    return context;
};

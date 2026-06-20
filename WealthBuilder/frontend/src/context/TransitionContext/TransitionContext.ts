import { createContext } from 'react';


export interface TransitionContextValue {
    // Plays the VHS sweep once. No-op while one is already running or under reduced motion.
    play: () => void;
}


// Kept in its own file (no component export) so react-refresh stays happy.
export const TransitionContext = createContext<TransitionContextValue | null>(null);

import { createContext } from 'react';


// 'standard' is the in-app green sweep (retracts top-to-bottom); 'exit' is the same green sweep
// reversed (bottom-to-top) to signal leaving the app (logout).
export type SweepVariant = 'standard' | 'exit';


export interface TransitionContextValue {
    // Plays the VHS sweep once. No-op while one is already running or under reduced motion.
    play: (variant?: SweepVariant) => void;
}


// Kept in its own file (no component export) so react-refresh stays happy.
export const TransitionContext = createContext<TransitionContextValue | null>(null);

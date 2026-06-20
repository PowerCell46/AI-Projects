import { useContext } from 'react';
import { TransitionContext } from '../context/TransitionContext/TransitionContext';
import type { TransitionContextValue } from '../context/TransitionContext/TransitionContext';


/**
 * Access the view-transition controller. Calling play() runs the VHS sweep, used for in-page
 * view swaps that keep the same URL (e.g. the asset admin list <-> form).
 */
export const useViewTransition = (): TransitionContextValue => {
    const context = useContext(TransitionContext);

    if (context === null) {
        throw new Error('useViewTransition must be used within a TransitionProvider');
    }

    return context;
};

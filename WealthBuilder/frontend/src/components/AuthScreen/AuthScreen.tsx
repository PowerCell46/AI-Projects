import { Stage } from './Stage/Stage';
import { VhsBands } from '../VhsBands/VhsBands';
import { ThemeToggle } from '../ThemeToggle/ThemeToggle';
import { useModeTransition } from './useModeTransition';
import type { AuthMode } from '../../types/auth';
import styles from './AuthScreen.module.css';


interface AuthScreenProps {
    mode: AuthMode;
}

const noop = (): void => { };


/**
 * The single auth surface hosting both login and register. Renders the committed mode as
 * a base layer; during a mode swap it reveals the target mode top-down (clip-path driven
 * by the sweep clock) behind the VHS tracking bands. The CRT scanline and vignette
 * overlays sit above everything but never intercept pointer events.
 */
export const AuthScreen = ({ mode }: AuthScreenProps) => {
    const { sweepTo, progress, switchMode } = useModeTransition(mode);

    const isSweeping = sweepTo !== null;
    const revealBottomInset = (1 - progress) * 100;

    return (
        <div className={styles.viewport}>
            <div className={styles.themeToggle}>
                <ThemeToggle variant="phosphor" />
            </div>

            <div className={styles.stageFrame}>
                <Stage
                    mode={mode}
                    interactive={!isSweeping}
                    onSwitchMode={switchMode}
                />

                {isSweeping && (
                    <div
                        className={styles.sweepLayer}
                        style={{ clipPath: `inset(0 0 ${revealBottomInset}% 0)` }}
                    >
                        <Stage mode={sweepTo} interactive={false} onSwitchMode={noop} />
                    </div>
                )}

                {isSweeping && <VhsBands progress={progress} />}

                <div className={styles.scanlines} aria-hidden="true" />
                <div className={styles.vignette} aria-hidden="true" />
            </div>
        </div>
    );
};

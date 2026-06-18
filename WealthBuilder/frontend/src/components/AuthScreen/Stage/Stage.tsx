import { CoverPanel } from '../CoverPanel/CoverPanel';
import { AuthForm } from '../AuthForm/AuthForm';
import type { AuthMode } from '../../../types/auth';
import styles from './Stage.module.css';


interface StageProps {
    mode: AuthMode;
    interactive: boolean;
    onSwitchMode: () => void;
}


/**
 * A full stage composition for one mode: cover half + form half. In login mode the cover
 * sits left and the form right; register mirrors it. Owns the left/right placement so the
 * panels stay layout-agnostic.
 */
export const Stage = ({ mode, interactive, onSwitchMode }: StageProps) => {
    const coverOnLeft = mode === 'login';

    const coverSide = coverOnLeft ? styles.left : styles.right;
    const formSide = coverOnLeft ? styles.right : styles.left;

    return (
        <div className={`${styles.stage} ${interactive ? '' : styles.preview}`}>
            <div className={`${styles.half} ${coverSide}`}>
                <CoverPanel />
            </div>

            <div className={`${styles.half} ${formSide}`}>
                <AuthForm
                    mode={mode}
                    interactive={interactive}
                    onSwitchMode={onSwitchMode}
                />
            </div>
        </div>
    );
};

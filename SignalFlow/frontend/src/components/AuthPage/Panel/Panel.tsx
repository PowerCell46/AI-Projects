import type { AuthMode } from '../authMode';
import './Panel.css';

const COPY: Record<AuthMode, PanelCopy> = {
    signin: {
        heading: 'New to SignalFlow?',
        body: 'Create an account to follow topics and build a feed that only shows what you track.',
        button: 'Create account',
    },
    register: {
        heading: 'Already have an account?',
        body: 'Sign in to pick up your feed and the topics you already follow.',
        button: 'Sign in',
    },
};

export type PanelPosition = 'left' | 'right' | 'full';

interface PanelCopy {
    heading: string;
    body: string;
    button: string;
}

interface PanelProps {
    mode: AuthMode;
    position: PanelPosition;
    onSwitch: () => void;
}

function Panel({ mode, position, onSwitch }: PanelProps) {
    const copy = COPY[mode];

    return (
        <div className="auth-panel" data-position={position}>
            <div className="auth-panel-content">
                <p className="auth-panel-wordmark">SignalFlow</p>
                <h2 className="auth-panel-heading">{copy.heading}</h2>
                <p className="auth-panel-copy">{copy.body}</p>
                <button type="button" className="auth-panel-switch" onClick={onSwitch}>
                    {copy.button}
                </button>
            </div>
        </div>
    );
}

export default Panel;

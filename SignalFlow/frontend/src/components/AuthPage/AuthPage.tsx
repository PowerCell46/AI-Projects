import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AuthForm from './AuthForm/AuthForm';
import Panel from './Panel/Panel';
import type { PanelPosition } from './Panel/Panel';
import type { AuthMode } from './authMode';
import { useMediaQuery } from './useMediaQuery';
import type { AuthUser } from '../../api/auth';
import { ROUTES } from '../../routes';
import { REDUCED_MOTION_QUERY } from '../../utils/motion';
import './AuthPage.css';

const PATH_BY_MODE: Record<AuthMode, string> = {
    signin: ROUTES.login,
    register: ROUTES.register,
};

// Must match the stacked-layout media queries in AuthPage.css, AuthForm.css and Panel.css, or the
// switch animation runs against the wrong layout.
const NARROW_LAYOUT_QUERY = '(max-width: 900px), (max-width: 1100px) and (orientation: portrait)';

interface SwitchTiming {
    swapAtMs: number;
    finishAtMs: number;
}

// The swap must land while the panel still covers the form, so it falls inside the 340ms slide in
// Panel.css.
const PANEL_SLIDE_TIMING: SwitchTiming = {
    swapAtMs: 300,
    finishAtMs: 360,
};

// The swap lands at the end of the 200ms opacity fade on .auth-half in AuthPage.css.
const MOBILE_FADE_TIMING: SwitchTiming = {
    swapAtMs: 200,
    finishAtMs: 240,
};

function panelPositionFor(mode: AuthMode, isCovered: boolean): PanelPosition {
    if (isCovered) {
        return 'full';
    }

    return mode === 'signin' ? 'right' : 'left';
}

interface AuthPageProps {
    mode: AuthMode;
    isEntering: boolean;
    onAuthSuccess: (user: AuthUser, mode: AuthMode) => void;
}

function AuthPage({ mode, isEntering, onAuthSuccess }: AuthPageProps) {
    const navigate = useNavigate();
    const isNarrow = useMediaQuery(NARROW_LAYOUT_QUERY);
    const isReducedMotion = useMediaQuery(REDUCED_MOTION_QUERY);
    const [isCovered, setIsCovered] = useState(false);
    const [isMobileFading, setIsMobileFading] = useState(false);

    const isAnimatingRef = useRef(false);
    const timeoutIdsRef = useRef<number[]>([]);
    const signinEmailRef = useRef<HTMLInputElement>(null);
    const registerEmailRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const timeoutIds = timeoutIdsRef.current;

        return () => {
            timeoutIds.forEach((timeoutId) => window.clearTimeout(timeoutId));
        };
    }, []);

    function focusEmail(target: AuthMode) {
        const emailRef = target === 'signin' ? signinEmailRef : registerEmailRef;

        emailRef.current?.focus();
    }

    function playSwitch(target: AuthMode, timing: SwitchTiming, setIsRunning: (isRunning: boolean) => void) {
        isAnimatingRef.current = true;
        setIsRunning(true);

        const swapId = window.setTimeout(
            () => navigate(PATH_BY_MODE[target]),
            timing.swapAtMs,
        );

        const finishId = window.setTimeout(
            () => {
                setIsRunning(false);
                isAnimatingRef.current = false;
                focusEmail(target);
            },
            timing.finishAtMs,
        );

        timeoutIdsRef.current.push(swapId, finishId);
    }

    function switchTo(target: AuthMode) {
        if (target === mode || isAnimatingRef.current) {
            return;
        }

        if (isReducedMotion) {
            navigate(PATH_BY_MODE[target]);
            focusEmail(target);

            return;
        }

        if (isNarrow) {
            playSwitch(target, MOBILE_FADE_TIMING, setIsMobileFading);

            return;
        }

        playSwitch(target, PANEL_SLIDE_TIMING, setIsCovered);
    }

    const otherMode: AuthMode = mode === 'signin' ? 'register' : 'signin';

    return (
        <div className="auth-page">
            <div
                className="auth-container"
                data-mode={mode}
                data-mobile-fading={isMobileFading}
                data-entering={isEntering}
            >
                <div className="auth-half auth-half-left" data-active={mode === 'signin'}>
                    <AuthForm
                        mode="signin"
                        isActive={mode === 'signin'}
                        emailInputRef={signinEmailRef}
                        onRequestSwitch={() => switchTo('register')}
                        onAuthSuccess={(user) => onAuthSuccess(user, 'signin')}
                    />
                </div>

                <div className="auth-half auth-half-right" data-active={mode === 'register'}>
                    <AuthForm
                        mode="register"
                        isActive={mode === 'register'}
                        emailInputRef={registerEmailRef}
                        onRequestSwitch={() => switchTo('signin')}
                        onAuthSuccess={(user) => onAuthSuccess(user, 'register')}
                    />
                </div>

                <Panel
                    mode={mode}
                    position={panelPositionFor(mode, isCovered)}
                    onSwitch={() => switchTo(otherMode)}
                />
            </div>
        </div>
    );
}

export default AuthPage;

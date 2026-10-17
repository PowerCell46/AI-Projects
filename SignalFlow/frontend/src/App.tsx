import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import AuthPage from './components/AuthPage/AuthPage';
import type { AuthMode } from './components/AuthPage/authMode';
import HomePage from './components/HomePage/HomePage';
import SuccessCurtain from './components/SuccessCurtain/SuccessCurtain';
import { logout } from './api/auth';
import type { AuthUser } from './api/auth';
import { ROUTES } from './routes';
import { useAuthSession } from './useAuthSession';
import { useCurtain } from './useCurtain';
import type { CurtainGreeting } from './useCurtain';
import { isReducedMotionPreferred } from './utils/motion';

const SUCCESS_HEADING: Record<AuthMode, string> = {
    signin: 'Welcome back',
    register: 'Account created',
};

const SIGNED_OUT_HEADING = 'Signed out';

function App() {
    const navigate = useNavigate();
    const session = useAuthSession();
    const curtain = useCurtain();

    function completeSignIn(authUser: AuthUser) {
        session.startSession(authUser);
        navigate(ROUTES.home);
    }

    function completeSignOut() {
        session.endSession();
        navigate(ROUTES.login);
    }

    function handleAuthSuccess(authUser: AuthUser, mode: AuthMode) {
        if (curtain.isPlaying) {
            return;
        }

        if (isReducedMotionPreferred()) {
            completeSignIn(authUser);

            return;
        }

        const greeting: CurtainGreeting = {
            heading: SUCCESS_HEADING[mode],
            sub: authUser.email,
        };

        curtain.play(
            'up',
            greeting,
            () => completeSignIn(authUser),
        );
    }

    function handleSignOutRequest() {
        if (curtain.isPlaying) {
            return;
        }

        // A failed logout request still signs the user out locally.
        if (isReducedMotionPreferred()) {
            logout()
                .catch(() => {})
                .finally(completeSignOut);

            return;
        }

        const greeting: CurtainGreeting = {
            heading: SIGNED_OUT_HEADING,
            sub: session.user?.email ?? '',
        };

        logout()
            .catch(() => {});

        curtain.play(
            'down',
            greeting,
            completeSignOut,
        );
    }

    const isAuthenticated = session.status === 'authenticated';
    const isCurtainRevealing = curtain.phase === 'revealing';

    function renderAuthPage(mode: AuthMode) {
        if (isAuthenticated) {
            return <Navigate to={ROUTES.home} replace />;
        }

        return (
            <AuthPage
                mode={mode}
                isEntering={isCurtainRevealing}
                onAuthSuccess={handleAuthSuccess}
            />
        );
    }

    function renderHomePage() {
        if (!isAuthenticated) {
            return <Navigate to={ROUTES.login} replace />;
        }

        return (
            <HomePage
                isEntering={isCurtainRevealing}
                onSignOutRequest={handleSignOutRequest}
            />
        );
    }

    if (session.status === 'loading') {
        return null;
    }

    return (
        <>
            <Routes>
                <Route path={ROUTES.login} element={renderAuthPage('signin')} />
                <Route path={ROUTES.register} element={renderAuthPage('register')} />
                <Route path={ROUTES.home} element={renderHomePage()} />
                <Route path="*" element={<Navigate to={ROUTES.home} replace />} />
            </Routes>

            {curtain.phase !== null && curtain.greeting !== null && (
                <SuccessCurtain
                    phase={curtain.phase}
                    direction={curtain.direction}
                    heading={curtain.greeting.heading}
                    sub={curtain.greeting.sub}
                />
            )}
        </>
    );
}

export default App;

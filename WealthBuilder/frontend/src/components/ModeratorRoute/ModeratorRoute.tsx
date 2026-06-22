import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../../context/AuthContext/useAuth';
import { SessionLoader } from '../SessionLoader/SessionLoader';
import { APP_ROUTES } from '../../constants/routes';


interface ModeratorRouteProps {
    children: ReactNode;
}


/**
 * Gates moderator-only routes. Waits out session rehydration, bounces anonymous visitors to
 * login, and sends signed-in non-moderators home rather than showing a screen they can't use.
 */
export const ModeratorRoute = ({ children }: ModeratorRouteProps) => {
    const { status, user } = useAuth();

    if (status === 'loading') {
        return <SessionLoader />;
    }

    if (status === 'unauthenticated') {
        return <Navigate to={APP_ROUTES.LOGIN} replace />;
    }

    if (user?.role !== 'MODERATOR') {
        return <Navigate to={APP_ROUTES.HOME} replace />;
    }

    return <>{children}</>;
};

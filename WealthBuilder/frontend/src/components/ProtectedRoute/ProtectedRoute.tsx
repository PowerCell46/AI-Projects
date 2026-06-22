import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../../context/AuthContext/useAuth';
import { SessionLoader } from '../SessionLoader/SessionLoader';
import { APP_ROUTES } from '../../constants/routes';


interface ProtectedRouteProps {
    children: ReactNode;
}


/**
 * Gates authenticated routes. While the session is rehydrating it shows a terminal-style
 * loader; once resolved it either renders the children or bounces to /login.
 */
export const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
    const { status } = useAuth();

    if (status === 'loading') {
        return <SessionLoader />;
    }

    if (status === 'unauthenticated') {
        return <Navigate to={APP_ROUTES.LOGIN} replace />;
    }

    return <>{children}</>;
};

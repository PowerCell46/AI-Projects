import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../../context/AuthContext/useAuth';
import { APP_ROUTES } from '../../constants/routes';


interface PublicOnlyRouteProps {
    children: ReactNode;
}


/**
 * Wraps the login/register screens. An already-authenticated visitor is sent straight to
 * the home screen instead of seeing the auth surface again.
 */
export const PublicOnlyRoute = ({ children }: PublicOnlyRouteProps) => {
    const { status } = useAuth();

    if (status === 'authenticated') {
        return <Navigate to={APP_ROUTES.HOME} replace />;
    }

    return <>{children}</>;
};

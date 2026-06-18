import { Navigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../../context/AuthContext/useAuth';
import { APP_ROUTES } from '../../constants/routes';
import styles from './ProtectedRoute.module.css';


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
        return <div className={styles.loader}>booting…</div>;
    }

    if (status === 'unauthenticated') {
        return <Navigate to={APP_ROUTES.LOGIN} replace />;
    }

    return <>{children}</>;
};

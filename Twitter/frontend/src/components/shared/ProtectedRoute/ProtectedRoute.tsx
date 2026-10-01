import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../../contexts/AuthContext';
import { ROUTES } from '../../../routes';


function ProtectedRoute() {
    const { status } = useAuth();

    if (status === 'loading') {
        return null;
    }

    if (status === 'anonymous') {
        return <Navigate to={ROUTES.login} replace />;
    }

    return <Outlet />;
}

export default ProtectedRoute;

import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../../contexts/AuthContext';
import { ROUTES } from '../../../routes';


function GuestRoute() {
    const { status } = useAuth();

    if (status === 'loading') {
        return null;
    }

    if (status === 'authenticated') {
        return <Navigate to={ROUTES.feed} replace />;
    }

    return <Outlet />;
}

export default GuestRoute;

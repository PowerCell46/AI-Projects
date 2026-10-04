import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../../contexts/AuthContext';
import { ROUTES } from '../../../routes';
import { ASCENT_LOCATION_STATE } from '../../../utils/ascent';


function ProtectedRoute() {
    const { status, hasSignedOut } = useAuth();

    if (status === 'loading') {
        return null;
    }

    if (status === 'anonymous') {
        return (
            <Navigate
                to={ROUTES.login}
                replace
                state={hasSignedOut ? ASCENT_LOCATION_STATE : null}
            />
        );
    }

    return <Outlet />;
}

export default ProtectedRoute;

import { Navigate } from 'react-router-dom';
import { useAuth } from '../../../contexts/AuthContext';
import { ROUTES } from '../../../routes';


function SessionRedirect() {
    const { status } = useAuth();

    if (status === 'loading') {
        return null;
    }

    return <Navigate to={status === 'authenticated' ? ROUTES.feed : ROUTES.login} replace />;
}

export default SessionRedirect;

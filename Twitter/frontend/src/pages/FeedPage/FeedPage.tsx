import { useState } from 'react';
import { logout } from '../../api/auth';
import DescentStage from '../../components/shared/DescentStage/DescentStage';
import { useAuth } from '../../contexts/AuthContext';
import { SIGNAL_LOST_MESSAGE } from '../../utils/authErrors';
import './FeedPage.css';


const FEED_DEPTH_METRES = 140;

const FEED_SEAFLOOR_DEPTH_METRES = 4900;

function FeedPage() {
    const { user, signOut } = useAuth();
    const [hasLogoutFailed, setHasLogoutFailed] = useState(false);
    const [isLoggingOut, setIsLoggingOut] = useState(false);

    // Signing out makes ProtectedRoute send the user to the login page.
    async function handleLogout() {
        if (isLoggingOut) {
            return;
        }

        setIsLoggingOut(true);
        setHasLogoutFailed(false);

        try {
            await logout();

            signOut();

        } catch {
            setHasLogoutFailed(true);
            setIsLoggingOut(false);
        }
    }

    return (
        <DescentStage depthMetres={FEED_DEPTH_METRES} seafloorDepthMetres={FEED_SEAFLOOR_DEPTH_METRES}>
            <h1 className="feed-page-username">@{user?.username}</h1>
            <button
                type="button"
                className="feed-page-logout"
                aria-disabled={isLoggingOut}
                onClick={handleLogout}
            >
                LOG OUT
            </button>
            <p className="feed-page-error" role="alert">{hasLogoutFailed ? SIGNAL_LOST_MESSAGE : ''}</p>
        </DescentStage>
    );
}

export default FeedPage;

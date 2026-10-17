import { useState } from 'react';
import Feed from './Feed/Feed';
import './HomePage.css';

interface HomePageProps {
    isEntering: boolean;
    onSignOutRequest: () => void;
}

function HomePage({ isEntering, onSignOutRequest }: HomePageProps) {
    const [isSigningOut, setIsSigningOut] = useState(false);

    function handleSignOut() {
        if (isSigningOut) {
            return;
        }

        setIsSigningOut(true);
        onSignOutRequest();
    }

    return (
        <div className="home-page">
            <header className="home-header">
                <span className="home-wordmark">SignalFlow</span>
                <button
                    type="button"
                    className="home-signout"
                    disabled={isSigningOut}
                    onClick={handleSignOut}
                >
                    {isSigningOut ? 'Signing out' : 'Sign out'}
                </button>
            </header>

            <main className="home-main" data-entering={isEntering}>
                <Feed />
            </main>
        </div>
    );
}

export default HomePage;

import { useState } from 'react'
import type { AuthUser } from '../../api/auth'
import Feed from './Feed/Feed'
import './HomePage.css'

interface HomePageProps {
    user: AuthUser
    onSignOutRequest: () => void
    entering: boolean
}

function HomePage({ onSignOutRequest, entering }: HomePageProps) {
    const [signingOut, setSigningOut] = useState(false)

    function handleSignOut() {
        if (signingOut) {
            return
        }

        setSigningOut(true)
        onSignOutRequest()
    }

    return (
        <div className="home-page">
            <header className="home-header">
                <span className="home-wordmark">signalflow</span>
                <button type="button" className="home-signout" onClick={handleSignOut} disabled={signingOut}>
                    {signingOut ? 'Signing out' : 'Sign out'}
                </button>
            </header>

            <main className="home-main" data-entering={entering}>
                <Feed />
            </main>
        </div>
    )
}

export default HomePage

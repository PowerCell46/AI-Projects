import { useState } from 'react'
import { logout } from '../../api/auth'
import type { AuthUser } from '../../api/auth'
import Feed from './Feed/Feed'
import './HomePage.css'

interface HomePageProps {
    user: AuthUser
    onSignedOut: () => void
}

function HomePage({ onSignedOut }: HomePageProps) {
    const [signingOut, setSigningOut] = useState(false)

    async function handleSignOut() {
        if (signingOut) {
            return
        }

        setSigningOut(true)

        try {
            await logout()
        } finally {
            onSignedOut()
        }
    }

    return (
        <div className="home-page">
            <header className="home-header">
                <span className="home-wordmark">signalflow</span>
                <button type="button" className="home-signout" onClick={handleSignOut} disabled={signingOut}>
                    {signingOut ? 'Signing out' : 'Sign out'}
                </button>
            </header>

            <main className="home-main">
                <Feed />
            </main>
        </div>
    )
}

export default HomePage

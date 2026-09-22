import { useEffect, useState } from 'react'
import AuthPage from './components/AuthPage/AuthPage'
import HomePage from './components/HomePage/HomePage'
import { me } from './api/auth'
import type { AuthUser } from './api/auth'

type Status = 'loading' | 'authenticated' | 'unauthenticated'

function App() {
    const [status, setStatus] = useState<Status>('loading')
    const [user, setUser] = useState<AuthUser | null>(null)

    useEffect(() => {
        let cancelled = false

        me()
            .then((result) => {
                if (cancelled) {
                    return
                }
                setUser(result)
                setStatus('authenticated')
            })
            .catch(() => {
                if (cancelled) {
                    return
                }
                setStatus('unauthenticated')
            })

        return () => {
            cancelled = true
        }
    }, [])

    if (status === 'loading') {
        return null
    }

    if (status === 'authenticated' && user) {
        return <HomePage user={user} onSignedOut={() => setStatus('unauthenticated')} />
    }

    return <AuthPage />
}

export default App

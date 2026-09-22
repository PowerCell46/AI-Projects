import { useEffect, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
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

    return (
        <Routes>
            <Route
                path="/login"
                element={status === 'authenticated' ? <Navigate to="/" replace /> : <AuthPage mode="signin" />}
            />
            <Route
                path="/register"
                element={status === 'authenticated' ? <Navigate to="/" replace /> : <AuthPage mode="register" />}
            />
            <Route
                path="/"
                element={
                    status === 'authenticated' && user ? (
                        <HomePage user={user} onSignedOut={() => setStatus('unauthenticated')} />
                    ) : (
                        <Navigate to="/login" replace />
                    )
                }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    )
}

export default App

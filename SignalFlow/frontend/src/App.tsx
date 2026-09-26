import { useEffect, useRef, useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import AuthPage from './components/AuthPage/AuthPage'
import HomePage from './components/HomePage/HomePage'
import SuccessCurtain from './components/SuccessCurtain/SuccessCurtain'
import type { CurtainDirection, CurtainPhase } from './components/SuccessCurtain/SuccessCurtain'
import type { Mode } from './components/AuthPage/Panel/Panel'
import { logout, me } from './api/auth'
import type { AuthUser } from './api/auth'

type Status = 'loading' | 'authenticated' | 'unauthenticated'

const CURTAIN_COVER_MS = 340
const CURTAIN_HOLD_MS = 1400
const CURTAIN_REVEAL_MS = 340

const SUCCESS_HEADING: Record<Mode, string> = {
    signin: 'Welcome back',
    register: 'Account created',
}

function App() {
    const navigate = useNavigate()
    const [status, setStatus] = useState<Status>('loading')
    const [user, setUser] = useState<AuthUser | null>(null)
    const [curtainPhase, setCurtainPhase] = useState<CurtainPhase | null>(null)
    const [curtainDirection, setCurtainDirection] = useState<CurtainDirection>('up')
    const [curtainGreeting, setCurtainGreeting] = useState<{ heading: string; sub: string } | null>(null)

    const timeoutIdsRef = useRef<number[]>([])

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

    useEffect(() => {
        const timeoutIds = timeoutIdsRef.current

        return () => {
            timeoutIds.forEach((id) => window.clearTimeout(id))
        }
    }, [])

    function scheduleCurtain(swap: () => void) {
        const swapId = window.setTimeout(() => {
            swap()
            setCurtainPhase('covered')
        }, CURTAIN_COVER_MS)

        const revealId = window.setTimeout(() => {
            setCurtainPhase('revealing')
        }, CURTAIN_COVER_MS + CURTAIN_HOLD_MS)

        const doneId = window.setTimeout(() => {
            setCurtainPhase(null)
            setCurtainGreeting(null)
        }, CURTAIN_COVER_MS + CURTAIN_HOLD_MS + CURTAIN_REVEAL_MS)

        timeoutIdsRef.current.push(swapId, revealId, doneId)
    }

    function handleAuthSuccess(authUser: AuthUser, mode: Mode) {
        if (curtainPhase !== null) {
            return
        }

        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            setUser(authUser)
            setStatus('authenticated')
            navigate('/')

            return
        }

        setCurtainGreeting({
            heading: SUCCESS_HEADING[mode],
            sub: authUser.email,
        })
        setCurtainDirection('up')
        setCurtainPhase('covering')

        scheduleCurtain(() => {
            setUser(authUser)
            setStatus('authenticated')
            navigate('/')
        })
    }

    function handleSignOutRequest() {
        if (curtainPhase !== null) {
            return
        }

        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            logout()
                .catch(() => {})
                .finally(() => {
                    setUser(null)
                    setStatus('unauthenticated')
                    navigate('/login')
                })

            return
        }

        setCurtainGreeting({
            heading: 'Signed out',
            sub: user?.email ?? '',
        })
        setCurtainDirection('down')
        setCurtainPhase('covering')

        logout()
            .catch(() => {})

        scheduleCurtain(() => {
            setUser(null)
            setStatus('unauthenticated')
            navigate('/login')
        })
    }

    if (status === 'loading') {
        return null
    }

    return (
        <>
            <Routes>
                <Route
                    path="/login"
                    element={status === 'authenticated' ? <Navigate to="/" replace /> : <AuthPage mode="signin" onAuthSuccess={handleAuthSuccess} entering={curtainPhase === 'revealing'} />}
                />
                <Route
                    path="/register"
                    element={status === 'authenticated' ? <Navigate to="/" replace /> : <AuthPage mode="register" onAuthSuccess={handleAuthSuccess} entering={curtainPhase === 'revealing'} />}
                />
                <Route
                    path="/"
                    element={
                        status === 'authenticated' && user ? (
                            <HomePage user={user} onSignOutRequest={handleSignOutRequest} entering={curtainPhase === 'revealing'} />
                        ) : (
                            <Navigate to="/login" replace />
                        )
                    }
                />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            {curtainPhase !== null && curtainGreeting !== null && (
                <SuccessCurtain
                    phase={curtainPhase}
                    direction={curtainDirection}
                    heading={curtainGreeting.heading}
                    sub={curtainGreeting.sub}
                />
            )}
        </>
    )
}

export default App

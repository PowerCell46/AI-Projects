import { useEffect, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import AuthForm from './AuthForm/AuthForm'
import Panel from './Panel/Panel'
import type { Mode } from './Panel/Panel'
import type { AuthUser } from '../../api/auth'
import './AuthPage.css'

const PATH_BY_MODE: Record<Mode, string> = {
    signin: '/login',
    register: '/register',
}

const COVER_AT_MS = 300
const UNCOVER_AT_MS = 360
const MOBILE_FADE_SWAP_MS = 200
const MOBILE_FADE_REVEAL_MS = 240

function useMediaQuery(query: string): boolean {
    const [matches, setMatches] = useState(() => window.matchMedia(query).matches)

    useEffect(() => {
        const mediaQueryList = window.matchMedia(query)

        function handleChange(event: MediaQueryListEvent) {
            setMatches(event.matches)
        }

        mediaQueryList.addEventListener('change', handleChange)

        return () => {
            mediaQueryList.removeEventListener('change', handleChange)
        }
    }, [query])

    return matches
}

interface AuthPageProps {
    mode: Mode
    onAuthSuccess: (user: AuthUser, mode: Mode) => void
    entering: boolean
}

function AuthPage({ mode, onAuthSuccess, entering }: AuthPageProps) {
    const navigate = useNavigate()
    const [covered, setCovered] = useState(false)
    const [mobileFading, setMobileFading] = useState(false)

    const isAnimatingRef = useRef(false)
    const timeoutIdsRef = useRef<number[]>([])
    const signinEmailRef = useRef<HTMLInputElement>(null)
    const registerEmailRef = useRef<HTMLInputElement>(null)

    const isNarrow = useMediaQuery('(max-width: 900px), (max-width: 1100px) and (orientation: portrait)')
    const prefersReducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')

    useEffect(() => {
        const timeoutIds = timeoutIdsRef.current

        return () => {
            timeoutIds.forEach((id) => window.clearTimeout(id))
        }
    }, [])

    function focusEmail(target: Mode) {
        const ref = target === 'signin' ? signinEmailRef : registerEmailRef
        ref.current?.focus()
    }

    function switchTo(target: Mode) {
        if (target === mode || isAnimatingRef.current) {
            return
        }

        const path = PATH_BY_MODE[target]

        if (prefersReducedMotion) {
            navigate(path)
            focusEmail(target)
            return
        }

        if (isNarrow) {
            isAnimatingRef.current = true
            setMobileFading(true)

            const swapId = window.setTimeout(() => {
                navigate(path)
            }, MOBILE_FADE_SWAP_MS)

            const revealId = window.setTimeout(() => {
                setMobileFading(false)
                isAnimatingRef.current = false
                focusEmail(target)
            }, MOBILE_FADE_REVEAL_MS)

            timeoutIdsRef.current.push(swapId, revealId)
            return
        }

        isAnimatingRef.current = true
        setCovered(true)

        const swapId = window.setTimeout(() => {
            navigate(path)
        }, COVER_AT_MS)

        const uncoverId = window.setTimeout(() => {
            setCovered(false)
            isAnimatingRef.current = false
            focusEmail(target)
        }, UNCOVER_AT_MS)

        timeoutIdsRef.current.push(swapId, uncoverId)
    }

    const panelStyle: CSSProperties = covered
        ? { left: 0, width: '100%' }
        : mode === 'signin'
          ? { left: '50%', width: '50%' }
          : { left: 0, width: '50%' }

    return (
        <div className="auth-page">
            <div className="auth-container" data-mode={mode} data-mobile-fading={mobileFading} data-entering={entering}>
                <div className="auth-half auth-half-left" data-active={mode === 'signin'}>
                    <AuthForm
                        mode="signin"
                        active={mode === 'signin'}
                        emailInputRef={signinEmailRef}
                        onRequestSwitch={() => switchTo('register')}
                        onAuthSuccess={(user) => onAuthSuccess(user, 'signin')}
                    />
                </div>

                <div className="auth-half auth-half-right" data-active={mode === 'register'}>
                    <AuthForm
                        mode="register"
                        active={mode === 'register'}
                        emailInputRef={registerEmailRef}
                        onRequestSwitch={() => switchTo('signin')}
                        onAuthSuccess={(user) => onAuthSuccess(user, 'register')}
                    />
                </div>

                <Panel mode={mode} style={panelStyle} onSwitch={() => switchTo(mode === 'signin' ? 'register' : 'signin')} />
            </div>
        </div>
    )
}

export default AuthPage

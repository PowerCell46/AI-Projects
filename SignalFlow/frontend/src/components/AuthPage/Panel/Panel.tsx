import type { CSSProperties } from 'react'
import './Panel.css'

export type Mode = 'signin' | 'register'

interface PanelProps {
    mode: Mode
    style: CSSProperties
    onSwitch: () => void
}

const COPY: Record<Mode, { heading: string; body: string; button: string }> = {
    signin: {
        heading: 'New to SignalFlow?',
        body: 'Create an account to follow topics and build a feed that only shows what you track.',
        button: 'Create account',
    },
    register: {
        heading: 'Already have an account?',
        body: 'Sign in to pick up your feed and the topics you already follow.',
        button: 'Sign in',
    },
}

function Panel({ mode, style, onSwitch }: PanelProps) {
    const copy = COPY[mode]

    return (
        <div className="auth-panel" style={style}>
            <div className="auth-panel-content">
                <p className="auth-panel-wordmark">signalflow</p>
                <h2 className="auth-panel-heading">{copy.heading}</h2>
                <p className="auth-panel-copy">{copy.body}</p>
                <button type="button" className="auth-panel-switch" onClick={onSwitch}>
                    {copy.button}
                </button>
            </div>
        </div>
    )
}

export default Panel

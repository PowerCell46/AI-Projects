import { useState } from 'react'
import type { ChangeEvent, FormEvent, RefObject } from 'react'
import { AuthApiError, login, register } from '../../../api/auth'
import './AuthForm.css'

export type Mode = 'signin' | 'register'

type Phase = 'idle' | 'submitting' | 'error'

type InvalidField = 'email' | 'password'

interface AuthFormProps {
    mode: Mode
    active: boolean
    emailInputRef: RefObject<HTMLInputElement | null>
    onRequestSwitch: () => void
}

const COPY: Record<Mode, { heading: string; sub: string; submit: string; submitting: string }> = {
    signin: {
        heading: 'Sign in',
        sub: 'Pick up your feed where you left it.',
        submit: 'Sign in',
        submitting: 'Signing in',
    },
    register: {
        heading: 'Create account',
        sub: 'Start tracking the topics you care about.',
        submit: 'Create account',
        submitting: 'Creating account',
    },
}

// Mirrors RegisterRequestDTO/LoginRequestDTO's @Pattern rules on the gateway — kept in sync with
// signal_flow_api_gateway so obviously-invalid input never reaches the network.
const EMAIL_MAX_LENGTH = 254
const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/

const PASSWORD_MIN_LENGTH = 8
const PASSWORD_MAX_LENGTH = 72
const PASSWORD_COMPLEXITY_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/

function validateEmail(value: string): string | null {
    const trimmed = value.trim()

    if (!trimmed) {
        return 'Enter your email.'
    }
    if (trimmed.length > EMAIL_MAX_LENGTH) {
        return `Email must be ${EMAIL_MAX_LENGTH} characters or fewer.`
    }
    if (!EMAIL_PATTERN.test(trimmed)) {
        return 'Enter a valid email address.'
    }

    return null
}

function validatePassword(value: string): string | null {
    if (!value) {
        return 'Enter your password.'
    }
    if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
        return `Password must be ${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH} characters.`
    }
    if (!PASSWORD_COMPLEXITY_PATTERN.test(value)) {
        return 'Password needs an uppercase letter, a lowercase letter, and a number.'
    }

    return null
}

function AuthForm({ mode, active, emailInputRef, onRequestSwitch }: AuthFormProps) {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [phase, setPhase] = useState<Phase>('idle')
    const [errorMessage, setErrorMessage] = useState('')
    const [invalidFields, setInvalidFields] = useState<Set<InvalidField>>(new Set())
    const [showPassword, setShowPassword] = useState(false)

    const isSignIn = mode === 'signin'
    const copy = COPY[mode]
    const emailId = `${mode}-email`
    const passwordId = `${mode}-password`
    const isSubmitting = phase === 'submitting'

    function handleFieldChange(field: InvalidField, event: ChangeEvent<HTMLInputElement>) {
        if (field === 'email') {
            setEmail(event.target.value)
        } else {
            setPassword(event.target.value)
        }

        if (phase === 'error') {
            setPhase('idle')
            setErrorMessage('')
            setInvalidFields(new Set())
        }
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        if (isSubmitting) {
            return
        }

        const emailError = validateEmail(email)
        const passwordError = validatePassword(password)

        if (emailError || passwordError) {
            const fields = new Set<InvalidField>()
            if (emailError) {
                fields.add('email')
            }
            if (passwordError) {
                fields.add('password')
            }

            setPhase('error')
            setErrorMessage(emailError ?? passwordError ?? '')
            setInvalidFields(fields)
            return
        }

        setPhase('submitting')
        setErrorMessage('')
        setInvalidFields(new Set())

        try {
            if (isSignIn) {
                await login({ email, password })
            } else {
                await register({ email, password })
            }
            window.location.assign('/')
        } catch (error) {
            if (isSignIn && error instanceof AuthApiError && error.status === 401) {
                setErrorMessage("That email and password don't match an account.")
                setInvalidFields(new Set(['email', 'password']))
            } else if (!isSignIn && error instanceof AuthApiError && error.status === 409) {
                setErrorMessage('An account already exists for that email.')
                setInvalidFields(new Set(['email']))
            } else {
                setErrorMessage("Couldn't reach the server. Try again.")
            }
            setPhase('error')
        }
    }

    return (
        <div className="auth-form-wrap" aria-hidden={!active} inert={!active}>
            <form className="auth-form" onSubmit={handleSubmit} noValidate>
                <h1 className="auth-form-heading">{copy.heading}</h1>
                <p className="auth-form-sub">{copy.sub}</p>

                <div className="auth-fields">
                    <div className="auth-field">
                        <label htmlFor={emailId} className="auth-label">
                            Email
                        </label>
                        <input
                            id={emailId}
                            ref={emailInputRef}
                            className="auth-input"
                            type="email"
                            autoComplete="email"
                            maxLength={EMAIL_MAX_LENGTH}
                            value={email}
                            onChange={(event) => handleFieldChange('email', event)}
                            aria-invalid={invalidFields.has('email')}
                            disabled={isSubmitting}
                        />
                    </div>

                    <div className="auth-field">
                        <label htmlFor={passwordId} className="auth-label">
                            Password
                        </label>
                        <div className="auth-password-wrap">
                            <input
                                id={passwordId}
                                className="auth-input auth-input-password"
                                type={showPassword ? 'text' : 'password'}
                                autoComplete={isSignIn ? 'current-password' : 'new-password'}
                                maxLength={PASSWORD_MAX_LENGTH}
                                value={password}
                                onChange={(event) => handleFieldChange('password', event)}
                                aria-invalid={invalidFields.has('password')}
                                disabled={isSubmitting}
                            />
                            <button
                                type="button"
                                className="auth-password-toggle"
                                onClick={() => setShowPassword((prev) => !prev)}
                                aria-pressed={showPassword}
                                aria-label={showPassword ? 'Hide password' : 'Show password'}
                            >
                                {showPassword ? 'Hide' : 'Show'}
                            </button>
                        </div>
                    </div>
                </div>

                <p className="auth-error" role="alert">
                    {phase === 'error' ? errorMessage : ''}
                </p>

                <button type="submit" className="auth-submit" disabled={isSubmitting}>
                    {isSubmitting ? copy.submitting : copy.submit}
                </button>

                <p className="auth-mobile-switch">
                    {isSignIn ? 'New here? ' : 'Already have an account? '}
                    <button type="button" className="auth-mobile-switch-link" onClick={onRequestSwitch}>
                        {isSignIn ? 'Create an account' : 'Sign in'}
                    </button>
                </p>
            </form>
        </div>
    )
}

export default AuthForm

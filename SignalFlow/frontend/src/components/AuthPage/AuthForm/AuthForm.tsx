import { useState } from 'react';
import type { ChangeEvent, FormEvent, RefObject } from 'react';
import { login, register } from '../../../api/auth';
import type { AuthCredentials, AuthUser } from '../../../api/auth';
import { ApiError } from '../../../api/request';
import type { AuthMode } from '../authMode';
import { EMAIL_MAX_LENGTH, PASSWORD_MAX_LENGTH, validateEmail, validatePassword } from './validation';
import './AuthForm.css';

const HTTP_UNAUTHORIZED = 401;

const HTTP_CONFLICT = 409;

const INVALID_CREDENTIALS_MESSAGE = "That email and password don't match an account.";

const DUPLICATE_ACCOUNT_MESSAGE = 'An account already exists for that email.';

const SERVER_UNREACHABLE_MESSAGE = "Couldn't reach the server. Try again.";

const COPY: Record<AuthMode, AuthFormCopy> = {
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
};

type FormPhase = 'idle' | 'submitting' | 'error';

type InvalidField = 'email' | 'password';

interface AuthFormCopy {
    heading: string;
    sub: string;
    submit: string;
    submitting: string;
}

interface FormFailure {
    message: string;
    invalidFields: InvalidField[];
}

function validateCredentials(email: string, password: string): FormFailure | null {
    const emailError = validateEmail(email);
    const passwordError = validatePassword(password);

    if (!emailError && !passwordError) {
        return null;
    }

    const invalidFields: InvalidField[] = [];

    if (emailError) {
        invalidFields.push('email');
    }

    if (passwordError) {
        invalidFields.push('password');
    }

    return {
        message: emailError ?? passwordError ?? '',
        invalidFields,
    };
}

// The design's copy differs from the gateway's own messages, so the status picks the text.
function failureFromError(mode: AuthMode, error: unknown): FormFailure {
    const status = error instanceof ApiError ? error.status : null;

    if (mode === 'signin' && status === HTTP_UNAUTHORIZED) {
        return {
            message: INVALID_CREDENTIALS_MESSAGE,
            invalidFields: ['email', 'password'],
        };
    }

    if (mode === 'register' && status === HTTP_CONFLICT) {
        return {
            message: DUPLICATE_ACCOUNT_MESSAGE,
            invalidFields: ['email'],
        };
    }

    return {
        message: SERVER_UNREACHABLE_MESSAGE,
        invalidFields: [],
    };
}

function authenticate(mode: AuthMode, credentials: AuthCredentials): Promise<AuthUser> {
    return mode === 'signin' ? login(credentials) : register(credentials);
}

interface AuthFormProps {
    mode: AuthMode;
    isActive: boolean;
    emailInputRef: RefObject<HTMLInputElement | null>;
    onRequestSwitch: () => void;
    onAuthSuccess: (user: AuthUser) => void;
}

function AuthForm({ mode, isActive, emailInputRef, onRequestSwitch, onAuthSuccess }: AuthFormProps) {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [phase, setPhase] = useState<FormPhase>('idle');
    const [errorMessage, setErrorMessage] = useState('');
    const [invalidFields, setInvalidFields] = useState<Set<InvalidField>>(new Set());
    const [isPasswordVisible, setIsPasswordVisible] = useState(false);

    const isSignIn = mode === 'signin';
    const isSubmitting = phase === 'submitting';
    const copy = COPY[mode];
    const emailId = `${mode}-email`;
    const passwordId = `${mode}-password`;

    function clearFailure() {
        setErrorMessage('');
        setInvalidFields(new Set());
    }

    function showFailure(failure: FormFailure) {
        setPhase('error');
        setErrorMessage(failure.message);
        setInvalidFields(new Set(failure.invalidFields));
    }

    function resetAfterEdit() {
        if (phase !== 'error') {
            return;
        }

        setPhase('idle');
        clearFailure();
    }

    function handleEmailChange(event: ChangeEvent<HTMLInputElement>) {
        setEmail(event.target.value);
        resetAfterEdit();
    }

    function handlePasswordChange(event: ChangeEvent<HTMLInputElement>) {
        setPassword(event.target.value);
        resetAfterEdit();
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (isSubmitting) {
            return;
        }

        const validationFailure = validateCredentials(email, password);

        if (validationFailure) {
            showFailure(validationFailure);

            return;
        }

        const credentials: AuthCredentials = {
            email,
            password,
        };

        setPhase('submitting');
        clearFailure();

        try {
            const user = await authenticate(mode, credentials);

            onAuthSuccess(user);

        } catch (error) {
            showFailure(failureFromError(mode, error));
        }
    }

    return (
        <div className="auth-form-wrap" aria-hidden={!isActive} inert={!isActive}>
            <form className="auth-form" noValidate onSubmit={handleSubmit}>
                <h1 className="auth-form-heading">{copy.heading}</h1>
                <p className="auth-form-sub">{copy.sub}</p>

                <div className="auth-fields">
                    <div className="auth-field">
                        <label className="auth-label" htmlFor={emailId}>
                            Email
                        </label>
                        <input
                            id={emailId}
                            type="email"
                            className="auth-input"
                            aria-invalid={invalidFields.has('email')}
                            autoComplete="email"
                            disabled={isSubmitting}
                            maxLength={EMAIL_MAX_LENGTH}
                            value={email}
                            ref={emailInputRef}
                            onChange={handleEmailChange}
                        />
                    </div>

                    <div className="auth-field">
                        <label className="auth-label" htmlFor={passwordId}>
                            Password
                        </label>
                        <div className="auth-password-wrap">
                            <input
                                id={passwordId}
                                type={isPasswordVisible ? 'text' : 'password'}
                                className="auth-input auth-input-password"
                                aria-invalid={invalidFields.has('password')}
                                autoComplete={isSignIn ? 'current-password' : 'new-password'}
                                disabled={isSubmitting}
                                maxLength={PASSWORD_MAX_LENGTH}
                                value={password}
                                onChange={handlePasswordChange}
                            />
                            <button
                                type="button"
                                className="auth-password-toggle"
                                aria-label={isPasswordVisible ? 'Hide password' : 'Show password'}
                                aria-pressed={isPasswordVisible}
                                onClick={() => setIsPasswordVisible((wasVisible) => !wasVisible)}
                            >
                                {isPasswordVisible ? 'Hide' : 'Show'}
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
    );
}

export default AuthForm;

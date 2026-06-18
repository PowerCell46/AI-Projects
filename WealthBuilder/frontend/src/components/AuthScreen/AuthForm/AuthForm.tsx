import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TerminalInput } from '../TerminalInput/TerminalInput';
import { AUTH_COPY } from '../authCopy';
import { useAuth } from '../../../context/AuthContext/useAuth';
import { APP_ROUTES } from '../../../constants/routes';
import { ApiError } from '../../../types/problem';
import type { AuthMode, Credentials } from '../../../types/auth';
import styles from './AuthForm.module.css';


interface AuthFormProps {
    mode: AuthMode;
    interactive: boolean;
    onSwitchMode: () => void;
}


type FieldErrors = Partial<Record<keyof Credentials, string>>;


/**
 * The login/register form half. Submits to the backend through the auth context, surfaces
 * field-level and form-level errors, and on success routes to the home screen.
 *
 * When `interactive` is false the form is a static preview (used by the VHS sweep layer).
 */
export const AuthForm = ({ mode, interactive, onSwitchMode }: AuthFormProps) => {
    const copy = AUTH_COPY[mode];
    const { login, register } = useAuth();
    const navigate = useNavigate();

    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
    const [formError, setFormError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const canSubmit = interactive
        && !submitting
        && username.trim().length > 0
        && password.length > 0;

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault();

        if (!canSubmit) {
            return;
        }

        setSubmitting(true);
        setFormError(null);
        setFieldErrors({});

        const credentials: Credentials = { username: username.trim(), password };

        try {
            await submitCredentials(credentials);
            navigate(APP_ROUTES.HOME, { replace: true });
        } catch (error) {
            applyError(error);
        } finally {
            setSubmitting(false);
        }
    };

    const submitCredentials = (credentials: Credentials): Promise<void> => {
        if (mode === 'login') {
            return login(credentials);
        }

        return register(credentials);
    };

    // Split an ApiError into per-field helpers (validation) and a form-level banner.
    const applyError = (error: unknown): void => {
        if (error instanceof ApiError) {
            setFieldErrors(error.fieldErrors as FieldErrors);
            setFormError(Object.keys(error.fieldErrors).length > 0 ? null : error.detail);

            return;
        }

        setFormError('Could not reach the server. Try again.');
    };

    return (
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
            <p className={styles.eyebrow}>{copy.eyebrow}</p>

            <h1 className={styles.heading}>
                {copy.heading}
                <span className={styles.cursor} aria-hidden="true">_</span>
            </h1>

            <div className={styles.fields}>
                <TerminalInput
                    id={`${mode}-username`}
                    label="USR"
                    type="text"
                    value={username}
                    autoComplete="username"
                    disabled={!interactive}
                    error={fieldErrors.username}
                    onChange={setUsername}
                />

                <TerminalInput
                    id={`${mode}-password`}
                    label="PWD"
                    type="password"
                    value={password}
                    autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                    disabled={!interactive}
                    error={fieldErrors.password}
                    revealable
                    onChange={setPassword}
                />
            </div>

            {formError !== null && (
                <p className={styles.formError} role="alert">! {formError}</p>
            )}

            <button type="submit" className={styles.submit} disabled={!canSubmit}>
                {submitting ? '[ ... ]' : copy.submitLabel}
            </button>

            <p className={styles.switchLine}>
                {copy.switchPrompt}{' '}
                <button
                    type="button"
                    className={styles.switchAction}
                    onClick={onSwitchMode}
                    disabled={!interactive}
                    tabIndex={interactive ? 0 : -1}
                >
                    {copy.switchAction}
                </button>
            </p>
        </form>
    );
};

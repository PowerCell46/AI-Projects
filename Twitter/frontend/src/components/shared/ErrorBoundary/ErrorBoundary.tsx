import { Component } from 'react';
import type { ReactNode } from 'react';
import GlowButton from '../GlowButton/GlowButton';
import './ErrorBoundary.css';


interface ErrorBoundaryProps {
    children: ReactNode;
}

interface ErrorBoundaryState {
    hasFailed: boolean;
}

function handleReload() {
    window.location.reload();
}

// The one class component in the app: React offers no hook that catches a render error. It sits above the router and
// the auth provider, so its fallback uses neither. React itself logs the caught error.
class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
    state: ErrorBoundaryState = { hasFailed: false };

    static getDerivedStateFromError(): ErrorBoundaryState {
        return { hasFailed: true };
    }

    render() {
        if (!this.state.hasFailed) {
            return this.props.children;
        }

        return (
            <main className="error-boundary" role="alert">
                <h1 className="error-boundary-title">SIGNAL LOST</h1>
                <p className="error-boundary-text">Something broke while drawing this page.</p>
                <GlowButton label="RELOAD" onClick={handleReload} />
            </main>
        );
    }
}

export default ErrorBoundary;

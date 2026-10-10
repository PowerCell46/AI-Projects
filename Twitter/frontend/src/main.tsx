import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import ErrorBoundary from './components/shared/ErrorBoundary/ErrorBoundary';
import { AuthProvider } from './contexts/AuthContext';
import './index.css';


const rootElement = document.getElementById('root');

if (!rootElement) {
    throw new Error('Root element #root is missing from index.html.');
}

createRoot(rootElement)
    .render(
        <StrictMode>
            <ErrorBoundary>
                <AuthProvider>
                    <BrowserRouter>
                        <App />
                    </BrowserRouter>
                </AuthProvider>
            </ErrorBoundary>
        </StrictMode>,
    );

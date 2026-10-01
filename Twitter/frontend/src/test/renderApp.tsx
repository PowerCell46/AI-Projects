import { act, render } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import App from '../App';
import { AuthProvider } from '../contexts/AuthContext';


// Resolves the session lookup inside act, so the guards have decided by the time this returns.
export async function renderApp(path: string, locationState: object | null = null) {
    window.history.replaceState({ usr: locationState, key: 'test', idx: 0 }, '', path);

    await act(async () => {
        render(
            <AuthProvider>
                <BrowserRouter>
                    <App />
                </BrowserRouter>
            </AuthProvider>,
        );
    });
}

import { Route, Routes } from 'react-router-dom';
import GuestRoute from './components/shared/GuestRoute/GuestRoute';
import ProtectedRoute from './components/shared/ProtectedRoute/ProtectedRoute';
import SessionRedirect from './components/shared/SessionRedirect/SessionRedirect';
import ConfirmPage from './pages/ConfirmPage/ConfirmPage';
import FeedPage from './pages/FeedPage/FeedPage';
import LoginPage from './pages/LoginPage/LoginPage';
import RegisterPage from './pages/RegisterPage/RegisterPage';
import ResendPage from './pages/ResendPage/ResendPage';
import { ROUTES } from './routes';


function App() {
    return (
        <Routes>
            <Route element={<GuestRoute />}>
                <Route path={ROUTES.login} element={<LoginPage />} />
                <Route path={ROUTES.register} element={<RegisterPage />} />
            </Route>
            <Route path={ROUTES.confirm} element={<ConfirmPage />} />
            <Route path={ROUTES.resend} element={<ResendPage />} />
            <Route element={<ProtectedRoute />}>
                <Route path={ROUTES.feed} element={<FeedPage />} />
            </Route>
            <Route path="*" element={<SessionRedirect />} />
        </Routes>
    );
}

export default App;

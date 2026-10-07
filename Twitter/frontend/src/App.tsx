import { Route, Routes } from 'react-router-dom';
import GuestRoute from './components/shared/GuestRoute/GuestRoute';
import ProtectedRoute from './components/shared/ProtectedRoute/ProtectedRoute';
import SessionRedirect from './components/shared/SessionRedirect/SessionRedirect';
import Shell from './components/shared/Shell/Shell';
import TabPanels from './components/shared/TabPanels/TabPanels';
import ConfirmPage from './pages/ConfirmPage/ConfirmPage';
import LikedPage from './pages/LikedPage/LikedPage';
import LoginPage from './pages/LoginPage/LoginPage';
import RegisterPage from './pages/RegisterPage/RegisterPage';
import ResendPage from './pages/ResendPage/ResendPage';
import SavedPage from './pages/SavedPage/SavedPage';
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
                <Route element={<Shell />}>
                    <Route element={<TabPanels />}>
                        <Route path={ROUTES.feed} />
                        <Route path={ROUTES.people} />
                        <Route path={ROUTES.tweet} />
                    </Route>
                    <Route path={ROUTES.saved} element={<SavedPage />} />
                    <Route path={ROUTES.liked} element={<LikedPage />} />
                </Route>
            </Route>
            <Route path="*" element={<SessionRedirect />} />
        </Routes>
    );
}

export default App;

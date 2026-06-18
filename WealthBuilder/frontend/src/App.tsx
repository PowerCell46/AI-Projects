import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext/AuthProvider';
import { ThemeProvider } from './context/ThemeContext/ThemeProvider';
import { AuthScreen } from './components/AuthScreen/AuthScreen';
import { HomePage } from './components/HomePage/HomePage';
import { ProtectedRoute } from './components/ProtectedRoute/ProtectedRoute';
import { PublicOnlyRoute } from './components/PublicOnlyRoute/PublicOnlyRoute';
import { APP_ROUTES } from './constants/routes';


export const App = () => {
    return (
        <ThemeProvider>
            <AuthProvider>
                <BrowserRouter>
                    <Routes>
                        <Route
                            path={APP_ROUTES.LOGIN}
                            element={(
                                <PublicOnlyRoute>
                                    <AuthScreen mode="login" />
                                </PublicOnlyRoute>
                            )}
                        />

                        <Route
                            path={APP_ROUTES.REGISTER}
                            element={(
                                <PublicOnlyRoute>
                                    <AuthScreen mode="register" />
                                </PublicOnlyRoute>
                            )}
                        />

                        <Route
                            path={APP_ROUTES.HOME}
                            element={(
                                <ProtectedRoute>
                                    <HomePage />
                                </ProtectedRoute>
                            )}
                        />

                        <Route path="*" element={<Navigate to={APP_ROUTES.HOME} replace />} />
                    </Routes>
                </BrowserRouter>
            </AuthProvider>
        </ThemeProvider>
    );
};

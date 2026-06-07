import { BrowserRouter, Navigate, Route, Routes, useSearchParams } from 'react-router-dom'
import { AppLayout } from './components/AppLayout/AppLayout'
import { useCurrentUser } from './hooks/useCurrentUser'
import { ArtistsPage } from './pages/ArtistsPage/ArtistsPage'
import { HistoryPage } from './pages/HistoryPage/HistoryPage'
import { InsightsPage } from './pages/InsightsPage/InsightsPage'
import { LikedPage } from './pages/LikedPage/LikedPage'
import { ProfilePage } from './pages/ProfilePage/ProfilePage'
import { OverviewPage } from './pages/OverviewPage/OverviewPage'
import { SignInPage } from './pages/SignInPage/SignInPage'


const LoginErrorPage = () => {
  const [searchParams] = useSearchParams()
  const reason = searchParams.get('reason')

  const message =
    reason === 'login_failed'
      ? 'Signing in with Spotify did not work. Please try again.'
      : 'Something went wrong while signing in. Please try again.'

  return <SignInPage errorMessage={message} />
}

export const App = () => {
  const { user, loading, refresh } = useCurrentUser()

  if (loading) {
    return null
  }

  const authenticated = user?.authenticated === true

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login-error" element={<LoginErrorPage />} />
        {authenticated ? (
          <Route element={<AppLayout />}>
            <Route path="/" element={<Navigate to="/overview" replace />} />
            <Route path="/overview" element={<OverviewPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/artists" element={<ArtistsPage />} />
            <Route path="/liked-songs" element={<LikedPage />} />
            <Route path="/liked" element={<Navigate to="/liked-songs" replace />} />
            <Route path="/insights" element={<InsightsPage />} />
            <Route path="/profile" element={<ProfilePage onLoggedOut={refresh} />} />
            <Route path="*" element={<Navigate to="/overview" replace />} />
          </Route>
        ) : (
          <Route path="*" element={<SignInPage />} />
        )}
      </Routes>
    </BrowserRouter>
  )
}

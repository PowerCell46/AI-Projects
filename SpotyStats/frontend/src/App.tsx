import { BrowserRouter, Navigate, Route, Routes, useSearchParams } from 'react-router-dom'
import { AppLayout } from './components/AppLayout/AppLayout'
import { useCurrentUser } from './hooks/useCurrentUser'
import { PlaceholderPage } from './pages/PlaceholderPage/PlaceholderPage'
import { ProfilePage } from './pages/ProfilePage/ProfilePage'
import { SignInPage } from './pages/SignInPage/SignInPage'
import { TodayPage } from './pages/TodayPage/TodayPage'


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
            <Route path="/" element={<Navigate to="/today" replace />} />
            <Route path="/today" element={<TodayPage />} />
            <Route
              path="/history"
              element={<PlaceholderPage eyebrow="Listening diary" title="History" />}
            />
            <Route
              path="/artists"
              element={<PlaceholderPage eyebrow="Listening diary" title="Artists" />}
            />
            <Route
              path="/liked"
              element={<PlaceholderPage eyebrow="Library" title="Liked" />}
            />
            <Route
              path="/insights"
              element={<PlaceholderPage eyebrow="Listening diary" title="Insights" />}
            />
            <Route path="/profile" element={<ProfilePage onLoggedOut={refresh} />} />
            <Route path="*" element={<Navigate to="/today" replace />} />
          </Route>
        ) : (
          <Route path="*" element={<SignInPage />} />
        )}
      </Routes>
    </BrowserRouter>
  )
}

import { useEffect, useState } from 'react'
import { fetchCurrentUser } from '../services/authService'
import type { CurrentUser } from '../services/authService'


interface CurrentUserState {
  user: CurrentUser | null
  loading: boolean
  refresh: () => void
}

/**
 * Auth probe for app startup: calls GET /api/me once (public, never 401s)
 * to decide between the sign-in screen and the logged-in shell.
 */
export const useCurrentUser = (): CurrentUserState => {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [probeCount, setProbeCount] = useState(0)

  useEffect(() => {
    let cancelled = false

    setLoading(true)

    fetchCurrentUser()
      .then((currentUser) => {
        if (!cancelled) {
          setUser(currentUser)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setUser({ authenticated: false, spotifyUserId: null, displayName: null })
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [probeCount])

  const refresh = (): void => setProbeCount((count) => count + 1)

  return { user, loading, refresh }
}

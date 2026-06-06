import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button/Button'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import { Skeleton } from '../../components/Skeleton/Skeleton'
import { ApiRequestError } from '../../services/api'
import { logout } from '../../services/authService'
import { fetchProfile } from '../../services/profileService'
import type { Profile } from '../../services/profileService'
import styles from './ProfilePage.module.css'


interface ProfilePageProps {
  onLoggedOut: () => void
}

export const ProfilePage = ({ onLoggedOut }: ProfilePageProps) => {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false

    fetchProfile()
      .then((loadedProfile) => {
        if (!cancelled) {
          setProfile(loadedProfile)
        }
      })
      .catch((cause: unknown) => {
        if (cancelled) {
          return
        }

        if (cause instanceof ApiRequestError && cause.status === 401) {
          setError('Your Spotify session has expired. Please sign in again.')
        } else {
          setError('Could not load your profile. Spotify may be unavailable — try again.')
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  const handleLogout = async (): Promise<void> => {
    try {
      await logout()
    } finally {
      onLoggedOut()
      navigate('/')
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Account"
        title="Profile"
        actions={
          <Button variant="secondary" onClick={() => void handleLogout()}>
            Log out
          </Button>
        }
      />
      <Panel>
        {error !== null && <p className={styles.error}>{error}</p>}
        {error === null && profile === null && (
          <div className={styles.card}>
            <Skeleton width="84px" height="84px" radius="50%" />
            <div className={styles.details}>
              <Skeleton width="180px" height="20px" />
              <Skeleton width="240px" height="14px" />
            </div>
          </div>
        )}
        {profile !== null && (
          <div className={styles.card}>
            {profile.imageUrl !== null ? (
              <img className={styles.avatar} src={profile.imageUrl} alt="" />
            ) : (
              <div className={styles.avatarFallback}>
                {(profile.displayName ?? profile.spotifyUserId).charAt(0).toUpperCase()}
              </div>
            )}
            <div className={styles.details}>
              <div className={styles.name}>
                {profile.displayName ?? profile.spotifyUserId}
              </div>
              {profile.email !== null && (
                <div className={styles.email}>{profile.email}</div>
              )}
              <div className={styles.userId}>Spotify ID · {profile.spotifyUserId}</div>
            </div>
          </div>
        )}
      </Panel>
    </>
  )
}

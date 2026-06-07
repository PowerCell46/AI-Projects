import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button/Button'
import { PageHeader } from '../../components/PageHeader/PageHeader'
import { Panel } from '../../components/Panel/Panel'
import { Skeleton } from '../../components/Skeleton/Skeleton'
import { StatCard, StatCardSkeleton } from '../../components/StatCard/StatCard'
import { ApiRequestError } from '../../services/api'
import { logout } from '../../services/authService'
import { fetchProfile } from '../../services/profileService'
import type { ListeningTotals, Profile } from '../../services/profileService'
import { formatListeningTime, formatShortDate } from '../../utils/format'
import styles from './ProfilePage.module.css'


interface ProfilePageProps {
  onLoggedOut: () => void
}

const SPOTIFY_APPS_URL = 'https://www.spotify.com/account/apps/'

const DAY_MS = 24 * 60 * 60 * 1000

/** Calendar days from the first tracked play until now, at least 1. */
const daysTracked = (trackingSince: string): number =>
  Math.max(1, Math.floor((Date.now() - Date.parse(trackingSince)) / DAY_MS) + 1)

interface HabitEntry {
  label: string
  value: string
}

/** Lifetime averages — numbers no other view derives from the totals. */
const buildHabits = (totals: ListeningTotals, trackingSince: string): HabitEntry[] => {
  const days = daysTracked(trackingSince)

  return [
    { label: 'Days tracked', value: String(days) },
    {
      label: 'Listening per day',
      value: formatListeningTime(totals.totalListeningTimeMs / days),
    },
    {
      label: 'Tracks per day',
      value: String(Math.round(totals.totalPlays / days)),
    },
    {
      label: 'Plays per unique track',
      value: totals.uniqueTracks > 0 ? (totals.totalPlays / totals.uniqueTracks).toFixed(1) : '—',
    },
  ]
}

const LogoutIcon = () => (
  <svg
    width="14"
    height="14"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
  >
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <polyline points="16 17 21 12 16 7" />
    <line x1="21" y1="12" x2="9" y2="12" />
  </svg>
)

const capitalize = (value: string): string =>
  value.charAt(0).toUpperCase() + value.slice(1)

const buildBadges = (profile: Profile): string[] => {
  const badges: string[] = []

  if (profile.product !== null) {
    badges.push(`Spotify ${capitalize(profile.product)}`)
  }
  if (profile.country !== null) {
    badges.push(profile.country)
  }
  if (profile.followers !== null) {
    badges.push(`${profile.followers} ${profile.followers === 1 ? 'follower' : 'followers'}`)
  }

  return badges
}

export const ProfilePage = ({ onLoggedOut }: ProfilePageProps) => {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)
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
    setLoggingOut(true)

    try {
      await logout()
    } finally {
      onLoggedOut()
      navigate('/')
    }
  }

  const totals = profile?.totals

  return (
    <>
      <PageHeader
        eyebrow="Account"
        title="Profile"
        actions={
          <Button
            variant="danger"
            onClick={() => void handleLogout()}
            disabled={loggingOut}
          >
            <LogoutIcon />
            {loggingOut ? 'Logging out…' : 'Log out'}
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
              <Skeleton width="200px" height="12px" />
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
              <div className={styles.badges}>
                {buildBadges(profile).map((badge) => (
                  <span key={badge} className={styles.badge}>
                    {badge}
                  </span>
                ))}
              </div>
            </div>
          </div>
        )}
      </Panel>
      {error === null && (
        <div className={styles.statsRow}>
          {totals === undefined ? (
            [0, 1, 2, 3].map((index) => <StatCardSkeleton key={index} />)
          ) : (
            <>
              <StatCard
                label="Tracks played"
                value={String(totals.totalPlays)}
                sublabel={
                  totals.trackingSince !== null
                    ? `tracked since ${formatShortDate(totals.trackingSince)}`
                    : 'nothing tracked yet'
                }
              />
              <StatCard
                label="Listening time"
                value={formatListeningTime(totals.totalListeningTimeMs)}
                sublabel="recorded by Spotystats"
              />
              <StatCard
                label="Unique artists"
                value={String(totals.uniqueArtists)}
                sublabel={`across ${totals.uniqueTracks} unique tracks`}
              />
              <StatCard
                label="Liked songs"
                value={totals.likedTotal !== null ? String(totals.likedTotal) : '—'}
                sublabel="in your Spotify library"
              />
            </>
          )}
        </div>
      )}
      {error === null && (
        <div className={styles.bottomGrid}>
          <Panel>
            <h2 className={styles.panelTitle}>Listening habits</h2>
            <p className={styles.panelSubtitle}>Lifetime averages across everything tracked</p>
            {totals === undefined && <Skeleton height="180px" radius="14px" />}
            {totals !== undefined && totals.trackingSince === null && (
              <p className={styles.emptyNote}>No plays recorded yet.</p>
            )}
            {totals !== undefined && totals.trackingSince !== null && (
              <ul className={styles.habits}>
                {buildHabits(totals, totals.trackingSince).map((habit) => (
                  <li key={habit.label} className={styles.habitRow}>
                    <span className={styles.habitLabel}>{habit.label}</span>
                    <span className={styles.habitValue}>{habit.value}</span>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
          <Panel>
            <h2 className={styles.panelTitle}>Your data</h2>
            <p className={styles.panelSubtitle}>How Spotystats keeps your diary</p>
            <div className={styles.dataText}>
              <p>
                Plays are captured automatically while you use Spotystats. Spotify only
                shares your 50 most recent plays, so regular visits keep the diary complete.
              </p>
              <p>
                Your play history lives in Spotystats&apos;s own database
                {totals?.trackingSince != null
                  ? ` and reaches back to ${formatShortDate(totals.trackingSince)}`
                  : ''}
                . Liked songs and followed artists stay in your Spotify library — Spotystats
                reads and updates them live.
              </p>
            </div>
            <a
              className={styles.manageLink}
              href={SPOTIFY_APPS_URL}
              target="_blank"
              rel="noreferrer"
            >
              Manage access on Spotify
            </a>
          </Panel>
        </div>
      )}
    </>
  )
}

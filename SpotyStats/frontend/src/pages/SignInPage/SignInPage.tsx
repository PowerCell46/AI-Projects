import { LOGIN_URL } from '../../services/authService'
import styles from './SignInPage.module.css'


interface SignInPageProps {
  errorMessage?: string
}

/**
 * Anonymous landing. Login is a full-page navigation (plain <a>), not a
 * fetch — the backend redirects through accounts.spotify.com and back.
 */
export const SignInPage = ({ errorMessage }: SignInPageProps) => (
  <div className={styles.page}>
    <div className={styles.wordmark}>Spotistats</div>
    <div className={styles.hero}>
      <div className={styles.eyebrow}>Listening diary</div>
      <h1 className={styles.title}>Your music, remembered</h1>
      <p className={styles.subtitle}>
        A personal diary of everything you listen to on Spotify — daily history,
        favorite artists, and how your weeks sound.
      </p>
      {errorMessage !== undefined && <p className={styles.error}>{errorMessage}</p>}
      <a className={styles.loginButton} href={LOGIN_URL}>
        Sign in with Spotify
      </a>
    </div>
  </div>
)

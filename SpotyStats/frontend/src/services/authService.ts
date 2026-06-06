import { apiGet, apiPost } from './api'


export interface CurrentUser {
  authenticated: boolean
  spotifyUserId: string | null
  displayName: string | null
}

/** Login must be a full-page navigation — the OAuth dance can't run over fetch. */
export const LOGIN_URL = '/oauth2/authorization/spotify'

export const fetchCurrentUser = (): Promise<CurrentUser> =>
  apiGet<CurrentUser>('/api/me')

export const logout = async (): Promise<void> => {
  await apiPost('/auth/logout')
}

export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

const readCookie = (name: string): string | null =>
  document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.split('=')[1] ?? null

/**
 * Turns a non-OK response into an ApiRequestError. Bare Spring Security
 * 401s may have no body, so JSON parsing must be allowed to fail.
 */
const toRequestError = async (response: Response): Promise<ApiRequestError> => {
  try {
    const body = (await response.json()) as ApiError
    return new ApiRequestError(body.status, body.error, body.message)
  } catch {
    return new ApiRequestError(response.status, 'unknown', response.statusText)
  }
}

export const apiGet = async <T>(url: string): Promise<T> => {
  const response = await fetch(url)

  if (!response.ok) {
    throw await toRequestError(response)
  }

  return (await response.json()) as T
}

/**
 * State-changing requests must echo the XSRF-TOKEN cookie back as a header —
 * that is the backend's whole CSRF check (see integration guide §5).
 */
export const apiPost = async (url: string, body?: unknown): Promise<Response> => {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': readCookie('XSRF-TOKEN') ?? '',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (!response.ok) {
    throw await toRequestError(response)
  }

  return response
}

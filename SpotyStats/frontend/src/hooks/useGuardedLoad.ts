import { useCallback, useRef } from 'react'


export interface GuardedLoad {
  /** Starts the load unless a previous run is still in flight. */
  run: (load: () => Promise<unknown>) => void
  /** Whether a run is currently in flight. */
  isRunning: () => boolean
}

/**
 * Guards an async load against overlapping runs: while one is in flight,
 * further run() calls are ignored. StrictMode mounts effects twice in dev,
 * and two concurrent loads would race each other for the same data.
 */
export const useGuardedLoad = (): GuardedLoad => {
  const inFlightRef = useRef(false)

  const run = useCallback((load: () => Promise<unknown>): void => {
    if (inFlightRef.current) {
      return
    }
    inFlightRef.current = true

    load().finally(() => {
      inFlightRef.current = false
    })
  }, [])

  const isRunning = useCallback(() => inFlightRef.current, [])

  return { run, isRunning }
}

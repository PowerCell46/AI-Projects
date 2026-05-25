import { useCallback, useEffect, useRef } from 'react'
import { prefersReducedMotion } from '../../utils/prefersReducedMotion'

export interface UseCarouselOptions {
  itemCount: number
  intervalMs?: number
  /** When false, auto-advance is disabled and next/prev become no-ops. */
  enabled?: boolean
  /** External pause signal (e.g. open modal). Suspends auto-advance while true. */
  paused?: boolean
}

export interface CarouselApi {
  containerRef: React.RefObject<HTMLDivElement | null>
  trackRef: React.RefObject<HTMLDivElement | null>
  next: () => void
  prev: () => void
}

const TRANSITION = 'transform 700ms cubic-bezier(0.4, 0.0, 0.2, 1)'

export function useCarousel({
  itemCount,
  intervalMs = 5000,
  enabled = true,
  paused = false,
}: UseCarouselOptions): CarouselApi {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const trackRef = useRef<HTMLDivElement | null>(null)
  const indexRef = useRef(0)
  const pausedRef = useRef(false)
  const externalPausedRef = useRef(paused)
  const animatingRef = useRef(false)

  useEffect(() => {
    externalPausedRef.current = paused
  }, [paused])

  const applyTranslate = useCallback((index: number, animated: boolean) => {
    const track = trackRef.current
    if (!track || track.children.length < 2) {
      if (track) {
        track.style.transition = 'none'
        track.style.transform = 'translateX(0)'
      }
      return
    }
    const a = track.children[0] as HTMLElement
    const b = track.children[1] as HTMLElement
    const step = b.offsetLeft - a.offsetLeft
    track.style.transition = animated ? TRANSITION : 'none'
    track.style.transform = `translateX(${-index * step}px)`
  }, [])

  const next = useCallback(() => {
    if (!enabled || animatingRef.current || itemCount === 0) return
    const track = trackRef.current
    if (!track) return
    animatingRef.current = true
    const nextIndex = indexRef.current + 1
    applyTranslate(nextIndex, true)
    indexRef.current = nextIndex

    const onEnd = () => {
      track.removeEventListener('transitionend', onEnd)
      animatingRef.current = false
      if (indexRef.current >= itemCount) {
        indexRef.current = 0
        applyTranslate(0, false)
      }
    }
    track.addEventListener('transitionend', onEnd)
  }, [applyTranslate, itemCount, enabled])

  const prev = useCallback(() => {
    if (!enabled || animatingRef.current || itemCount === 0) return
    const track = trackRef.current
    if (!track) return
    animatingRef.current = true

    if (indexRef.current === 0) {
      indexRef.current = itemCount
      applyTranslate(itemCount, false)
      void track.offsetWidth
    }

    const nextIndex = indexRef.current - 1
    applyTranslate(nextIndex, true)
    indexRef.current = nextIndex

    const onEnd = () => {
      track.removeEventListener('transitionend', onEnd)
      animatingRef.current = false
    }
    track.addEventListener('transitionend', onEnd)
  }, [applyTranslate, itemCount, enabled])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const onEnter = () => {
      pausedRef.current = true
    }
    const onLeave = () => {
      pausedRef.current = false
    }
    container.addEventListener('mouseenter', onEnter)
    container.addEventListener('mouseleave', onLeave)
    return () => {
      container.removeEventListener('mouseenter', onEnter)
      container.removeEventListener('mouseleave', onLeave)
    }
  }, [])

  useEffect(() => {
    if (!enabled) return
    if (prefersReducedMotion()) return
    if (itemCount === 0) return
    const id = window.setInterval(() => {
      if (pausedRef.current || externalPausedRef.current) return
      next()
    }, intervalMs)
    return () => window.clearInterval(id)
  }, [itemCount, intervalMs, next, enabled])

  useEffect(() => {
    const onResize = () => applyTranslate(indexRef.current, false)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [applyTranslate])

  useEffect(() => {
    indexRef.current = 0
    applyTranslate(0, false)
  }, [itemCount, enabled, applyTranslate])

  return { containerRef, trackRef, next, prev }
}

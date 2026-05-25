import {
  Children,
  cloneElement,
  isValidElement,
  useEffect,
  useLayoutEffect,
  useState,
  type ReactNode,
} from 'react'
import { useCarousel } from './Carousel'
import './Carousel.css'

interface CarouselProps {
  children: ReactNode
  intervalMs?: number
  ariaLabel?: string
  renderHeader: (api: { next: () => void; prev: () => void }) => ReactNode
  trackVariant?: string
  /** When false, never loop or auto-advance regardless of content size. */
  enableLoop?: boolean
  /** External pause signal — suspends auto-advance while true. */
  paused?: boolean
}

export default function Carousel({
  children,
  intervalMs = 5000,
  ariaLabel,
  renderHeader,
  trackVariant,
  enableLoop = true,
  paused = false,
}: CarouselProps) {
  const items = Children.toArray(children)
  const [needsLoop, setNeedsLoop] = useState(false)
  const { containerRef, trackRef, next, prev } = useCarousel({
    itemCount: items.length,
    intervalMs,
    enabled: needsLoop,
    paused,
  })

  useLayoutEffect(() => {
    const container = containerRef.current
    const track = trackRef.current
    if (!container || !track) return

    const check = () => {
      if (!enableLoop) {
        setNeedsLoop(false)
        return
      }
      const childCount = track.children.length
      if (childCount < 2 || items.length === 0) {
        setNeedsLoop(false)
        return
      }
      const a = track.children[0] as HTMLElement
      const b = track.children[1] as HTMLElement
      const step = b.offsetLeft - a.offsetLeft
      // step is the per-item advance distance, so the original set occupies roughly
      // step * items.length (a tiny bit less for the trailing missing gap, which is fine).
      const originalWidth = step * items.length
      setNeedsLoop(originalWidth > container.clientWidth + 1)
    }

    check()
    if (typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver(check)
    ro.observe(container)
    return () => ro.disconnect()
  }, [items.length, needsLoop, containerRef, trackRef, enableLoop])

  // When loop state flips, reset the inline track transform.
  useEffect(() => {
    const track = trackRef.current
    if (!track) return
    track.style.transition = 'none'
    track.style.transform = 'translateX(0)'
  }, [needsLoop, trackRef])

  const duplicated = needsLoop
    ? items.map((item, i) => {
        if (isValidElement(item)) {
          return cloneElement(item, { key: `clone-${i}` } as Partial<unknown> as never)
        }
        return item
      })
    : null

  return (
    <section className="carousel" aria-roledescription="carousel" aria-label={ariaLabel}>
      {renderHeader({ next, prev })}
      <div className="carousel-container" ref={containerRef} aria-live="off">
        <div
          className={`carousel-track${trackVariant ? ` ${trackVariant}` : ''}`}
          ref={trackRef}
        >
          {items}
          {duplicated && (
            <div aria-hidden="true" style={{ display: 'contents' }}>
              {duplicated}
            </div>
          )}
        </div>
      </div>
    </section>
  )
}

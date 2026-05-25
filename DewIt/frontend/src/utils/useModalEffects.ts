import { useEffect, useRef, type MutableRefObject } from 'react'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

// Module-level stack tracking every currently-open modal's onClose ref.
// The last element is the topmost (most recently opened) modal.
// Using stable ref objects as stack keys so callbacks survive re-renders.
const modalStack: MutableRefObject<() => void>[] = []

export function useModalEffects(open: boolean, onClose: () => void) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)
  // Keep a stable ref so the keydown handler always calls the latest onClose
  // without needing to be recreated every render.
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  // Tracks whether the current open cycle was closed via Escape (keyboard).
  // Only keyboard-triggered closes restore focus — programmatic .focus() after
  // a mouse-initiated close would spuriously trigger :focus-visible on the
  // previously-focused element (the brown outline on the task card).
  const closedByEscapeRef = useRef(false)

  useEffect(() => {
    if (!open) return

    previousFocusRef.current = document.activeElement as HTMLElement | null
    closedByEscapeRef.current = false

    // Register this modal as the topmost; add body class only on the first open modal.
    modalStack.push(onCloseRef)
    if (modalStack.length === 1) {
      document.body.classList.add('modal-open')
    }

    const dialog = dialogRef.current
    if (dialog) {
      const first = dialog.querySelector<HTMLElement>(FOCUSABLE_SELECTOR)
      first?.focus()
    }

    const onKeyDown = (e: KeyboardEvent) => {
      // Ignore if a different (topmost) modal should handle this event.
      if (modalStack[modalStack.length - 1] !== onCloseRef) return

      if (e.key === 'Escape') {
        e.preventDefault()
        closedByEscapeRef.current = true
        onCloseRef.current()
        return
      }

      if (e.key === 'Tab' && dialog) {
        const focusables = Array.from(
          dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR),
        ).filter((el) => !el.hasAttribute('disabled'))
        if (focusables.length === 0) return
        const first = focusables[0]
        const last = focusables[focusables.length - 1]
        const active = document.activeElement as HTMLElement | null
        if (e.shiftKey && active === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && active === last) {
          e.preventDefault()
          first.focus()
        }
      }
    }

    document.addEventListener('keydown', onKeyDown)

    return () => {
      document.removeEventListener('keydown', onKeyDown)

      // Remove this modal from the stack.
      const idx = modalStack.indexOf(onCloseRef)
      if (idx !== -1) modalStack.splice(idx, 1)
      // Remove body class only once all modals have closed.
      if (modalStack.length === 0) {
        document.body.classList.remove('modal-open')
      }

      // Restore focus only for keyboard-triggered closes (Escape).
      // Skipping it for mouse/backdrop closes prevents :focus-visible from
      // firing on the originating element after a click interaction.
      if (closedByEscapeRef.current) {
        previousFocusRef.current?.focus?.()
      }
    }
  }, [open]) // onClose updates are handled via onCloseRef, not a dep here

  return { dialogRef }
}

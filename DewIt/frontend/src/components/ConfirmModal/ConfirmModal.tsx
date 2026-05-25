import { useCallback, type MouseEvent } from 'react'
import { useModalEffects } from '../../utils/useModalEffects'
import './ConfirmModal.css'

type ConfirmVariant = 'danger' | 'primary'

interface ConfirmModalProps {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: ConfirmVariant
  loading?: boolean
  onConfirm: () => void
  onClose: () => void
}

export default function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Discard',
  variant = 'danger',
  loading = false,
  onConfirm,
  onClose,
}: ConfirmModalProps) {
  const handleClose = useCallback(() => {
    if (!loading) onClose()
  }, [loading, onClose])
  const { dialogRef } = useModalEffects(open, handleClose)

  if (!open) return null

  const handleBackdrop = (e: MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) handleClose()
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={handleBackdrop}>
      <div
        ref={dialogRef}
        className="confirm-modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
        aria-describedby="confirm-modal-message"
      >
        <h2 id="confirm-modal-title" className="confirm-modal-title">{title}</h2>
        <p id="confirm-modal-message" className="confirm-modal-message">{message}</p>
        <footer className="confirm-modal-footer">
          <button
            type="button"
            className="confirm-modal-button confirm-modal-button--cancel"
            onClick={handleClose}
            disabled={loading}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`confirm-modal-button confirm-modal-button--${variant}`}
            onClick={onConfirm}
            disabled={loading}
            autoFocus
          >
            {loading ? 'Working…' : confirmLabel}
          </button>
        </footer>
      </div>
    </div>
  )
}

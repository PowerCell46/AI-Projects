import './NavButtons.css'

interface NavButtonsProps {
  onPrev: () => void
  onNext: () => void
  labelContext?: string
}

export default function NavButtons({ onPrev, onNext, labelContext }: NavButtonsProps) {
  const prevLabel = labelContext ? `Previous ${labelContext}` : 'Previous'
  const nextLabel = labelContext ? `Next ${labelContext}` : 'Next'
  return (
    <div className="nav-buttons">
      <button type="button" className="nav-button" aria-label={prevLabel} onClick={onPrev}>
        <span aria-hidden="true">&larr;</span>
      </button>
      <button type="button" className="nav-button" aria-label={nextLabel} onClick={onNext}>
        <span aria-hidden="true">&rarr;</span>
      </button>
    </div>
  )
}

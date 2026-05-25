import './SkeletonCard.css'

export default function SkeletonCard() {
  return (
    <div className="skeleton-card" aria-hidden="true">
      <div className="skeleton-card-top">
        <div className="skeleton-card-top-left">
          <div className="skeleton-card-checkbox" />
          <div className="skeleton-card-pill" />
        </div>
        <div className="skeleton-card-priority" />
      </div>
      <div className="skeleton-card-title" />
      <div className="skeleton-card-desc skeleton-card-desc--1" />
      <div className="skeleton-card-desc skeleton-card-desc--2" />
      <div className="skeleton-card-spacer" />
      <div className="skeleton-card-footer">
        <div className="skeleton-card-due" />
      </div>
    </div>
  )
}

import { useId, type ReactNode } from 'react'
import './Field.css'

interface FieldProps {
  label: string
  /** Pass the id of a real <input>/<textarea> to link the label via htmlFor. */
  htmlFor?: string
  children: ReactNode
}

export default function Field({ label, htmlFor, children }: FieldProps) {
  const labelId = useId()
  return (
    <div className="field" aria-labelledby={labelId}>
      {htmlFor ? (
        <label id={labelId} className="field-label" htmlFor={htmlFor}>
          {label}
        </label>
      ) : (
        <span id={labelId} className="field-label">
          {label}
        </span>
      )}
      {children}
    </div>
  )
}

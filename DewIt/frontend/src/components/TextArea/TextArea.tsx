import type { TextareaHTMLAttributes } from 'react'
import './TextArea.css'

type TextAreaProps = TextareaHTMLAttributes<HTMLTextAreaElement>

export default function TextArea(props: TextAreaProps) {
  const { className, rows, ...rest } = props
  return (
    <textarea
      rows={rows ?? 3}
      {...rest}
      className={`field-control text-area${className ? ` ${className}` : ''}`}
    />
  )
}

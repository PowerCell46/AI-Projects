import type { InputHTMLAttributes } from 'react'
import './TextInput.css'

type TextInputProps = InputHTMLAttributes<HTMLInputElement>

export default function TextInput(props: TextInputProps) {
  const { className, ...rest } = props
  return (
    <input
      type="text"
      {...rest}
      className={`field-control text-input${className ? ` ${className}` : ''}`}
    />
  )
}

import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './Button.module.css'


type ButtonVariant = 'primary' | 'secondary' | 'danger'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  selected?: boolean
  children: ReactNode
}

const classFor = (variant: ButtonVariant, selected: boolean | undefined): string => {
  if (selected === true) {
    return styles.selected
  }

  return styles[variant]
}

export const Button = ({ variant = 'primary', selected, children, ...rest }: ButtonProps) => (
  <button
    className={classFor(variant, selected)}
    aria-pressed={selected}
    {...rest}
  >
    {children}
  </button>
)

import type { ButtonHTMLAttributes, ReactNode } from 'react'
import styles from './Button.module.css'


interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary'
  selected?: boolean
  children: ReactNode
}

const classFor = (variant: 'primary' | 'secondary', selected: boolean | undefined): string => {
  if (selected === true) {
    return styles.selected
  }

  return variant === 'primary' ? styles.primary : styles.secondary
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
